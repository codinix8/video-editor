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
    /** Texte der Review (über das ganze Video). */
    var reviewTexts: MutableList<ReviewText> = mutableListOf()
        set(v) { field = v; invalidate() }
    var onReviewTextChanged: (() -> Unit)? = null
    var onReviewTextEdit: ((ReviewText) -> Unit)? = null
    private var activeText: ReviewText? = null
    private var textStartWidth = 0.6f
    private var textStartRot = 0f
    private var lastTextTapAt = 0L
    private var rawX = 0.5f
    private var rawY = 0.5f
    /** Doppeltipp auf den Untertitel: Editor öffnen (mit Index des Blocks). */
    var onEditRequested: ((Int) -> Unit)? = null
    private var lastTapAt = 0L

    private var timeMs = 0L
    private var current: Caption? = null
    private var currentIdx = -1
    private val cache = HashMap<Long, Bitmap>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = android.graphics.Color.WHITE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }
    private fun textRect(o: ReviewText): android.graphics.RectF {
        val w = o.widthFrac * width; val h = w * o.bitmap.height / o.bitmap.width.toFloat()
        val cx = width * o.cx; val cy = height * o.cy
        return android.graphics.RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }
    private fun textAt(x: Float, y: Float): ReviewText? {
        for (o in reviewTexts.asReversed()) {
            val r = textRect(o); val rad = Math.toRadians(-o.rotationDeg.toDouble())
            val dx = x - r.centerX(); val dy = y - r.centerY()
            val lx = (dx * Math.cos(rad) - dy * Math.sin(rad)).toFloat() + r.centerX()
            val ly = (dx * Math.sin(rad) + dy * Math.cos(rad)).toFloat() + r.centerY()
            if (android.graphics.RectF(r).apply { inset(-30f, -30f) }.contains(lx, ly)) return o
        }
        return null
    }
    private var lastRect = android.graphics.RectF()

    // Gesten
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var startDist = 0f
    private var startScale = 1f
    private var startAngle = 0f
    private var startRot = 0f
    private var lastRotSnap = false
    private var lastSnapX = false; private var lastSnapY = false
    private var snapUntil = 0L
    private val snapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFD60A.toInt(); strokeWidth = 2.5f }
    private var lastMidX = 0f
    private var lastMidY = 0f

    fun setTime(ms: Long) {
        timeMs = ms
        val idx = captions.indexOfFirst { ms >= it.startMs && ms < it.endMs }
        val c = if (idx >= 0) captions[idx] else null
        val needsRedraw = c !== current || c != null || reviewTexts.isNotEmpty()
        current = c; currentIdx = idx
        if (needsRedraw) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (System.currentTimeMillis() < snapUntil) {
            if (lastSnapX) canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), snapPaint)
            if (lastSnapY) canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, snapPaint)
            postInvalidateDelayed(100)
        }
        // Review-Texte vor den Untertiteln
        reviewTexts.forEach { o ->
            val r = textRect(o)
            canvas.save(); canvas.rotate(o.rotationDeg, r.centerX(), r.centerY())
            canvas.drawBitmap(o.bitmap, null, r, paint)
            if (o === activeText) canvas.drawRoundRect(android.graphics.RectF(r).apply { inset(-6f, -6f) }, 10f, 10f, framePaint)
            canvas.restore()
        }
        val c = current ?: run { lastRect.setEmpty(); return }
        if (width == 0 || height == 0) return
        val key = (currentIdx.toLong() shl 20) or (CaptionStyle.cacheKey(c, timeMs, settings).toLong() and 0xFFFFF)
        val bmp = cache.getOrPut(key) { CaptionStyle.renderBlock(c, timeMs, settings, width, height) }
        var bytes = 0L; cache.values.forEach { bytes += it.byteCount }
        if (bytes > 24L * 1024 * 1024) { cache.clear(); cache[key] = bmp }
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
                textAt(e.x, e.y)?.let { t ->
                    val now = System.currentTimeMillis()
                    if (now - lastTextTapAt < 320 && activeText === t) { lastTextTapAt = 0; onReviewTextEdit?.invoke(t); return true }
                    lastTextTapAt = now
                    activeText = t; reviewTexts.remove(t); reviewTexts.add(t)   // nach vorn
                    rawX = t.cx; rawY = t.cy
                    dragging = true; lastX = e.x; lastY = e.y; invalidate()
                    return true
                }
                activeText = null
                rawX = settings.cxFrac; rawY = settings.cyFrac
                if (current == null || lastRect.isEmpty) { invalidate(); return false }
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
                if (dragging && e.pointerCount == 2 && activeText != null) {
                    startDist = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                    textStartWidth = activeText!!.widthFrac; textStartRot = activeText!!.rotationDeg
                    startAngle = Math.toDegrees(Math.atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
                    lastMidX = (e.getX(0) + e.getX(1)) / 2f; lastMidY = (e.getY(0) + e.getY(1)) / 2f
                    return true
                }
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
                activeText?.let { t ->
                    if (e.pointerCount >= 2 && startDist > 0) {
                        val d = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                        t.widthFrac = (textStartWidth * d / startDist).coerceIn(0.1f, 1.5f)
                        val raw = textStartRot + Math.toDegrees(Math.atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat() - startAngle
                        val n = Math.round(raw / 90f) * 90f
                        t.rotationDeg = if (kotlin.math.abs(raw - n) < 4f) n else raw
                        val mx = (e.getX(0) + e.getX(1)) / 2f; val my = (e.getY(0) + e.getY(1)) / 2f
                        rawX = (rawX + (mx - lastMidX) / width).coerceIn(0f, 1f); rawY = (rawY + (my - lastMidY) / height).coerceIn(0f, 1f)
                        lastMidX = mx; lastMidY = my
                    } else {
                        rawX = (rawX + (e.x - lastX) / width).coerceIn(0f, 1f); rawY = (rawY + (e.y - lastY) / height).coerceIn(0f, 1f)
                        lastX = e.x; lastY = e.y
                    }
                    t.cx = if (kotlin.math.abs(rawX - 0.5f) < 0.018f) 0.5f else rawX
                    t.cy = if (kotlin.math.abs(rawY - 0.5f) < 0.018f) 0.5f else rawY
                    invalidate(); return true
                }
                if (e.pointerCount >= 2 && startDist > 0) {
                    val d = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                    settings.scale = (startScale * d / startDist).coerceIn(0.5f, 2.2f)
                    val ang = Math.toDegrees(Math.atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
                    val raw = startRot + (ang - startAngle)
                    val n = Math.round(raw / 90f) * 90f
                    val rotSnap = kotlin.math.abs(raw - n) < 4f
                    settings.rotationDeg = if (rotSnap) n else raw
                    if (rotSnap && !lastRotSnap) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    lastRotSnap = rotSnap
                    val mx = (e.getX(0) + e.getX(1)) / 2f; val my = (e.getY(0) + e.getY(1)) / 2f
                    rawX = (rawX + (mx - lastMidX) / width).coerceIn(0.05f, 0.95f)
                    rawY = (rawY + (my - lastMidY) / height).coerceIn(0.05f, 0.95f)
                    lastMidX = mx; lastMidY = my
                    cache.clear()
                } else {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    rawX = (rawX + dx / width).coerceIn(0.05f, 0.95f)
                    rawY = (rawY + dy / height).coerceIn(0.05f, 0.95f)
                    lastX = e.x; lastY = e.y
                }
                // Mitte einrasten (horizontal und vertikal) – nur die sichtbare Position
                val hx = kotlin.math.abs(rawX - 0.5f) < 0.018f
                val hy = kotlin.math.abs(rawY - 0.5f) < 0.018f
                settings.cxFrac = if (hx) 0.5f else rawX
                settings.cyFrac = if (hy) 0.5f else rawY
                if ((hx || hy) && !(lastSnapX || lastSnapY)) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                lastSnapX = hx; lastSnapY = hy
                snapUntil = if (hx || hy) System.currentTimeMillis() + 400 else 0
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(remaining); lastY = e.getY(remaining); startDist = 0f
                return dragging
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    if (activeText != null) onReviewTextChanged?.invoke() else onSettingsChanged?.invoke()
                    invalidate(); return true
                }
                return false
            }
        }
        return super.onTouchEvent(e)
    }
}
