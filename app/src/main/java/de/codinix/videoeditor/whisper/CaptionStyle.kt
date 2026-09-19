package de.codinix.videoeditor.whisper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import org.json.JSONObject

/** Einstellungen der Untertitel-Darstellung: Vorlage, Position, Größe, Akzentfarbe. */
data class CaptionSettings(
    var template: Int = CaptionStyle.TEMPLATE_CLASSIC,
    /** Vertikale Mitte des Blocks, Anteil der Frame-Höhe (0 = oben). */
    var cyFrac: Float = 0.80f,
    /** Größenfaktor relativ zur Grundgröße. */
    var scale: Float = 1f,
    var accentColor: Int = 0xFFFFD60A.toInt()
) {
    fun toJson() = JSONObject().put("template", template).put("cy", cyFrac.toDouble())
        .put("scale", scale.toDouble()).put("accent", accentColor)
    companion object {
        fun fromJson(o: JSONObject?): CaptionSettings = if (o == null) CaptionSettings() else CaptionSettings(
            o.optInt("template", 0), o.optDouble("cy", 0.8).toFloat(), o.optDouble("scale", 1.0).toFloat(),
            o.optInt("accent", 0xFFFFD60A.toInt()))
    }
}

/**
 * Rendert einen Untertitel-Block als eigenständiges Bitmap (eng zugeschnitten). Position
 * und Skalierung übernehmen Review (Canvas) und Export (OverlaySettings) getrennt, damit
 * beide identisch aussehen. Alle Größen sind relativ zur Frame-Höhe.
 */
object CaptionStyle {
    const val TEMPLATE_CLASSIC = 0   // weiß, fett, Kontur
    const val TEMPLATE_BOX = 1       // weiß auf dunklem Balken
    const val TEMPLATE_KARAOKE = 2   // gesprochene Wörter leuchten in Akzentfarbe
    const val TEMPLATE_WORD = 3      // nur das aktuelle Wort, groß
    const val TEMPLATE_ACCENT = 4    // Schlüsselwörter farbig

    val TEMPLATE_NAMES = listOf("Klassisch", "Balken", "Karaoke", "Wort für Wort", "Akzent")

    private const val TEXT_FRAC = 0.042f
    private const val WIDTH_FRAC = 0.86f

    /** Welches Wort ist zur Zeit t aktiv (Index in caption.words), -1 wenn keins. */
    fun activeWordIndex(c: Caption, timeMs: Long): Int {
        var idx = -1
        for (i in c.words.indices) if (c.words[i].startMs <= timeMs) idx = i else break
        return idx
    }

    /** Schlüssel für den Bitmap-Cache: ändert sich nur, wenn sich das Bild ändern muss. */
    fun cacheKey(c: Caption, timeMs: Long, s: CaptionSettings): Int = when (s.template) {
        TEMPLATE_KARAOKE, TEMPLATE_WORD -> activeWordIndex(c, timeMs)
        else -> 0
    }

    /** Pop-Skalierung fürs Wort-für-Wort-Template (1.0 = normal). */
    fun popScale(c: Caption, timeMs: Long, s: CaptionSettings): Float {
        if (s.template != TEMPLATE_WORD) return 1f
        val i = activeWordIndex(c, timeMs)
        if (i < 0) return 1f
        val dt = (timeMs - c.words[i].startMs).coerceAtLeast(0)
        return if (dt < 140) 1f + 0.18f * (1f - dt / 140f) else 1f
    }

    private fun isKeyword(index: Int, word: String) = word.length >= 7 || index % 3 == 2

    fun renderBlock(c: Caption, timeMs: Long, s: CaptionSettings, frameW: Int, frameH: Int): Bitmap {
        val textSize = frameH * TEXT_FRAC * s.scale * (if (s.template == TEMPLATE_WORD) 1.7f else 1f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
        }
        val active = activeWordIndex(c, timeMs)

        // Text und Spans je Vorlage
        val text: CharSequence = when (s.template) {
            TEMPLATE_WORD -> if (active >= 0) c.words[active].text else ""
            TEMPLATE_KARAOKE, TEMPLATE_ACCENT -> {
                val sb = SpannableString(c.words.joinToString(" ") { it.text })
                var pos = 0
                c.words.forEachIndexed { i, w ->
                    val highlight = if (s.template == TEMPLATE_KARAOKE) i <= active else isKeyword(i, w.text)
                    if (highlight) sb.setSpan(ForegroundColorSpan(s.accentColor), pos, pos + w.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    pos += w.text.length + 1
                }
                sb
            }
            else -> c.text
        }
        if (text.isEmpty()) return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val maxWidth = (frameW * WIDTH_FRAC).toInt()
        fun layoutFor(p: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, p, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).setLineSpacing(0f, 1.05f).build()
        val layout = layoutFor(paint)
        // Tatsächlich genutzte Breite
        var used = 0f
        for (i in 0 until layout.lineCount) used = maxOf(used, layout.getLineWidth(i))
        val pad = textSize * (if (s.template == TEMPLATE_BOX) 0.45f else 0.25f)
        val w = (used + pad * 2).toInt().coerceAtLeast(2)
        val h = (layout.height + pad * 2).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (s.template == TEMPLATE_BOX) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), textSize * 0.35f, textSize * 0.35f, bg)
        }
        canvas.save()
        canvas.translate((w - layout.width) / 2f, pad)
        if (s.template != TEMPLATE_BOX) {
            val outline = TextPaint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = textSize * 0.14f
                strokeJoin = Paint.Join.ROUND
                color = Color.BLACK
            }
            // Kontur ohne Farb-Spans zeichnen
            val plain = text.toString()
            StaticLayout.Builder.obtain(plain, 0, plain.length, outline, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).setLineSpacing(0f, 1.05f).build().draw(canvas)
        }
        layout.draw(canvas)
        canvas.restore()
        return bmp
    }
}
