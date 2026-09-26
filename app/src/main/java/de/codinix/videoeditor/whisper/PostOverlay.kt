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

/** Text in der Review: liegt über dem ganzen Video, wird beim Speichern eingebrannt. */
class ReviewText(
    val id: Long,
    var text: String,
    var colorArgb: Int,
    var bgColorArgb: Int?,
    var cx: Float = 0.5f,
    var cy: Float = 0.5f,
    var widthFrac: Float = 0.6f,
    var rotationDeg: Float = 0f
) {
    var bitmap: Bitmap = de.codinix.videoeditor.overlay.TextRenderer.render(text, colorArgb, bgColorArgb)
        private set
    fun rerender() { bitmap = de.codinix.videoeditor.overlay.TextRenderer.render(text, colorArgb, bgColorArgb) }
    fun spec() = PostOverlaySpec(bitmap, cx, cy, widthFrac, rotationDeg, Long.MAX_VALUE)
    fun toJson() = org.json.JSONObject().put("text", text).put("color", colorArgb).put("bgColor", bgColorArgb ?: org.json.JSONObject.NULL)
        .put("cx", cx.toDouble()).put("cy", cy.toDouble()).put("widthFrac", widthFrac.toDouble()).put("rot", rotationDeg.toDouble())
    companion object {
        fun fromJson(o: org.json.JSONObject) = ReviewText(System.nanoTime(), o.getString("text"), o.getInt("color"),
            if (o.isNull("bgColor")) null else o.getInt("bgColor"), o.optDouble("cx", 0.5).toFloat(), o.optDouble("cy", 0.5).toFloat(),
            o.optDouble("widthFrac", 0.6).toFloat(), o.optDouble("rot", 0.0).toFloat())
        fun listToJson(l: List<ReviewText>) = org.json.JSONArray().also { a -> l.forEach { a.put(it.toJson()) } }
        fun listFromJson(a: org.json.JSONArray?): MutableList<ReviewText> {
            val out = ArrayList<ReviewText>(); if (a == null) return out
            for (i in 0 until a.length()) out.add(fromJson(a.getJSONObject(i))); return out
        }
    }
}

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
