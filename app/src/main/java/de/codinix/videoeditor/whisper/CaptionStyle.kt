package de.codinix.videoeditor.whisper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Zeichnet einen Untertitel-Block im Stil „Klassisch“: weiß, fett, dunkle Kontur, zentriert.
 * Alle Größen relativ zur Frame-Höhe, damit Review (Bildschirm) und Export (Videoauflösung)
 * identisch aussehen. Weitere Vorlagen kommen hier hinzu.
 */
object CaptionStyle {
    /** Textgröße als Anteil der Frame-Höhe. */
    const val TEXT_FRAC = 0.042f
    /** Maximale Breite als Anteil der Frame-Breite. */
    const val WIDTH_FRAC = 0.86f
    /** Vertikale Mitte des Blocks als Anteil der Frame-Höhe (0 = oben). */
    const val CENTER_Y_FRAC = 0.80f

    fun makePaint(frameH: Int): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = frameH * TEXT_FRAC
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
    }

    fun layout(text: String, paint: TextPaint, frameW: Int): StaticLayout {
        val width = (frameW * WIDTH_FRAC).toInt()
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.05f)
            .build()
    }

    /** Zeichnet den Block auf ein Canvas der Größe frameW×frameH (Ursprung oben links). */
    fun draw(canvas: Canvas, text: String, frameW: Int, frameH: Int) {
        val paint = makePaint(frameH)
        val layout = layout(text, paint, frameW)
        val outline = TextPaint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = paint.textSize * 0.14f
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        val outlineLayout = StaticLayout.Builder.obtain(text, 0, text.length, outline, layout.width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).setLineSpacing(0f, 1.05f).build()
        val x = (frameW - layout.width) / 2f
        val y = frameH * CENTER_Y_FRAC - layout.height / 2f
        canvas.save(); canvas.translate(x, y)
        outlineLayout.draw(canvas); layout.draw(canvas)
        canvas.restore()
    }

    /** Vollbild-Bitmap (transparent) mit dem Block – für den Export. */
    fun renderFrame(text: String, frameW: Int, frameH: Int): Bitmap {
        val bmp = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), text, frameW, frameH)
        return bmp
    }
}
