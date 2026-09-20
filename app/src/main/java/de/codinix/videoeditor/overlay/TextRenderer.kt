package de.codinix.videoeditor.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Rendert Text (inkl. Emojis, die das System-Font liefert) in ein Bitmap.
 * Feste Ausgabegröße in Pixeln – die Skalierung im Bild macht das Overlay per widthFrac.
 * Mit Hintergrund: abgerundeter Balken. Ohne: Text mit Kontur, damit er auf jedem
 * Hintergrund lesbar bleibt.
 */
object TextRenderer {
    private const val TEXT_SIZE = 160f
    private const val MAX_WIDTH = 2200
    private const val PADDING = 60f
    private const val RADIUS = 60f

    /**
     * @param colorArgb Textfarbe inkl. Alpha
     * @param bgColorArgb Hintergrundfarbe inkl. Alpha oder null für keinen Hintergrund
     */
    fun render(text: String, colorArgb: Int, bgColorArgb: Int?): Bitmap {
        val background = bgColorArgb != null
        val content = text.ifBlank { " " }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TEXT_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = colorArgb
        }
        val measured = paint.measureText(content.lines().maxByOrNull { paint.measureText(it) } ?: content)
        val width = measured.coerceIn(TEXT_SIZE, MAX_WIDTH.toFloat()).toInt()
        val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.05f)
            .build()

        val w = (layout.width + PADDING * 2).toInt().coerceAtLeast(2)
        val h = (layout.height + PADDING * 2).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (bgColorArgb != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColorArgb }
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), RADIUS, RADIUS, bgPaint)
        }

        canvas.save()
        canvas.translate(PADDING, PADDING)
        if (!background) {
            // Kontur für Lesbarkeit
            val outline = TextPaint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = TEXT_SIZE * 0.12f
                val base = if (Color.luminance(colorArgb) > 0.5f) Color.BLACK else Color.WHITE
                color = (base and 0x00FFFFFF) or (Color.alpha(colorArgb) shl 24)  // Kontur mit Text-Alpha
                strokeJoin = Paint.Join.ROUND
            }
            StaticLayout.Builder.obtain(content, 0, content.length, outline, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.05f)
                .build().draw(canvas)
        }
        layout.draw(canvas)
        canvas.restore()
        return bmp
    }

    /** Gemeinsame Palette für Text- und Untertitel-Dialog: Grautöne, dann Farbkreis (hell/kräftig). */
    val COLORS = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFD9D9D9.toInt(), 0xFF9E9E9E.toInt(), 0xFF555555.toInt(), 0xFF000000.toInt(),
        0xFFFFF176.toInt(), 0xFFFFD60A.toInt(),
        0xFFFFB74D.toInt(), 0xFFFF9500.toInt(),
        0xFFFF8A80.toInt(), 0xFFFF3B4E.toInt(), 0xFFC62828.toInt(),
        0xFFFF6BCB.toInt(), 0xFFE91E63.toInt(),
        0xFFCE93D8.toInt(), 0xFFAF52DE.toInt(),
        0xFF82B1FF.toInt(), 0xFF0A84FF.toInt(), 0xFF1A237E.toInt(),
        0xFF80DEEA.toInt(), 0xFF32ADE6.toInt(),
        0xFFA5D6A7.toInt(), 0xFF34C759.toInt(), 0xFF1B5E20.toInt()
    )
}
