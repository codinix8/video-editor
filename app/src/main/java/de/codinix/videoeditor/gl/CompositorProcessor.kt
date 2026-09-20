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

    // ---- Mosaik ----
    @Volatile var mosaic: de.codinix.videoeditor.overlay.MosaicSnapshot? = null
    private var imageTileProgram = 0
    private var lineProgram = 0
    private val mosaicTextures = HashMap<Int, Pair<android.graphics.Bitmap, Int>>()

    // ---- Kachel-Modus ----
    /** ID des Video-Overlays, das als Vollbild-Hintergrund dient (0 = keins). */
    @Volatile var backgroundOverlayId: Long = 0L
    private var tileProgram = 0
    private val startNanos = System.nanoTime()

    /** GL-Texturen für Overlay-Bitmaps, per Overlay-ID. */
    private val overlayTextures = HashMap<Long, Int>()
    /** Welches Bitmap-Objekt liegt in der Textur? Ändert es sich (Text neu gerendert), neu hochladen. */
    private val overlayBitmaps = HashMap<Long, android.graphics.Bitmap>()

    /** Video-Overlays: externe Textur + SurfaceTexture, die der Player befüllt. */
    private inner class VideoLayer(val texId: Int, val surfaceTexture: SurfaceTexture, val surface: Surface) {
        @Volatile var frameAvailable = false
        val matrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        var hasFrame = false
    }
    private val videoLayers = HashMap<Long, VideoLayer>()

    private val texMatrix = FloatArray(16)
    private val outMatrix = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)

    private val fullQuad = GlUtil.floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texCoords = GlUtil.floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
    /** Für Bitmaps: Y gespiegelt, weil Bitmaps oben-links beginnen, GL unten-links. */
    private val texCoordsFlipped = GlUtil.floatBuffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))

    /** Wird aufgerufen, wenn sich das sichtbare Seitenverhältnis (Breite/Höhe) ändert. */
    var onFrameAspectChanged: ((widthOverHeight: Float) -> Unit)? = null
    private var lastReportedAspect = 0f

    /**
     * Drehung, die der Konsument (Display bzw. Encoder) auf einen UNGEDREHTEN Puffer
     * anwendet, damit er aufrecht erscheint – i.d.R. CameraInfo.getSensorRotationDegrees().
     * Ob ein Ausgabepuffer ungedreht ist, erkennen wir daran, dass er quer liegt (Breite > Höhe),
     * denn die App läuft im Hochformat.
     */
    @Volatile
    var sensorRotationDegrees = 90

    /** Frontkamera aktiv? Dann muss das Endbild horizontal gespiegelt sein. */
    @Volatile
    var frontFacing = false

    private var released = false

    init {
        handler.post { initGl() }
    }

    private fun initGl() {
        try {
            egl = EglCore()
            cameraProgram = GlUtil.createProgram(VERTEX_CAMERA, FRAGMENT_CAMERA)
            overlayProgram = GlUtil.createProgram(VERTEX_OVERLAY, FRAGMENT_OVERLAY)
            tileProgram = GlUtil.createProgram(VERTEX_TILE, FRAGMENT_TILE)
            imageTileProgram = GlUtil.createProgram(VERTEX_TILE, FRAGMENT_TILE_IMAGE)
            lineProgram = GlUtil.createProgram(VERTEX_TILE, FRAGMENT_LINE)
            cameraTexId = GlUtil.createExternalTexture()
        } catch (e: Exception) {
            Log.e(TAG, "GL-Initialisierung fehlgeschlagen", e)
        }
    }

    // ------------------------------------------------------------------ Video-Overlays

    /**
     * Legt auf dem GL-Thread eine Textur für ein Video-Overlay an und liefert die Surface,
     * in die der Player rendern soll (Callback auf dem GL-Thread – Aufrufer postet weiter).
     */
    fun createVideoLayer(overlayId: Long, onReady: (Surface) -> Unit) {
        handler.post {
            if (released || egl == null) return@post
            videoLayers[overlayId]?.let { onReady(it.surface); return@post }
            val tex = GlUtil.createExternalTexture()
            val st = SurfaceTexture(tex)
            val surface = Surface(st)
            val layer = VideoLayer(tex, st, surface)
            st.setOnFrameAvailableListener({ layer.frameAvailable = true })
            videoLayers[overlayId] = layer
            onReady(surface)
        }
    }

    fun releaseVideoLayer(overlayId: Long) {
        handler.post { videoLayers.remove(overlayId)?.let { destroyVideoLayer(it) } }
    }

    private fun destroyVideoLayer(l: VideoLayer) {
        l.surfaceTexture.setOnFrameAvailableListener(null)
        l.surface.release()
        l.surfaceTexture.release()
        GlUtil.deleteTexture(l.texId)
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

    /** Drehung, die für diesen Puffer noch vom Konsumenten kommt (0 wenn bereits aufrecht). */
    private fun pendingRotation(size: Size): Int =
        if (size.width > size.height) sensorRotationDegrees else 0

    /** Sichtbares Seitenverhältnis (Breite/Höhe) nach der Drehung durch den Konsumenten. */
    private fun displayAspect(size: Size): Float {
        val rot = pendingRotation(size)
        return if (rot == 90 || rot == 270) size.height.toFloat() / size.width.toFloat()
        else size.width.toFloat() / size.height.toFloat()
    }

    private fun reportAspect(out: SurfaceOutput) {
        if (out.targets and androidx.camera.core.CameraEffect.PREVIEW == 0) return
        val a = displayAspect(out.size)
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
        for (l in videoLayers.values) {
            if (l.frameAvailable) {
                l.frameAvailable = false
                try {
                    l.surfaceTexture.updateTexImage()
                    l.surfaceTexture.getTransformMatrix(l.matrix)
                    l.hasFrame = true
                } catch (e: Exception) { Log.w(TAG, "Video-Textur", e) }
            }
        }

        for (out in outputs) {
            try {
                eglCore.makeCurrent(out.eglSurface)
                GLES20.glViewport(0, 0, out.size.width, out.size.height)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                out.surfaceOutput.updateTransformMatrix(outMatrix, texMatrix)
                // Frontkamera: Der Vorschau-Puffer bleibt ungespiegelt und wird erst vom Display
                // gespiegelt (zusammen mit der Drehung). Der Aufnahme-Puffer muss dagegen schon in
                // GL gespiegelt sein, ein Encoder kann das nicht – dort bleiben Overlays unverändert.
                val isPreview = out.surfaceOutput.targets and androidx.camera.core.CameraEffect.PREVIEW != 0
                val consumerMirrors = frontFacing && isPreview && pendingRotation(out.size) != 0

                val tileMode = backgroundOverlayId != 0L && snapshot.any { it.isCamera }
                val mo = mosaic
                if (mo != null && mo.layout != 0) {
                    drawMosaic(mo, out.size, pendingRotation(out.size), consumerMirrors, outMatrix)
                } else if (tileMode) {
                    drawBackground(snapshot, out.size, consumerMirrors)
                } else {
                    drawCamera(outMatrix)
                }
                drawOverlays(snapshot, out.size, consumerMirrors, if (tileMode) outMatrix else null)

                eglCore.setPresentationTime(out.eglSurface, timestamp)
                eglCore.swapBuffers(out.eglSurface)
            } catch (e: Exception) {
                Log.e(TAG, "Render-Fehler", e)
            }
        }
    }

    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    private fun drawCamera(transform: FloatArray) {
        GLES20.glDisable(GLES20.GL_BLEND)
        drawExternal(cameraTexId, transform, identity)
    }

    /** Hintergrundvideo formatfüllend hinter die freigestellte Person. */
    private fun drawBackground(snapshot: List<OverlaySnapshot>, size: Size, preMirror: Boolean) {
        val bg = snapshot.firstOrNull { it.isVideo && it.id == backgroundOverlayId } ?: return
        val layer = videoLayers[bg.id] ?: return
        if (!layer.hasFrame) return
        val dispAspect = displayAspect(size)
        // Breite so wählen, dass Breite UND Höhe gefüllt sind (Aspect-Fill)
        val w = maxOf(1f, 1f / (bg.aspect * dispAspect))
        val fill = OverlaySnapshot(bg.id, 0.5f, 0.5f, w, 0f, bg.aspect, null, true)
        buildOverlayMatrix(fill, dispAspect, pendingRotation(size), preMirror, mvp)
        GLES20.glDisable(GLES20.GL_BLEND)
        drawExternal(layer.texId, layer.matrix, mvp)
    }

    /**
     * Kamerabild als Kachel: Form (Quadrat/Hochformat/Kreis), Signatur-Rahmen mit wanderndem
     * Regenbogen und weichem Leuchten. Das Kamerabild wird mittig auf die Form zugeschnitten.
     * [preMatrix] = Drehung/Spiegelung Display→Puffer (wie bei den Overlay-Vertices),
     * damit die Abtastung des Kamerabilds zur Anzeige passt.
     */
    // ------------------------------------------------------------------ Mosaik

    private fun drawMosaic(mo: de.codinix.videoeditor.overlay.MosaicSnapshot, size: Size, preRotation: Int, preMirror: Boolean, camTransform: FloatArray) {
        val dispAspect = displayAspect(size)
        val rects = de.codinix.videoeditor.overlay.Mosaic.rects(mo.layout)
        val gapX = 0.006f                       // Anteil der Breite
        val gapY = gapX * dispAspect            // gleicher Pixelabstand in der Höhe
        // Grundfarbe: Zwischenräume/Füllung
        GLES20.glClearColor(if (mo.gapWhite) 1f else 0f, if (mo.gapWhite) 1f else 0f, if (mo.gapWhite) 1f else 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        Matrix.setIdentityM(tmp, 0)
        if (preRotation != 0) Matrix.rotateM(tmp, 0, preRotation.toFloat(), 0f, 0f, 1f)
        if (preMirror) Matrix.scaleM(tmp, 0, -1f, 1f, 1f)

        syncMosaicTextures(mo)
        rects.forEachIndexed { i, r0 ->
            val t = mo.tiles.getOrNull(i) ?: return@forEachIndexed
            // Innenrand für Zwischenraum
            val l = r0.left + if (r0.left > 0f) gapX / 2 else 0f
            val rr = r0.right - if (r0.right < 1f) gapX / 2 else 0f
            val tp = r0.top + if (r0.top > 0f) gapY / 2 else 0f
            val bt = r0.bottom - if (r0.bottom < 1f) gapY / 2 else 0f
            val w = rr - l; val h = bt - tp
            val tileAspect = (h / w) / dispAspect                  // H/B in Breiten-Einheiten
            val quad = OverlaySnapshot(0, (l + rr) / 2f, (tp + bt) / 2f, w, 0f, tileAspect)
            buildOverlayMatrix(quad, dispAspect, preRotation, preMirror, mvp)

            // Zuschnitt: Inhalt formatfüllend × Zoom, verschoben
            val contentAspect = when (t.kind) {
                de.codinix.videoeditor.overlay.Mosaic.KIND_IMAGE -> t.bitmap?.let { it.height.toFloat() / it.width } ?: 1f
                else -> 1f / dispAspect
            }
            var cropW = 1f; var cropH = 1f
            if (tileAspect < contentAspect) cropH = tileAspect / contentAspect else cropW = contentAspect / tileAspect
            val z = t.zoom.coerceIn(0.3f, 4f)
            cropW /= z; cropH /= z
            val cropX = (1f - cropW) / 2f + t.offX.coerceIn(-1f, 1f) * kotlin.math.abs(1f - cropW) / 2f
            val cropY = (1f - cropH) / 2f + t.offY.coerceIn(-1f, 1f) * kotlin.math.abs(1f - cropH) / 2f
            val fill = if (t.fillWhite) floatArrayOf(1f, 1f, 1f, 1f) else floatArrayOf(0f, 0f, 0f, 1f)

            val program = if (t.kind == de.codinix.videoeditor.overlay.Mosaic.KIND_IMAGE) imageTileProgram else tileProgram
            GLES20.glUseProgram(program)
            val aPos = GLES20.glGetAttribLocation(program, "aPosition")
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uHalfQuad"), 1f, tileAspect)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uHalfTile"), 1f, tileAspect)
            GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uCrop"), cropX, cropY, cropW, cropH)
            GLES20.glUniform4fv(GLES20.glGetUniformLocation(program, "uFill"), 1, fill, 0)
            if (t.kind == de.codinix.videoeditor.overlay.Mosaic.KIND_IMAGE) {
                val tex = mosaicTextures[i]?.second ?: return@forEachIndexed
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
            } else {
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uPre"), 1, false, tmp, 0)
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, camTransform, 0)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uShape"), 0)
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBorder"), 0f)
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uGlow"), 0f)
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRadius"), 0f)
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), 0f)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
            }
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
        }

        if (mo.rainbowGaps) drawRainbowGaps(mo.layout, dispAspect, preRotation, preMirror, gapX, gapY)
    }

    /** Trennlinien als wandernder Regenbogen über den Zwischenräumen. */
    private fun drawRainbowGaps(layout: Int, dispAspect: Float, preRotation: Int, preMirror: Boolean, gapX: Float, gapY: Float) {
        val lines = ArrayList<FloatArray>() // cx, cy, w, h (Frame-Anteile)
        when (layout) {
            de.codinix.videoeditor.overlay.Mosaic.LAYOUT_2_ROWS -> lines.add(floatArrayOf(0.5f, 0.5f, 1f, gapY))
            de.codinix.videoeditor.overlay.Mosaic.LAYOUT_2_COLS -> lines.add(floatArrayOf(0.5f, 0.5f, gapX, 1f))
            de.codinix.videoeditor.overlay.Mosaic.LAYOUT_3_ROWS -> { lines.add(floatArrayOf(0.5f, 1f / 3, 1f, gapY)); lines.add(floatArrayOf(0.5f, 2f / 3, 1f, gapY)) }
            de.codinix.videoeditor.overlay.Mosaic.LAYOUT_3_COLS -> { lines.add(floatArrayOf(1f / 3, 0.5f, gapX, 1f)); lines.add(floatArrayOf(2f / 3, 0.5f, gapX, 1f)) }
            de.codinix.videoeditor.overlay.Mosaic.LAYOUT_2X2 -> { lines.add(floatArrayOf(0.5f, 0.5f, 1f, gapY)); lines.add(floatArrayOf(0.5f, 0.5f, gapX, 1f)) }
        }
        val t = ((System.nanoTime() - startNanos) / 1_000_000_000.0).toFloat()
        GLES20.glUseProgram(lineProgram)
        val aPos = GLES20.glGetAttribLocation(lineProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(lineProgram, "uTime"), t)
        lines.forEach { ln ->
            val w = ln[2]; val h = ln[3]
            val quad = OverlaySnapshot(0, ln[0], ln[1], w, 0f, (h / w) / dispAspect)
            buildOverlayMatrix(quad, dispAspect, preRotation, preMirror, mvp)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(lineProgram, "uMvp"), 1, false, mvp, 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(lineProgram, "uHorizontal"), if (w > h) 1 else 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun syncMosaicTextures(mo: de.codinix.videoeditor.overlay.MosaicSnapshot) {
        val it = mosaicTextures.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val tile = mo.tiles.getOrNull(e.key)
            if (tile == null || tile.bitmap !== e.value.first) { GlUtil.deleteTexture(e.value.second); it.remove() }
        }
        mo.tiles.forEachIndexed { i, t ->
            val bmp = t.bitmap ?: return@forEachIndexed
            if (t.kind == de.codinix.videoeditor.overlay.Mosaic.KIND_IMAGE && i !in mosaicTextures && !bmp.isRecycled) {
                mosaicTextures[i] = bmp to GlUtil.createTextureFromBitmap(bmp)
            }
        }
    }

    private fun drawCameraTile(o: OverlaySnapshot, dispAspect: Float, preRotation: Int, preMirror: Boolean,
                               camTransform: FloatArray) {
        val glow = if (o.border) 0.10f else 0f
        val tileAspect = o.aspect                       // H/B der Kachel in Breiten-Einheiten
        val halfW = 1f + glow
        val halfH = tileAspect + glow
        // Quad um den Leuchtsaum vergrößern
        val quad = OverlaySnapshot(o.id, o.cx, o.cy, o.widthFrac * halfW, o.rotationDeg, halfH / halfW)
        buildOverlayMatrix(quad, dispAspect, preRotation, preMirror, mvp)

        // Vor-Transformation Display-NDC → Puffer-NDC (identisch zum Vertex-Pfad)
        Matrix.setIdentityM(tmp, 0)
        if (preRotation != 0) Matrix.rotateM(tmp, 0, preRotation.toFloat(), 0f, 0f, 1f)
        if (preMirror) Matrix.scaleM(tmp, 0, -1f, 1f, 1f)

        // Mittiger Zuschnitt: Kamera hat H/B = camAspect, Kachel tileAspect
        val camAspect = 1f / dispAspect
        var cropW = 1f; var cropH = 1f
        if (tileAspect < camAspect) cropH = tileAspect / camAspect else cropW = camAspect / tileAspect
        val cropX = (1f - cropW) / 2f; val cropY = (1f - cropH) / 2f

        GLES20.glUseProgram(tileProgram)
        val aPos = GLES20.glGetAttribLocation(tileProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(tileProgram, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(tileProgram, "uPre"), 1, false, tmp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(tileProgram, "uTexMatrix"), 1, false, camTransform, 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(tileProgram, "uHalfQuad"), halfW, halfH)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(tileProgram, "uHalfTile"), 1f, tileAspect)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(tileProgram, "uCrop"), cropX, cropY, cropW, cropH)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(tileProgram, "uShape"), o.shape)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(tileProgram, "uBorder"), if (o.border) 0.035f else 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(tileProgram, "uGlow"), glow)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(tileProgram, "uRadius"), 0.14f)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(tileProgram, "uFill"), 0f, 0f, 0f, 1f)
        val t = ((System.nanoTime() - startNanos) / 1_000_000_000.0).toFloat()
        GLES20.glUniform1f(GLES20.glGetUniformLocation(tileProgram, "uTime"), t)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(tileProgram, "sTexture"), 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /** Zeichnet eine externe (OES-)Textur mit Textur-Transform und Modellmatrix. */
    private fun drawExternal(texId: Int, texTransform: FloatArray, modelMatrix: FloatArray) {
        GLES20.glUseProgram(cameraProgram)
        val aPos = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        val aTex = GLES20.glGetAttribLocation(cameraProgram, "aTexCoord")
        val uMat = GLES20.glGetUniformLocation(cameraProgram, "uTexMatrix")
        val uMvp = GLES20.glGetUniformLocation(cameraProgram, "uMvp")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glUniformMatrix4fv(uMat, 1, false, texTransform, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, modelMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    private fun drawOverlays(snapshot: List<OverlaySnapshot>, size: Size, preMirror: Boolean, camTransform: FloatArray? = null) {
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

        val dispAspect = displayAspect(size)
        val preRotation = pendingRotation(size)
        for (o in snapshot) {
            if (o.isVideo && o.id == backgroundOverlayId && camTransform != null) continue
            if (o.isCamera) {
                if (camTransform == null) continue
                drawCameraTile(o, dispAspect, preRotation, preMirror, camTransform)
                // Zurück zum 2D-Programm
                GLES20.glUseProgram(overlayProgram)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GLES20.glEnableVertexAttribArray(aPos)
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
                GLES20.glEnableVertexAttribArray(aTex)
                GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texCoordsFlipped)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                continue
            }
            if (o.isVideo) {
                val layer = videoLayers[o.id] ?: continue
                if (!layer.hasFrame) continue
                buildOverlayMatrix(o, dispAspect, preRotation, preMirror, mvp)
                GLES20.glDisable(GLES20.GL_BLEND)
                drawExternal(layer.texId, layer.matrix, mvp)
                // Zurück zum 2D-Programm für nachfolgende Bilder
                GLES20.glUseProgram(overlayProgram)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GLES20.glEnableVertexAttribArray(aPos)
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, fullQuad)
                GLES20.glEnableVertexAttribArray(aTex)
                GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texCoordsFlipped)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                continue
            }
            val tex = overlayTextures[o.id] ?: continue
            buildOverlayMatrix(o, dispAspect, preRotation, preMirror, mvp)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /**
     * Baut die Modellmatrix: Einheitsquadrat → Overlay-Rechteck im SICHTBAREN Bild (NDC),
     * anschließend zurück in die Puffer-Orientierung.
     * Reihenfolge (von innen nach außen): Skalierung (Breite, Höhe in „Breiten-Einheiten“) →
     * eigene Drehung → Seitenverhältnis (y in NDC) → Verschiebung zum Mittelpunkt →
     * Vor-Drehung, die die spätere Drehung durch Display/Encoder wieder aufhebt.
     */
    private fun buildOverlayMatrix(
        o: OverlaySnapshot, dispAspect: Float, preRotation: Int, preMirror: Boolean, out: FloatArray
    ) {
        val ndcX = o.cx * 2f - 1f
        val ndcY = 1f - o.cy * 2f
        Matrix.setIdentityM(out, 0)
        // Konsument dreht den Puffer um preRotation im Uhrzeigersinn → wir drehen vorab gegen
        if (preRotation != 0) Matrix.rotateM(out, 0, preRotation.toFloat(), 0f, 0f, 1f)
        // Konsument spiegelt das (aufrechte) Bild horizontal → wir spiegeln vorab
        if (preMirror) Matrix.scaleM(out, 0, -1f, 1f, 1f)
        Matrix.translateM(out, 0, ndcX, ndcY, 0f)
        Matrix.scaleM(out, 0, 1f, dispAspect, 1f)           // Breiten-Einheiten → NDC-y
        Matrix.rotateM(out, 0, -o.rotationDeg, 0f, 0f, 1f)  // Uhrzeigersinn im Bild = negativ in GL
        Matrix.scaleM(out, 0, o.widthFrac, o.widthFrac * o.aspect, 1f)
    }

    /** Lädt neue Bitmaps hoch und wirft Texturen entfernter Overlays weg. */
    private fun syncOverlayTextures(snapshot: List<OverlaySnapshot>) {
        val liveIds = snapshot.map { it.id }.toSet()
        val it = overlayTextures.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in liveIds) { GlUtil.deleteTexture(e.value); it.remove(); overlayBitmaps.remove(e.key) }
        }
        for (o in snapshot) {
            val bmp = o.bitmap ?: continue
            if (bmp.isRecycled) continue
            val stale = overlayBitmaps[o.id] !== bmp
            if (o.id in overlayTextures && stale) {
                GlUtil.deleteTexture(overlayTextures.remove(o.id)!!)
            }
            if (o.id !in overlayTextures) {
                overlayTextures[o.id] = GlUtil.createTextureFromBitmap(bmp)
                overlayBitmaps[o.id] = bmp
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
            overlayBitmaps.clear()
            mosaicTextures.values.forEach { GlUtil.deleteTexture(it.second) }
            mosaicTextures.clear()
            videoLayers.values.forEach { destroyVideoLayer(it) }
            videoLayers.clear()
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
            uniform mat4 uMvp;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * aPosition;
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
        private const val VERTEX_TILE = """
            attribute vec4 aPosition;
            uniform mat4 uMvp;
            varying vec2 vLocal;
            void main() {
                gl_Position = uMvp * aPosition;
                vLocal = aPosition.xy;
            }
        """
        /**
         * Kachel: Form per Abstandsfunktion, Kamerabild mittig zugeschnitten, Rahmen als
         * wandernder Regenbogen (Farbton = Winkel + Zeit), außen weiches Leuchten.
         */
        private const val FRAGMENT_TILE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vLocal;
            uniform samplerExternalOES sTexture;
            uniform mat4 uPre;
            uniform mat4 uTexMatrix;
            uniform vec2 uHalfQuad;
            uniform vec2 uHalfTile;
            uniform vec4 uCrop;
            uniform int uShape;
            uniform float uBorder;
            uniform float uGlow;
            uniform float uRadius;
            uniform float uTime;
            uniform vec4 uFill;

            vec3 hsv(float h) {
                vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0);
                return clamp(p - 1.0, 0.0, 1.0);
            }
            float sdf(vec2 p) {
                if (uShape == 2) return length(p) - uHalfTile.x;
                vec2 q = abs(p) - uHalfTile + vec2(uRadius);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRadius;
            }
            void main() {
                vec2 p = vLocal * uHalfQuad;              // Breiten-Einheiten, Ursprung Kachelmitte
                float d = sdf(p);
                float aa = 0.006;
                // Kamerabild
                vec2 local01 = (p / uHalfTile + 1.0) * 0.5;            // x rechts, y oben
                vec2 disp = uCrop.xy + vec2(local01.x, 1.0 - local01.y) * uCrop.zw;
                vec2 dispNdc = vec2(disp.x * 2.0 - 1.0, 1.0 - disp.y * 2.0);
                vec2 bufNdc = (uPre * vec4(dispNdc, 0.0, 1.0)).xy;
                vec2 tb = (bufNdc + 1.0) * 0.5;
                vec2 cam = (uTexMatrix * vec4(tb, 0.0, 1.0)).xy;
                vec4 c = texture2D(sTexture, cam);
                if (disp.x < 0.0 || disp.x > 1.0 || disp.y < 0.0 || disp.y > 1.0) c = uFill;   // Inhalt kleiner als Kachel

                float angle = atan(p.y, p.x) / 6.2831853;
                vec3 rainbow = hsv(fract(angle - uTime * 0.12));
                rainbow = mix(rainbow, vec3(1.0), 0.15);  // leicht aufgehellt, weniger grell

                float inner = -uBorder;
                if (d < inner) {
                    gl_FragColor = vec4(c.rgb, 1.0);
                } else if (d < 0.0) {
                    float t = smoothstep(inner - aa, inner + aa, d);   // Übergang Bild→Rahmen
                    gl_FragColor = vec4(mix(c.rgb, rainbow, t), 1.0);
                } else if (uGlow > 0.0 && d < uGlow) {
                    float g = 1.0 - d / uGlow;
                    g = g * g * 0.75;
                    gl_FragColor = vec4(rainbow, g);
                } else {
                    // Außen: sanfter Rand ohne Rahmen
                    float a = uBorder > 0.0 ? 0.0 : 1.0 - smoothstep(0.0, aa, d);
                    gl_FragColor = vec4(c.rgb, a);
                }
            }
        """
        /** Bild in einer Mosaik-Kachel: Zuschnitt/Zoom/Verschiebung, außen Füllfarbe. */
        private const val FRAGMENT_TILE_IMAGE = """
            precision mediump float;
            varying vec2 vLocal;
            uniform sampler2D sTexture;
            uniform vec2 uHalfQuad;
            uniform vec2 uHalfTile;
            uniform vec4 uCrop;
            uniform vec4 uFill;
            void main() {
                vec2 p = vLocal * uHalfQuad;
                vec2 local01 = (p / uHalfTile + 1.0) * 0.5;
                vec2 disp = uCrop.xy + vec2(local01.x, 1.0 - local01.y) * uCrop.zw;
                if (disp.x < 0.0 || disp.x > 1.0 || disp.y < 0.0 || disp.y > 1.0) { gl_FragColor = uFill; return; }
                vec4 c = texture2D(sTexture, disp);
                gl_FragColor = vec4(c.rgb, 1.0);
            }
        """
        /** Trennlinie als Regenbogen entlang der Linie. */
        private const val FRAGMENT_LINE = """
            precision mediump float;
            varying vec2 vLocal;
            uniform float uTime;
            uniform int uHorizontal;
            vec3 hsv(float h) {
                vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0);
                return clamp(p - 1.0, 0.0, 1.0);
            }
            void main() {
                float u = uHorizontal == 1 ? vLocal.x : vLocal.y;
                vec3 c = mix(hsv(fract(u * 0.5 + 0.5 - uTime * 0.12)), vec3(1.0), 0.15);
                gl_FragColor = vec4(c, 1.0);
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
