package de.codinix.videoeditor.gl

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

/**
 * Personenerkennung (ML Kit Selfie Segmentation) auf dem Analyse-Stream der Kamera.
 * Liefert pro Frame eine Maske (Byte 0..255 = Vordergrund-Wahrscheinlichkeit) samt
 * Koordinaten-Abbildung an den Compositor.
 *
 * Koordinaten: Die Maske liegt „aufrecht“ vor (ML Kit dreht das Eingabebild um
 * rotationDegrees). Der Compositor sampelt sie mit Sensor-Koordinaten des Kamerabilds;
 * dafür liefern wir hier den Zuschnitt (cropRect) und die Drehung mit.
 */
class PersonSegmenter(private val compositor: CompositorProcessor) : ImageAnalysis.Analyzer {

    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
    )
    @Volatile private var busy = false
    private var buffer = ByteArray(0)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null || busy) { image.close(); return }
        busy = true
        val rotation = image.imageInfo.rotationDegrees
        val crop = image.cropRect
        val w = image.width
        val h = image.height
        val input = InputImage.fromMediaImage(media, rotation)
        segmenter.process(input)
            .addOnSuccessListener { mask ->
                val mw = mask.width
                val mh = mask.height
                // ML Kit liefert einen ByteBuffer mit Float-Werten (4 Bytes pro Pixel)
                val fb = mask.buffer.also { it.rewind() }.asFloatBuffer()
                val n = mw * mh
                if (buffer.size != n) buffer = ByteArray(n)
                for (i in 0 until n) {
                    val v = fb.get(i)
                    buffer[i] = (v * 255f).toInt().coerceIn(0, 255).toByte()
                }
                compositor.updateMask(
                    buffer, mw, mh, rotation,
                    crop.left.toFloat() / w, crop.top.toFloat() / h,
                    crop.width().toFloat() / w, crop.height().toFloat() / h
                )
            }
            .addOnFailureListener { Log.w(TAG, "Segmentierung fehlgeschlagen", it) }
            .addOnCompleteListener { image.close(); busy = false }
    }

    fun close() { segmenter.close() }

    companion object { private const val TAG = "PersonSegmenter" }
}
