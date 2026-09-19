package de.codinix.videoeditor.whisper

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings

/**
 * Brennt die Untertitel beim Export ein: liefert pro Zeitpunkt ein Vollbild-Bitmap mit dem
 * passenden Block (transparent, wenn keiner aktiv ist). Bitmaps werden pro Block gecacht.
 * [offsetUs] verschiebt die Zeitachse für das jeweilige Segment (Sequenz-Items zählen bei 0).
 */
@OptIn(UnstableApi::class)
class CaptionOverlay(
    private val captions: List<Caption>,
    private val frameW: Int,
    private val frameH: Int,
    private val offsetUs: Long
) : BitmapOverlay() {

    private val empty: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val cache = HashMap<Int, Bitmap>()
    private var lastIndex = -2
    private var last: Bitmap = empty
    private val settings = OverlaySettings.Builder().build()
    private val emptySettings = OverlaySettings.Builder().setScale(0.001f, 0.001f).build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = (presentationTimeUs + offsetUs) / 1000
        val idx = captions.indexOfFirst { ms >= it.startMs && ms < it.endMs }
        if (idx == lastIndex) return last
        lastIndex = idx
        last = if (idx < 0) empty else cache.getOrPut(idx) { CaptionStyle.renderFrame(captions[idx].text, frameW, frameH) }
        return last
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        if (lastIndex < 0) emptySettings else settings
}
