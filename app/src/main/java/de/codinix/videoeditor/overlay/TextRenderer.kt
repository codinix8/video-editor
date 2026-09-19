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
    private const val TEXT_SIZE = 72f
    private const val MAX_WIDTH = 1000
    private const val PADDING = 28f
    private const val RADIUS = 28f

    fun render(text: String, colorArgb: Int, background: Boolean): Bitmap {
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

        if (background) {
            // Balkenfarbe: dunkel bei hellem Text, hell bei dunklem Text
            val lum = Color.luminance(colorArgb)
            val bg = if (lum > 0.5f) Color.argb(200, 0, 0, 0) else Color.argb(220, 255, 255, 255)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), RADIUS, RADIUS, bgPaint)
        }

        canvas.save()
        canvas.translate(PADDING, PADDING)
        if (!background) {
            // Kontur für Lesbarkeit
            val outline = TextPaint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = TEXT_SIZE * 0.12f
                color = if (Color.luminance(colorArgb) > 0.5f) Color.BLACK else Color.WHITE
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

    /** Auswahlfarben für den Dialog. */
    val COLORS = intArrayOf(
        Color.WHITE, Color.BLACK, 0xFFFFD60A.toInt(), 0xFFFF3B4E.toInt(),
        0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFFF6BCB.toInt(), 0xFFFF9500.toInt()
    )
}
