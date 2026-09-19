package de.codinix.videoeditor.whisper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Zeigt in der Review den passenden Untertitel über dem Video und lässt ihn per Finger
 * verschieben (vertikal) und skalieren (zwei Finger). Berührungen außerhalb des Blocks
 * werden durchgereicht (Play/Pause des Players).
 */
class CaptionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var captions: List<Caption> = emptyList()
        set(v) { field = v; cache.clear(); invalidate() }
    var settings = CaptionSettings()
        set(v) { field = v; cache.clear(); invalidate() }
    var onSettingsChanged: (() -> Unit)? = null

    private var timeMs = 0L
    private var current: Caption? = null
    private var currentIdx = -1
    private val cache = HashMap<Long, Bitmap>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var lastRect = android.graphics.RectF()

    // Gesten
    private var dragging = false
    private var lastY = 0f
    private var startDist = 0f
    private var startScale = 1f

    fun setTime(ms: Long) {
        timeMs = ms
        val idx = captions.indexOfFirst { ms >= it.startMs && ms < it.endMs }
        val c = if (idx >= 0) captions[idx] else null
        val needsRedraw = c !== current || (c != null && settings.template in listOf(CaptionStyle.TEMPLATE_KARAOKE, CaptionStyle.TEMPLATE_WORD))
        current = c; currentIdx = idx
        if (needsRedraw) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val c = current ?: run { lastRect.setEmpty(); return }
        if (width == 0 || height == 0) return
        val key = (currentIdx.toLong() shl 20) or (CaptionStyle.cacheKey(c, timeMs, settings).toLong() and 0xFFFFF)
        val bmp = cache.getOrPut(key) { CaptionStyle.renderBlock(c, timeMs, settings, width, height) }
        if (cache.size > 64) cache.clear()
        val pop = CaptionStyle.popScale(c, timeMs, settings)
        val cx = width / 2f
        val cy = height * settings.cyFrac
        val w = bmp.width * pop; val h = bmp.height * pop
        lastRect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        canvas.drawBitmap(bmp, null, lastRect, paint)
        if (pop != 1f) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (current == null || lastRect.isEmpty) return false
                val hit = android.graphics.RectF(lastRect).apply { inset(-40f, -40f) }.contains(e.x, e.y)
                if (!hit) return false
                dragging = true; lastY = e.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (dragging && e.pointerCount == 2) {
                    startDist = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)); startScale = settings.scale
                }
                return dragging
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                if (e.pointerCount >= 2 && startDist > 0) {
                    val d = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                    settings.scale = (startScale * d / startDist).coerceIn(0.5f, 2.2f)
                    cache.clear()
                } else {
                    val dy = e.y - lastY
                    if (abs(dy) > 0.5f) settings.cyFrac = (settings.cyFrac + dy / height).coerceIn(0.08f, 0.95f)
                    lastY = e.y
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (e.actionIndex == 0) 1 else 0
                lastY = e.getY(remaining); startDist = 0f
                return dragging
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) { dragging = false; onSettingsChanged?.invoke(); return true }
                return false
            }
        }
        return super.onTouchEvent(e)
    }
}
