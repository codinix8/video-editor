package de.codinix.videoeditor.whisper

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings

/**
 * Brennt die Untertitel beim Export ein: eng zugeschnittener Block als Bitmap, positioniert
 * über OverlaySettings (Anker in NDC), Skalierung für Pop-Animationen. Bitmaps werden je
 * Zustand gecacht. [offsetUs] verschiebt die Zeitachse für das jeweilige Segment.
 */
@OptIn(UnstableApi::class)
class CaptionOverlay(
    private val captions: List<Caption>,
    private val settings: CaptionSettings,
    private val frameW: Int,
    private val frameH: Int,
    private val offsetUs: Long
) : BitmapOverlay() {

    private val empty: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val cache = HashMap<Long, Bitmap>()
    private var lastKey = Long.MIN_VALUE
    private var last: Bitmap = empty
    private var lastPop = 1f
    private val anchorX = 2f * settings.cxFrac - 1f
    private val anchorY = 1f - 2f * settings.cyFrac

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = (presentationTimeUs + offsetUs) / 1000
        val idx = captions.indexOfFirst { ms >= it.startMs && ms < it.endMs }
        if (idx < 0) { lastKey = -1; last = empty; lastPop = 1f; return empty }
        val c = captions[idx]
        lastPop = CaptionStyle.popScale(c, ms, settings)
        val key = (idx.toLong() shl 20) or (CaptionStyle.cacheKey(c, ms, settings).toLong() and 0xFFFFF)
        if (key == lastKey) return last
        lastKey = key
        last = cache.getOrPut(key) { CaptionStyle.renderBlock(c, ms, settings, frameW, frameH) }
        // Speichergrenze: höchstens ~40 MB Bitmaps im Cache halten
        var bytes = 0L; cache.values.forEach { bytes += it.byteCount }
        if (bytes > 40L * 1024 * 1024) { cache.clear(); cache[key] = last }
        return last
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        OverlaySettings.Builder()
            .setBackgroundFrameAnchor(anchorX, anchorY)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(lastPop, lastPop)
            .setRotationDegrees(-settings.rotationDeg)   // Media3: gegen den Uhrzeigersinn positiv
            .build()
}
