package de.codinix.videoeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Locale

/**
 * Spulleiste der Review: Segmente als Blöcke, Abspielkopf, Zeitangaben. Ziehen spult,
 * Tippen auf ein Segment springt an dessen Anfang. Beim Halten ohne Bewegung wird das
 * Spulen feiner (Verhältnis Fingerweg → Zeit sinkt), um in langen Videos zu treffen.
 */
class ScrubBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var segmentsMs: List<Long> = emptyList()
        set(v) { field = v; invalidate() }
    var positionMs: Long = 0
        set(v) { if (!scrubbing) { field = v; invalidate() } }

    /** Während des Ziehens: neue Position (ms). */
    var onScrub: ((Long) -> Unit)? = null
    var onScrubStart: (() -> Unit)? = null
    var onScrubEnd: ((Long) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B4E.toInt() }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 12f * resources.displayMetrics.density
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val rect = RectF()

    private var scrubbing = false
    private var scrubPos = 0L
    private var lastX = 0f
    private var fine = 1f
    private var lastMoveAt = 0L

    private val total get() = segmentsMs.sum().coerceAtLeast(1)
    private val barTop get() = height * 0.42f
    private val barBottom get() = height * 0.78f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val pos = if (scrubbing) scrubPos else positionMs
        val r = (barBottom - barTop) / 2
        rect.set(0f, barTop, w, barBottom)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val gap = 3f * resources.displayMetrics.density
        var x = 0f
        segmentsMs.forEach { ms ->
            val len = ms.toFloat() / total * w
            rect.set(x, barTop, (x + len - gap).coerceAtLeast(x + 1f), barBottom)
            canvas.drawRoundRect(rect, r, r, segPaint)
            x += len
        }
        // Abgespielter Anteil
        val px = (pos.toFloat() / total * w).coerceIn(0f, w)
        canvas.save(); canvas.clipRect(0f, barTop, px, barBottom)
        x = 0f
        segmentsMs.forEach { ms ->
            val len = ms.toFloat() / total * w
            rect.set(x, barTop, (x + len - gap).coerceAtLeast(x + 1f), barBottom)
            canvas.drawRoundRect(rect, r, r, playedPaint)
            x += len
        }
        canvas.restore()
        // Abspielkopf
        val headW = 4f * resources.displayMetrics.density
        rect.set(px - headW / 2, barTop - 6f, px + headW / 2, barBottom + 6f)
        canvas.drawRoundRect(rect, headW / 2, headW / 2, headPaint)
        // Zeiten
        val y = barTop - 8f * resources.displayMetrics.density
        canvas.drawText(fmt(pos), 0f, y, textPaint)
        val totalText = fmt(total)
        canvas.drawText(totalText, w - textPaint.measureText(totalText), y, textPaint)
        if (scrubbing && fine < 1f) {
            val hint = "Feinspulen"
            canvas.drawText(hint, (w - textPaint.measureText(hint)) / 2, y, textPaint)
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.GERMANY, "%d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrubbing = true; fine = 1f; lastX = e.x; lastMoveAt = System.currentTimeMillis()
                scrubPos = (e.x / width * total).toLong().coerceIn(0, total - 1)
                onScrubStart?.invoke(); onScrub?.invoke(scrubPos); invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val now = System.currentTimeMillis()
                // Lange still gehalten → feiner
                if (now - lastMoveAt > 600 && fine > 0.2f) fine = if (fine > 0.5f) 0.35f else 0.15f
                val dx = e.x - lastX
                if (dx != 0f) {
                    scrubPos = (scrubPos + (dx / width * total * fine).toLong()).coerceIn(0, total - 1)
                    lastX = e.x; lastMoveAt = now
                    onScrub?.invoke(scrubPos); invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrubbing = false
                positionMs = scrubPos
                onScrubEnd?.invoke(scrubPos)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
