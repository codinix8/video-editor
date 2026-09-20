package de.codinix.videoeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Dünner Balken oben im Bild: zeigt abgeschlossene Segmente als weiße Blöcke,
 * das laufende Segment in Rot, mit kleinen Lücken dazwischen (wie bei TikTok).
 * Skaliert sich auf mindestens 60 Sekunden, wächst darüber hinaus mit.
 */
class SegmentProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B4E.toInt() }
    private val armedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt() }

    private var segmentsMs: List<Long> = emptyList()
    private var liveMs: Long = 0
    private var lastArmed = false
    /** Wiedergabeposition in ms (Review-Modus) oder -1 im Aufnahmemodus. */
    private var playheadMs = -1L
    /** Skala des Aufnahmemodus: Gesamtlimit (Balken voll = Limit erreicht). */
    var limitMs: Long = 0L
    /** Letzte Sekunden vor dem Limit: laufendes Segment orange. */
    var warn: Boolean = false

    private val rect = RectF()

    fun update(segments: List<Long>, live: Long, armed: Boolean) {
        segmentsMs = segments
        liveMs = live
        lastArmed = armed
        playheadMs = -1
        invalidate()
    }

    /** Review: alle Segmente sichtbar, abgespielter Teil rot. Skala = Gesamtlänge. */
    fun updatePlayback(segments: List<Long>, positionMs: Long, armed: Boolean) {
        segmentsMs = segments
        liveMs = 0
        lastArmed = armed
        playheadMs = positionMs.coerceAtLeast(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val total = segmentsMs.sum() + liveMs
        val scale = (if (playheadMs >= 0) maxOf(1L, total) else if (limitMs > 0) limitMs else maxOf(MIN_SCALE_MS, total)).toFloat()
        val gap = h * 0.6f
        var x = 0f

        segmentsMs.forEachIndexed { i, ms ->
            val len = ms / scale * w
            val paint = if (lastArmed && i == segmentsMs.lastIndex) armedPaint else segmentPaint
            rect.set(x, 0f, (x + len - gap).coerceAtLeast(x + 1f), h)
            canvas.drawRoundRect(rect, r, r, paint)
            x += len
        }
        if (liveMs > 0) {
            val len = liveMs / scale * w
            rect.set(x, 0f, x + len, h)
            canvas.drawRoundRect(rect, r, r, if (warn) armedPaint else livePaint)
        }
        if (playheadMs >= 0 && total > 0) {
            val px = (playheadMs / scale * w).coerceIn(0f, w)
            rect.set(0f, 0f, px, h)
            canvas.save()
            canvas.clipRect(rect)
            // Roten Fortschritt nur über den Segmentblöcken zeichnen
            var sx = 0f
            segmentsMs.forEach { ms ->
                val len = ms / scale * w
                rect.set(sx, 0f, (sx + len - gap).coerceAtLeast(sx + 1f), h)
                canvas.drawRoundRect(rect, r, r, livePaint)
                sx += len
            }
            canvas.restore()
        }
    }

    companion object {
        private const val MIN_SCALE_MS = 60_000L
    }
}
