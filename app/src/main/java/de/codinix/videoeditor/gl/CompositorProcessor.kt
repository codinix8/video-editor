package de.codinix.videoeditor.gl

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import de.codinix.videoeditor.overlay.OverlaySnapshot
import de.codinix.videoeditor.overlay.OverlayStore
import java.util.concurrent.Executor

/**
 * Herzstück der Render-Pipeline.
 *
 * CameraX liefert die Kamerabilder in eine SurfaceTexture (Eingang) und stellt pro
 * Ziel (Vorschau, Video-Aufnahme) eine Ausgabe-Surface bereit. Für jeden Kameraframe
 * zeichnen wir die Szene einmal pro Ausgabe: Kamerabild, dann alle Overlays.
 * So sind Vorschau und Aufnahme garantiert identisch.
 *
 * Läuft komplett auf einem eigenen Thread mit eigenem EGL-Kontext.
 */
class CompositorProcessor(private val overlays: OverlayStore) : SurfaceProcessor {

    private val thread = HandlerThread("gl-compositor").apply { start() }
    val handler = Handler(thread.looper)
    val executor = Executor { handler.post(it) }

    private var egl: EglCore? = null
    private var cameraProgram = 0
    private var overlayProgram = 0
    private var cameraTexId = 0
    private var inputTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var inputSize: Size? = null

    private class Output(val surfaceOutput: SurfaceOutput, val surface: Surface, val eglSurface: EGLSurface) {
        val size: Size get() = surfaceOutput.size
    }
    private val outputs = mutableListOf<Output>()

    /** GL-Texturen für Overlay-Bitmaps, per Overlay-ID. */
    private val overlayTextures = HashMap<Long, Int>()

    private val texMatrix = FloatArray(16)
    private val outMatrix = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)

    private val fullQuad = GlUtil.floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texCoords = GlUtil.floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
    /** Für Bitmaps: Y gespiegelt, weil Bitmaps oben-links beginnen, GL unten-links. */
    private val texCoordsFlipped = GlUtil.floatBuffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))

    /** Wird auf dem UI-Thread aufgerufen, wenn sich die Frame-Auflösung (nach Rotation) ändert. */
    var onFrameAspectChanged: ((widthOverHeight: Float) -> Unit)? = null
    private var lastReportedAspect = 0f

    private var released = false

    init {
        handler.post { initGl() }
    }

    private fun initGl() {
        try {
            egl = EglCore()
            cameraProgram = GlUtil.createProgram(VERTEX_CAMERA, FRAGMENT_CAMERA)
            overlayProgram = GlUtil.createProgram(VERTEX_OVERLAY, FRAGMENT_OVERLAY)
            cameraTexId = GlUtil.createExternalTexture()
        } catch (e: Exception) {
            Log.e(TAG, "GL-Initialisierung fehlgeschlagen", e)
        }
    }

    // ------------------------------------------------------------------ CameraX-Callbacks

    override fun onInputSurface(request: SurfaceRequest) {
        handler.post {
            if (released) { request.willNotProvideSurface(); return@post }
            // Alte Eingabe freigeben (z.B. nach Kamera-Wechsel)
            inputTexture?.setOnFrameAvailableListener(null)
            inputSurface?.release()
            inputTexture?.release()

            val st = SurfaceTexture(cameraTexId)
            st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            val surface = Surface(st)
            inputTexture = st
            inputSurface = surface
            inputSize = request.resolution
            st.setOnFrameAvailableListener({ onFrame() }, handler)

            request.provideSurface(surface, executor) {
                // Kamera ist fertig mit dieser Surface
                if (inputSurface === surface) {
                    inputTexture?.setOnFrameAvailableListener(null)
                    inputSurface = null
                    inputTexture = null
                }
                surface.release()
                st.release()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        handler.post {
            val eglCore = egl
            if (released || eglCore == null) { surfaceOutput.close(); return@post }
            val surface = surfaceOutput.getSurface(executor) { event ->
                // CameraX möchte diese Ausgabe schließen
                val idx = outputs.indexOfFirst { it.surfaceOutput === event.surfaceOutput }
                if (idx >= 0) {
                    val out = outputs.removeAt(idx)
                    eglCore.makeNothingCurrent()
                    eglCore.destroySurface(out.eglSurface)
                }
                event.surfaceOutput.close()
            }
            try {
                val eglSurface = eglCore.createWindowSurface(surface)
                outputs.add(Output(surfaceOutput, surface, eglSurface))
                reportAspect(surfaceOutput)
            } catch (e: Exception) {
                Log.e(TAG, "Ausgabe-Surface fehlgeschlagen", e)
                surfaceOutput.close()
            }
        }
    }

    private fun reportAspect(out: SurfaceOutput) {
        // Die Ausgabe-Größe ist bereits „aufrecht“ (CameraX rotiert per Transform-Matrix
        // in den Texturkoordinaten, die Größe der Ziel-Surface ist die sichtbare Größe).
        if (out.targets and androidx.camera.core.CameraEffect.PREVIEW == 0) return
        val a = out.size.width.toFloat() / out.size.height.toFloat()
        if (a != lastReportedAspect) {
            lastReportedAspect = a
            onFrameAspectChanged?.invoke(a)
        }
    }

    // ------------------------------------------------------------------ Rendering

    private fun onFrame() {
        val st = inputTexture ?: return
        val eglCore = egl ?: return
        if (outputs.isEmpty()) {
            // Trotzdem konsumieren, sonst staut sich die Queue
            eglCore.makeNothingCurrent()
            st.updateTexImage()
            return
        }
        eglCore.makeCurrent(outputs.first().eglSurface)
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)
        val timestamp = st.timestamp
        val snapshot = overlays.snapshot
        syncOverlayTextures(snapshot)

        for (out in outputs) {
            try {
                eglCore.makeCurrent(out.eglSurface)
                GLES20.glViewport(0, 0, out.size.width, out.size.height)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                out.surfaceOutput.updateTransformMatrix(outMatrix, texMatrix)
                drawCamera(outMatrix)
                drawOverlays(snapshot, out.size)

                eglCore.setPresentationTime(out.eglSurface, timestamp)
                eglCore.swapBuffers(out.eglSurface)
            } catch (e: Exception) {
                Log.e(TAG, "Render-Fehler", e)
            }
        }
    }

    private fun drawCamera(transform: FloatArray) {
        GLES20.glUseProgram(cameraProgram)
        GLES20.glDisable(GLES20.GL_BLEND)
        val aPos = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        val aTex = GLES20.glGetAttribLocation(cameraProgram, "aTexCoord")
        val uMat = GLES20.glGetUniformLocation(cameraProgram, "uTexMatrix")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glUniformMatrix4fv(uMat, 1, false, transform, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    private fun drawOverlays(snapshot: List<OverlaySnapshot>, size: Size) {
        if (snapshot.isEmpty()) return
        GLES20.glUseProgram(overlayProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Bitmaps sind premultiplied
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val aPos = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        val aTex = GLES20.glGetAttribLocation(overlayProgram, "aTexCoord")
        val uMvp = GLES20.glGetUniformLocation(overlayProgram, "uMvp")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texCoordsFlipped)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        val frameAspect = size.width.toFloat() / size.height.toFloat()
        for (o in snapshot) {
            val tex = overlayTextures[o.id] ?: continue
            buildOverlayMatrix(o, frameAspect, mvp)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /**
     * Baut die Modellmatrix: Einheitsquadrat → Overlay-Rechteck in NDC.
     * Reihenfolge: Skalierung (Breite, Höhe in „Breiten-Einheiten“) → Rotation →
     * Anpassung des Seitenverhältnisses (y in NDC) → Verschiebung zum Mittelpunkt.
     */
    private fun buildOverlayMatrix(o: OverlaySnapshot, frameAspect: Float, out: FloatArray) {
        val ndcX = o.cx * 2f - 1f
        val ndcY = 1f - o.cy * 2f
        Matrix.setIdentityM(out, 0)
        Matrix.translateM(out, 0, ndcX, ndcY, 0f)
        Matrix.scaleM(out, 0, 1f, frameAspect, 1f)          // Breiten-Einheiten → NDC-y
        Matrix.rotateM(out, 0, -o.rotationDeg, 0f, 0f, 1f)  // Uhrzeigersinn im Bild = negativ in GL
        Matrix.scaleM(out, 0, o.widthFrac, o.widthFrac * o.aspect, 1f)
    }

    /** Lädt neue Bitmaps hoch und wirft Texturen entfernter Overlays weg. */
    private fun syncOverlayTextures(snapshot: List<OverlaySnapshot>) {
        val liveIds = snapshot.map { it.id }.toSet()
        val it = overlayTextures.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in liveIds) { GlUtil.deleteTexture(e.value); it.remove() }
        }
        for (o in snapshot) {
            if (o.id !in overlayTextures && !o.bitmap.isRecycled) {
                overlayTextures[o.id] = GlUtil.createTextureFromBitmap(o.bitmap)
            }
        }
    }

    fun release() {
        handler.post {
            released = true
            inputTexture?.setOnFrameAvailableListener(null)
            outputs.forEach { egl?.destroySurface(it.eglSurface); it.surfaceOutput.close() }
            outputs.clear()
            overlayTextures.values.forEach { GlUtil.deleteTexture(it) }
            overlayTextures.clear()
            inputSurface?.release(); inputTexture?.release()
            egl?.release(); egl = null
            thread.quitSafely()
        }
    }

    companion object {
        private const val TAG = "Compositor"

        private const val VERTEX_CAMERA = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """
        private const val FRAGMENT_CAMERA = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
        private const val VERTEX_OVERLAY = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMvp;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * aPosition;
                vTexCoord = aTexCoord;
            }
        """
        private const val FRAGMENT_OVERLAY = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
