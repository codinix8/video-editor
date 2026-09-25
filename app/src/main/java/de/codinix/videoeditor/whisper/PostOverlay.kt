package de.codinix.videoeditor.whisper

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings

/**
 * Nachträglich aufgelegtes Bild-/Text-Overlay für den Zeitraum VOR seinem Einfügen
 * (danach ist es bereits in die Segmente gebrannt). Position/Größe/Drehung in Frame-Anteilen.
 */
data class PostOverlaySpec(
    val bitmap: Bitmap, val cx: Float, val cy: Float, val widthFrac: Float, val rotationDeg: Float, val untilMs: Long
)

@OptIn(UnstableApi::class)
class PostOverlay(private val spec: PostOverlaySpec, private val frameW: Int, private val offsetUs: Long) : BitmapOverlay() {
    private val empty = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val scaled: Bitmap = run {
        val targetW = (spec.widthFrac * frameW).toInt().coerceAtLeast(2)
        val targetH = (targetW * spec.bitmap.height / spec.bitmap.width.toFloat()).toInt().coerceAtLeast(2)
        Bitmap.createScaledBitmap(spec.bitmap, targetW, targetH, true)
    }
    private var active = false

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = (presentationTimeUs + offsetUs) / 1000
        active = ms < spec.untilMs
        return if (active) scaled else empty
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        OverlaySettings.Builder()
            .setBackgroundFrameAnchor(2f * spec.cx - 1f, 1f - 2f * spec.cy)
            .setOverlayFrameAnchor(0f, 0f)
            .setRotationDegrees(-spec.rotationDeg)
            .build()
}
