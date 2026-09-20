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

/** Einstellungen der Untertitel-Darstellung: Vorlage, Position, Größe, Drehung, Akzentfarbe. */
data class CaptionSettings(
    var template: Int = CaptionStyle.TEMPLATE_CLASSIC,
    /** Mitte des Blocks als Anteil der Frame-Breite/-Höhe (0,0 = oben links). */
    var cxFrac: Float = 0.5f,
    var cyFrac: Float = 0.80f,
    /** Größenfaktor relativ zur Grundgröße. */
    var scale: Float = 1f,
    /** Drehung im Uhrzeigersinn. */
    var rotationDeg: Float = 0f,
    var accentColor: Int = 0xFFFFD60A.toInt(),
    /** Emojis aus dem Wörterbuch hinter passende Wörter setzen. */
    var emojis: Boolean = false
) {
    fun toJson() = JSONObject().put("template", template).put("cx", cxFrac.toDouble()).put("cy", cyFrac.toDouble())
        .put("scale", scale.toDouble()).put("rot", rotationDeg.toDouble()).put("accent", accentColor).put("emojis", emojis)
    companion object {
        fun fromJson(o: JSONObject?): CaptionSettings = if (o == null) CaptionSettings() else CaptionSettings(
            o.optInt("template", 0), o.optDouble("cx", 0.5).toFloat(), o.optDouble("cy", 0.8).toFloat(),
            o.optDouble("scale", 1.0).toFloat(), o.optDouble("rot", 0.0).toFloat(), o.optInt("accent", 0xFFFFD60A.toInt()),
            o.optBoolean("emojis", false))
    }
}

/**
 * Rendert einen Untertitel-Block als eigenständiges, eng zugeschnittenes Bitmap.
 * Position, Skalierung und Drehung übernehmen Review (Canvas) und Export (OverlaySettings).
 * Alle Größen sind relativ zur Frame-Höhe, damit beide identisch aussehen.
 */
object CaptionStyle {
    const val TEMPLATE_CLASSIC = 0
    const val TEMPLATE_BOX = 1
    const val TEMPLATE_KARAOKE = 2
    const val TEMPLATE_WORD = 3
    const val TEMPLATE_ACCENT = 4
    const val TEMPLATE_NEON = 5
    const val TEMPLATE_HIGHLIGHT = 6
    const val TEMPLATE_SHADOW = 7
    const val TEMPLATE_TWO_TONE = 8
    const val TEMPLATE_TYPEWRITER = 9
    const val TEMPLATE_BIG_SMALL = 10

    val TEMPLATE_NAMES = listOf(
        "Klassisch", "Balken", "Karaoke", "Wort für Wort", "Akzent",
        "Neon", "Highlight-Box", "Schatten", "Zweifarbig", "Schreibmaschine", "Groß & klein"
    )

    val ACCENT_COLORS: IntArray get() = de.codinix.videoeditor.overlay.TextRenderer.COLORS

    private const val TEXT_FRAC = 0.042f
    private const val WIDTH_FRAC = 0.86f

    fun activeWordIndex(c: Caption, timeMs: Long): Int {
        var idx = -1
        for (i in c.words.indices) if (c.words[i].startMs <= timeMs) idx = i else break
        return idx
    }

    private fun typewriterChars(c: Caption, timeMs: Long): Int {
        val total = c.text.length
        val dur = ((c.endMs - c.startMs) * 0.6f).coerceAtLeast(300f)
        val f = ((timeMs - c.startMs) / dur).coerceIn(0f, 1f)
        return (total * f).toInt().coerceIn(1, total)
    }

    /** Schlüssel für den Bitmap-Cache: ändert sich nur, wenn sich das Bild ändern muss. */
    fun cacheKey(c: Caption, timeMs: Long, s: CaptionSettings): Int = when (s.template) {
        TEMPLATE_KARAOKE, TEMPLATE_WORD, TEMPLATE_HIGHLIGHT, TEMPLATE_BIG_SMALL -> activeWordIndex(c, timeMs)
        TEMPLATE_TYPEWRITER -> typewriterChars(c, timeMs)
        else -> 0
    }

    /** Pop-Skalierung (1.0 = normal) für wortweise Vorlagen. */
    fun popScale(c: Caption, timeMs: Long, s: CaptionSettings): Float {
        if (s.template != TEMPLATE_WORD && s.template != TEMPLATE_BIG_SMALL) return 1f
        val i = activeWordIndex(c, timeMs)
        if (i < 0) return 1f
        val dt = (timeMs - c.words[i].startMs).coerceAtLeast(0)
        return if (dt < 140) 1f + 0.16f * (1f - dt / 140f) else 1f
    }

    private fun isKeyword(index: Int, word: String) = word.length >= 7 || index % 3 == 2

    private fun basePaint(size: Float, color: Int = Color.WHITE) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        this.color = color
    }

    private fun layoutOf(text: CharSequence, p: TextPaint, maxWidth: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).setLineSpacing(0f, 1.05f).build()

    private fun usedWidth(l: StaticLayout): Float { var w = 0f; for (i in 0 until l.lineCount) w = maxOf(w, l.getLineWidth(i)); return w }

    /** Wörter mit Emoji-Anhang (wenn eingeschaltet), Zeiten bleiben. */
    fun decorate(c: Caption, s: CaptionSettings): Caption {
        if (!s.emojis) return c
        val words = c.words.map { w -> EmojiDict.forWord(w.text)?.let { e -> w.copy(text = w.text + " " + e) } ?: w }
        return c.copy(words = words, text = words.joinToString(" ") { it.text })
    }

    fun renderBlock(c0: Caption, timeMs: Long, s: CaptionSettings, frameW: Int, frameH: Int): Bitmap {
        val c = decorate(c0, s)
        val base = frameH * TEXT_FRAC * s.scale
        val maxWidth = (frameW * WIDTH_FRAC).toInt()
        val active = activeWordIndex(c, timeMs)
        val joined = c.words.joinToString(" ") { it.text }.ifEmpty { c.text }

        // Wort-Zeichenbereiche für Spans/Boxen
        val ranges = ArrayList<IntRange>()
        run { var pos = 0; c.words.forEach { w -> ranges.add(pos until pos + w.text.length); pos += w.text.length + 1 } }

        when (s.template) {
            TEMPLATE_WORD -> {
                val word = if (active >= 0) c.words[active].text else return empty()
                return drawSimple(word, basePaint(base * 1.7f), maxWidth, outline = true, pad = 0.25f)
            }
            TEMPLATE_BIG_SMALL -> {
                val word = if (active >= 0) c.words[active].text else return empty()
                val big = basePaint(base * 1.5f, s.accentColor)
                val small = basePaint(base * 0.7f)
                val lb = layoutOf(word, big, maxWidth)
                val ls = layoutOf(joined, small, maxWidth)
                val pad = base * 0.25f
                val w = (maxOf(usedWidth(lb), usedWidth(ls)) + pad * 2).toInt().coerceAtLeast(2)
                val h = (lb.height + ls.height + pad * 2.5f).toInt().coerceAtLeast(2)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val cv = Canvas(bmp)
                cv.save(); cv.translate((w - lb.width) / 2f, pad); drawOutlined(cv, lb, big, word, maxWidth); cv.restore()
                cv.save(); cv.translate((w - ls.width) / 2f, pad + lb.height + pad * 0.5f); drawOutlined(cv, ls, small, joined, maxWidth); cv.restore()
                return bmp
            }
            TEMPLATE_TYPEWRITER -> {
                val n = typewriterChars(c, timeMs)
                return drawSimple(joined.substring(0, n.coerceAtMost(joined.length)), basePaint(base), maxWidth, outline = true, pad = 0.25f)
            }
            TEMPLATE_BOX -> {
                val p = basePaint(base)
                val l = layoutOf(joined, p, maxWidth)
                val pad = base * 0.45f
                val w = (usedWidth(l) + pad * 2).toInt().coerceAtLeast(2); val h = (l.height + pad * 2).toInt().coerceAtLeast(2)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
                cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), base * 0.35f, base * 0.35f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) })
                cv.save(); cv.translate((w - l.width) / 2f, pad); l.draw(cv); cv.restore()
                return bmp
            }
            TEMPLATE_NEON -> {
                val p = basePaint(base, Color.WHITE).apply { setShadowLayer(base * 0.45f, 0f, 0f, s.accentColor) }
                val l = layoutOf(joined, p, maxWidth)
                val pad = base * 0.6f
                val w = (usedWidth(l) + pad * 2).toInt().coerceAtLeast(2); val h = (l.height + pad * 2).toInt().coerceAtLeast(2)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
                cv.save(); cv.translate((w - l.width) / 2f, pad)
                l.draw(cv); l.draw(cv)   // doppelt: kräftigeres Leuchten
                val core = basePaint(base, s.accentColor)
                layoutOf(joined, core, maxWidth).let { cl -> cv.translate((l.width - cl.width) / 2f, 0f); cl.draw(cv) }
                cv.restore()
                return bmp
            }
            TEMPLATE_SHADOW -> {
                val p = basePaint(base)
                val l = layoutOf(joined, p, maxWidth)
                val off = base * 0.09f
                val pad = base * 0.3f
                val w = (usedWidth(l) + pad * 2 + off).toInt().coerceAtLeast(2); val h = (l.height + pad * 2 + off).toInt().coerceAtLeast(2)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
                val shadow = basePaint(base, Color.BLACK)
                val ls = layoutOf(joined, shadow, maxWidth)
                cv.save(); cv.translate((w - l.width) / 2f + off, pad + off); ls.draw(cv); cv.restore()
                cv.save(); cv.translate((w - l.width) / 2f, pad); l.draw(cv); cv.restore()
                return bmp
            }
            TEMPLATE_HIGHLIGHT -> {
                val p = basePaint(base)
                val text = SpannableString(joined)
                if (active >= 0 && active < ranges.size) {
                    text.setSpan(ForegroundColorSpan(Color.BLACK), ranges[active].first, ranges[active].last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val l = layoutOf(text, p, maxWidth)
                val pad = base * 0.35f
                val w = (usedWidth(l) + pad * 2).toInt().coerceAtLeast(2); val h = (l.height + pad * 2).toInt().coerceAtLeast(2)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
                cv.save(); cv.translate((w - l.width) / 2f, pad)
                if (active >= 0 && active < ranges.size) {
                    val r = ranges[active]
                    val line = l.getLineForOffset(r.first)
                    val x0 = l.getPrimaryHorizontal(r.first); val x1 = l.getPrimaryHorizontal(r.last + 1)
                    val top = l.getLineTop(line).toFloat(); val bottom = l.getLineBottom(line).toFloat()
                    val m = base * 0.12f
                    cv.drawRoundRect(RectF(minOf(x0, x1) - m, top, maxOf(x0, x1) + m, bottom), base * 0.2f, base * 0.2f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.accentColor })
                }
                // Kontur nur für nicht hervorgehobene Wörter (schwarzer Text braucht keine)
                val outline = TextPaint(p).apply { style = Paint.Style.STROKE; strokeWidth = base * 0.14f; strokeJoin = Paint.Join.ROUND; color = Color.BLACK }
                val plain = SpannableString(joined)
                if (active >= 0 && active < ranges.size) plain.setSpan(ForegroundColorSpan(Color.TRANSPARENT), ranges[active].first, ranges[active].last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                layoutOf(plain, outline, maxWidth).draw(cv)
                l.draw(cv)
                cv.restore()
                return bmp
            }
            TEMPLATE_KARAOKE, TEMPLATE_ACCENT, TEMPLATE_TWO_TONE -> {
                val text = SpannableString(joined)
                c.words.forEachIndexed { i, w ->
                    val color = when (s.template) {
                        TEMPLATE_KARAOKE -> if (i <= active) s.accentColor else null
                        TEMPLATE_ACCENT -> if (isKeyword(i, w.text)) s.accentColor else null
                        else -> if (i % 2 == 1) s.accentColor else null
                    }
                    if (color != null && i < ranges.size) text.setSpan(ForegroundColorSpan(color), ranges[i].first, ranges[i].last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                return drawSimple(text, basePaint(base), maxWidth, outline = true, pad = 0.25f)
            }
            else -> return drawSimple(joined, basePaint(base), maxWidth, outline = true, pad = 0.25f)
        }
    }

    private fun empty() = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private fun drawOutlined(cv: Canvas, l: StaticLayout, p: TextPaint, plain: String, maxWidth: Int) {
        val outline = TextPaint(p).apply { style = Paint.Style.STROKE; strokeWidth = p.textSize * 0.14f; strokeJoin = Paint.Join.ROUND; color = Color.BLACK; clearShadowLayer() }
        layoutOf(plain, outline, maxWidth).let { ol -> cv.save(); cv.translate((l.width - ol.width) / 2f, 0f); ol.draw(cv); cv.restore() }
        l.draw(cv)
    }

    private fun drawSimple(text: CharSequence, p: TextPaint, maxWidth: Int, outline: Boolean, pad: Float): Bitmap {
        if (text.isEmpty()) return empty()
        val l = layoutOf(text, p, maxWidth)
        val padPx = p.textSize * pad
        val w = (usedWidth(l) + padPx * 2).toInt().coerceAtLeast(2); val h = (l.height + padPx * 2).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
        cv.save(); cv.translate((w - l.width) / 2f, padPx)
        if (outline) drawOutlined(cv, l, p, text.toString(), maxWidth) else l.draw(cv)
        cv.restore()
        return bmp
    }

    /** Vorschau einer Vorlage mit Beispieltext für den Auswahl-Dialog. */
    fun preview(template: Int, accent: Int, frameW: Int, frameH: Int): Bitmap {
        val words = listOf("Dein", "Text", "sieht", "so", "aus")
        val ws = words.mapIndexed { i, w -> Word(i * 400L, i * 400L + 380, w) }
        val c = Caption(0, 2000, words.joinToString(" "), ws)
        val s = CaptionSettings(template = template, accentColor = accent, scale = 1f, emojis = false)
        return renderBlock(c, 1100, s, frameW, frameH)   // Zeitpunkt: drittes Wort aktiv
    }
}
