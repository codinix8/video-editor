package de.codinix.videoeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Plattform-Hilfslinien: zeigt halbtransparent, wo TikTok, Instagram Reels oder YouTube Shorts
 * ihre Oberfläche über das Video legen, plus Instagrams 4:5-Feed-Beschnitt. Nur Anzeige –
 * im Export nicht enthalten. Koordinaten als Anteile des sichtbaren 9:16-Frames.
 */
class SafeZoneView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    /** 0 aus, 1 TikTok, 2 Instagram, 3 YouTube Shorts */
    var platform: Int = 0
        set(v) { field = v; invalidate() }
    /** Breite/Höhe des Frames (fillCenter wie die Vorschau). */
    var frameAspect = 9f / 16f
        set(v) { field = v; invalidate() }

    private val fill = Paint().apply { color = 0x4D000000 }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFD60A.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * resources.displayMetrics.density; setShadowLayer(4f, 0f, 0f, Color.BLACK) }
    private val frame = RectF()

    override fun onDraw(canvas: Canvas) {
        if (platform == 0) return
        val vw = width.toFloat(); val vh = height.toFloat()
        val scale = maxOf(vw / frameAspect, vh)
        val fw = frameAspect * scale; val fh = scale
        frame.set((vw - fw) / 2, (vh - fh) / 2, (vw + fw) / 2, (vh + fh) / 2)
        fun x(f: Float) = frame.left + f * fw
        fun y(f: Float) = frame.top + f * fh
        fun zone(l: Float, t: Float, r: Float, b: Float, label: String) {
            val rc = RectF(x(l), y(t), x(r), y(b))
            canvas.drawRect(rc, fill); canvas.drawRect(rc, stroke)
            canvas.drawText(label, rc.left + 8f, rc.top + text.textSize + 6f, text)
        }
        when (platform) {
            1 -> { zone(0f, 0f, 1f, 0.07f, "TikTok"); zone(0f, 0.76f, 1f, 1f, "Beschreibung"); zone(0.85f, 0.40f, 1f, 0.76f, "") }
            2 -> {
                zone(0f, 0f, 1f, 0.07f, "Instagram"); zone(0f, 0.78f, 1f, 1f, "Beschreibung"); zone(0.85f, 0.45f, 1f, 0.78f, "")
                // 4:5-Feed-Beschnitt
                val cut = (1f - (frameAspect / 0.8f)) / 2f
                canvas.drawLine(x(0f), y(cut), x(1f), y(cut), line); canvas.drawLine(x(0f), y(1f - cut), x(1f), y(1f - cut), line)
                canvas.drawText("Feed 4:5", x(0f) + 8f, y(cut) - 6f, text)
            }
            3 -> { zone(0f, 0f, 1f, 0.10f, "Shorts"); zone(0f, 0.80f, 1f, 1f, "Titel"); zone(0.85f, 0.50f, 1f, 0.80f, "") }
        }
    }
}
