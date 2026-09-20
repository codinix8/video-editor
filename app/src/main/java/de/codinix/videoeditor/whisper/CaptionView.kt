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
    /** Doppeltipp auf den Untertitel: Editor öffnen (mit Index des Blocks). */
    var onEditRequested: ((Int) -> Unit)? = null
    private var lastTapAt = 0L

    private var timeMs = 0L
    private var current: Caption? = null
    private var currentIdx = -1
    private val cache = HashMap<Long, Bitmap>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var lastRect = android.graphics.RectF()

    // Gesten
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var startDist = 0f
    private var startScale = 1f
    private var startAngle = 0f
    private var startRot = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    fun setTime(ms: Long) {
        timeMs = ms
        val idx = captions.indexOfFirst { ms >= it.startMs && ms < it.endMs }
        val c = if (idx >= 0) captions[idx] else null
        val needsRedraw = c !== current || c != null
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
        val cx = width * settings.cxFrac
        val cy = height * settings.cyFrac
        val w = bmp.width * pop; val h = bmp.height * pop
        lastRect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        canvas.save()
        canvas.rotate(settings.rotationDeg, cx, cy)
        canvas.drawBitmap(bmp, null, lastRect, paint)
        canvas.restore()
        if (pop != 1f) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (current == null || lastRect.isEmpty) return false
                // Treffer im (zurückgedrehten) Block?
                val cx = lastRect.centerX(); val cy = lastRect.centerY()
                val rad = Math.toRadians(-settings.rotationDeg.toDouble())
                val dx = e.x - cx; val dy = e.y - cy
                val lx = (dx * Math.cos(rad) - dy * Math.sin(rad)).toFloat() + cx
                val ly = (dx * Math.sin(rad) + dy * Math.cos(rad)).toFloat() + cy
                val hit = android.graphics.RectF(lastRect).apply { inset(-40f, -40f) }.contains(lx, ly)
                if (!hit) return false
                val now = System.currentTimeMillis()
                if (now - lastTapAt < 320) { lastTapAt = 0; dragging = false; onEditRequested?.invoke(currentIdx); return true }
                lastTapAt = now
                dragging = true; lastX = e.x; lastY = e.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (dragging && e.pointerCount == 2) {
                    startDist = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)); startScale = settings.scale
                    startAngle = Math.toDegrees(Math.atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
                    startRot = settings.rotationDeg
                    lastMidX = (e.getX(0) + e.getX(1)) / 2f; lastMidY = (e.getY(0) + e.getY(1)) / 2f
                }
                return dragging
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                if (e.pointerCount >= 2 && startDist > 0) {
                    val d = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                    settings.scale = (startScale * d / startDist).coerceIn(0.5f, 2.2f)
                    val ang = Math.toDegrees(Math.atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
                    settings.rotationDeg = startRot + (ang - startAngle)
                    val mx = (e.getX(0) + e.getX(1)) / 2f; val my = (e.getY(0) + e.getY(1)) / 2f
                    settings.cxFrac = (settings.cxFrac + (mx - lastMidX) / width).coerceIn(0.05f, 0.95f)
                    settings.cyFrac = (settings.cyFrac + (my - lastMidY) / height).coerceIn(0.05f, 0.95f)
                    lastMidX = mx; lastMidY = my
                    cache.clear()
                } else {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        settings.cxFrac = (settings.cxFrac + dx / width).coerceIn(0.05f, 0.95f)
                        settings.cyFrac = (settings.cyFrac + dy / height).coerceIn(0.05f, 0.95f)
                    }
                    lastX = e.x; lastY = e.y
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(remaining); lastY = e.getY(remaining); startDist = 0f
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
