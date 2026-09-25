package de.codinix.videoeditor.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Transparente Ebene über der Kameravorschau. Nimmt Berührungen entgegen:
 *  - Tippen: Overlay auswählen / abwählen
 *  - Ziehen: verschieben
 *  - Zwei Finger: skalieren und drehen
 * Zeichnet um das ausgewählte Overlay einen gestrichelten Rahmen.
 *
 * Die Vorschau füllt den Bildschirm (fillCenter), deshalb wird hier der sichtbare
 * Ausschnitt des Frames berechnet, um Bildschirm- und Frame-Koordinaten umzurechnen.
 */
class OverlayGestureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    lateinit var store: OverlayStore
    /** Aktives Mosaik (oder null). Kacheln werden angefasst, wenn kein Overlay getroffen wurde. */
    var mosaic: Mosaic? = null
    var onMosaicChanged: (() -> Unit)? = null
    var onMosaicTileSelected: ((Int) -> Unit)? = null
    /** Kurzer Tipp ohne Ziehen auf ein Video (Overlay-ID) bzw. eine Kachel (Index) bzw. den Hintergrund. */
    var onVideoTap: ((overlay: VideoOverlay?, tileIndex: Int, background: Boolean) -> Unit)? = null

    // Eingeblendetes Play/Pause-Symbol
    private var iconRect = RectF()
    private var iconPlaying = true
    private var iconShownAt = 0L
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val iconBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }

    /** Symbol in der Mitte von [rect] (Pixel) zeigen; blendet über 3 s aus. */
    fun showPlayIcon(rect: RectF, playing: Boolean) {
        iconRect.set(rect); iconPlaying = playing; iconShownAt = System.currentTimeMillis()
        invalidate()
    }

    /** Pixelrechteck eines Overlays (ohne Drehung) bzw. einer Kachel bzw. des ganzen Frames. */
    fun rectOf(o: Overlay?, tileIndex: Int): RectF {
        computeFrameRect()
        if (o != null) {
            val cx = toPxX(o.cx); val cy = toPxY(o.cy)
            val hw = o.widthFrac * frameRect.width() / 2f; val hh = hw * o.aspect
            return RectF(cx - hw, cy - hh, cx + hw, cy + hh)
        }
        val m = mosaic
        if (tileIndex >= 0 && m != null && m.layout != Mosaic.LAYOUT_NONE) {
            val r = Mosaic.rects(m.layout)[tileIndex]
            return RectF(toPxX(r.left), toPxY(r.top), toPxX(r.right), toPxY(r.bottom))
        }
        return RectF(frameRect)
    }
    private var mosaicTile = -1
    private var mosaicStartZoom = 1f
    private var mosaicStartRot = 0f

    private fun mosaicTileAt(px: Float, py: Float): Int {
        val m = mosaic ?: return -1
        if (m.layout == Mosaic.LAYOUT_NONE) return -1
        val fx = toFrameX(px); val fy = toFrameY(py)
        Mosaic.rects(m.layout).forEachIndexed { i, r -> if (r.contains(fx, fy)) return i }
        return -1
    }
    var onChanged: (() -> Unit)? = null
    var onSelectionChanged: ((Overlay?) -> Unit)? = null

    /** Breite/Höhe des Frames. Wird vom Compositor gemeldet. */
    var frameAspect = 9f / 16f
        set(v) { field = v; invalidate() }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    private val frameRect = RectF()

    // Gestenzustand
    private var active: Overlay? = null
    private var moved = false
    private var lastX = 0f
    private var lastY = 0f
    private var startDist = 0f
    private var startAngle = 0f
    private var startWidth = 0f
    private var startRot = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeFrameRect()
    }

    private fun computeFrameRect() {
        val vw = width.toFloat(); val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return
        val scale = maxOf(vw / frameAspect, vh / 1f)   // fillCenter
        val fw = frameAspect * scale
        val fh = scale
        frameRect.set((vw - fw) / 2f, (vh - fh) / 2f, (vw + fw) / 2f, (vh + fh) / 2f)
    }

    // --------------------------------------------------------------- Umrechnung

    private fun toFrameX(px: Float) = (px - frameRect.left) / frameRect.width()
    private fun toFrameY(py: Float) = (py - frameRect.top) / frameRect.height()
    private fun toPxX(fx: Float) = frameRect.left + fx * frameRect.width()
    private fun toPxY(fy: Float) = frameRect.top + fy * frameRect.height()

    // Einrasten: Rotation auf 90°-Schritte, Position auf die Bildmitte
    private var snapLinesUntil = 0L
    private var snapH = false; private var snapV = false; private var snapRot = false
    private val snapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFD60A.toInt(); strokeWidth = 2.5f }
    private var lastSnapState = 0
    private fun snapRotation(deg: Float): Float {
        val n = Math.round(deg / 90f) * 90f
        return if (kotlin.math.abs(deg - n) < 4f) { snapRot = true; n } else { snapRot = false; deg }
    }
    private fun snapCenter(v: Float, isX: Boolean): Float {
        val hit = kotlin.math.abs(v - 0.5f) < 0.018f
        if (isX) snapH = hit else snapV = hit
        return if (hit) 0.5f else v
    }
    private fun snapFeedback() {
        val state = (if (snapH) 1 else 0) or (if (snapV) 2 else 0) or (if (snapRot) 4 else 0)
        if (state != 0 && state != lastSnapState) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        lastSnapState = state
        if (state != 0) snapLinesUntil = System.currentTimeMillis() + 400
    }
    private fun drawSnapLines(canvas: Canvas) {
        if (System.currentTimeMillis() > snapLinesUntil) return
        if (snapH) canvas.drawLine(toPxX(0.5f), frameRect.top, toPxX(0.5f), frameRect.bottom, snapPaint)
        if (snapV) canvas.drawLine(frameRect.left, toPxY(0.5f), frameRect.right, toPxY(0.5f), snapPaint)
        postInvalidateDelayed(100)
    }

    private fun hitTest(px: Float, py: Float): Overlay? {
        // Oberstes zuerst
        for (o in store.items.asReversed()) {
            if (o is VideoOverlay && o.isBackground) continue
            val cx = toPxX(o.cx); val cy = toPxY(o.cy)
            val halfW = o.widthFrac * frameRect.width() / 2f
            val halfH = halfW * o.aspect
            val rad = Math.toRadians(-o.rotationDeg.toDouble())
            val dx = px - cx; val dy = py - cy
            val lx = dx * cos(rad) - dy * sin(rad)
            val ly = dx * sin(rad) + dy * cos(rad)
            // Etwas Toleranz, damit kleine Overlays greifbar bleiben
            val tol = 24f
            if (abs(lx) <= halfW + tol && abs(ly) <= halfH + tol) return o
        }
        return null
    }

    // --------------------------------------------------------------- Touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        computeFrameRect()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moved = false
                lastX = e.x; lastY = e.y
                active = hitTest(e.x, e.y)
                active?.let { store.bringToFront(it.id) }
                mosaicTile = if (active == null) mosaicTileAt(e.x, e.y) else -1
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2 && active == null && mosaicTile >= 0) {
                    startDist = dist(e); startAngle = angle(e)
                    mosaicStartZoom = mosaic?.tiles?.getOrNull(mosaicTile)?.zoom ?: 1f
                    mosaicStartRot = mosaic?.tiles?.getOrNull(mosaicTile)?.rotationDeg ?: 0f
                    return true
                }
                if (e.pointerCount == 2) {
                    val o = active ?: hitTest(midX(e), midY(e)) ?: return true
                    active = o
                    startDist = dist(e); startAngle = angle(e)
                    startWidth = o.widthFrac; startRot = o.rotationDeg
                    lastMidX = midX(e); lastMidY = midY(e)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (active == null && mosaicTile >= 0) {
                    val m = mosaic ?: return true
                    val t = m.tiles.getOrNull(mosaicTile) ?: return true
                    val r = Mosaic.rects(m.layout)[mosaicTile]
                    if (e.pointerCount >= 2 && startDist > 0) {
                        t.zoom = (mosaicStartZoom * dist(e) / startDist).coerceIn(0.3f, 4f)
                        t.rotationDeg = snapRotation(mosaicStartRot + (angle(e) - startAngle))
                        snapH = false; snapV = false; snapFeedback()
                    } else {
                        val dx = e.x - lastX; val dy = e.y - lastY
                        if (kotlin.math.abs(dx) > 2 || kotlin.math.abs(dy) > 2) moved = true
                        // Inhalt folgt dem Finger: Verschiebung relativ zur Kachelgröße
                        t.offX = (t.offX - dx / (r.width() * frameRect.width()) * 2f).coerceIn(-1f, 1f)
                        t.offY = (t.offY - dy / (r.height() * frameRect.height()) * 2f).coerceIn(-1f, 1f)
                        lastX = e.x; lastY = e.y
                    }
                    onMosaicChanged?.invoke()
                    invalidate()
                    return true
                }
                val o = active ?: return true
                if (e.pointerCount >= 2) {
                    val d = dist(e)
                    if (startDist > 0) {
                        o.widthFrac = (startWidth * d / startDist).coerceIn(0.05f, 3f)
                    }
                    o.rotationDeg = snapRotation(startRot + (angle(e) - startAngle))
                    val mx = midX(e); val my = midY(e)
                    o.cx += (mx - lastMidX) / frameRect.width()
                    o.cy += (my - lastMidY) / frameRect.height()
                    lastMidX = mx; lastMidY = my
                } else {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    if (abs(dx) > 2 || abs(dy) > 2) moved = true
                    o.cx += dx / frameRect.width()
                    o.cy += dy / frameRect.height()
                    lastX = e.x; lastY = e.y
                }
                o.cx = snapCenter(o.cx.coerceIn(-0.5f, 1.5f), true); o.cy = snapCenter(o.cy.coerceIn(-0.5f, 1.5f), false)
                snapFeedback()
                store.publish()
                onChanged?.invoke()
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Zurück auf Ein-Finger-Ziehen mit dem verbleibenden Finger
                val remaining = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(remaining); lastY = e.getY(remaining)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val newSel = active?.id
                mosaic?.let { m ->
                    // Exklusiv: Kachel oder Overlay, nie beides
                    val tile = if (active == null) mosaicTile else -1
                    if (m.selected != tile) { m.selected = tile; onMosaicTileSelected?.invoke(tile) }
                }
                // Kurzer Tipp ohne Ziehen: Play/Pause des getroffenen Videos
                if (!moved && e.actionMasked == MotionEvent.ACTION_UP && e.pointerCount == 1) {
                    val vo = active as? VideoOverlay
                    val tileHasVideo = mosaicTile >= 0 && mosaic?.tiles?.getOrNull(mosaicTile)?.kind == Mosaic.KIND_VIDEO
                    val bg = active == null && mosaicTile < 0 && store.videoOverlay()?.isBackground == true
                    if (vo != null || tileHasVideo || bg) onVideoTap?.invoke(vo, if (tileHasVideo) mosaicTile else -1, bg)
                }
                if (store.selectedId != newSel) {
                    store.selectedId = newSel
                    onSelectionChanged?.invoke(active)
                }
                active = null; mosaicTile = -1
                lastSnapState = 0; snapH = false; snapV = false; snapRot = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun drawPlayIcon(canvas: Canvas) {
        if (iconShownAt == 0L) return
        val age = System.currentTimeMillis() - iconShownAt
        if (age > 3000) { iconShownAt = 0L; return }
        val alpha = if (age < 2200) 1f else 1f - (age - 2200) / 800f
        val cx = iconRect.centerX(); val cy = iconRect.centerY()
        val r = (minOf(iconRect.width(), iconRect.height()) * 0.16f).coerceIn(28f, 72f)
        iconBg.alpha = (0x55 * alpha).toInt(); iconPaint.alpha = (230 * alpha).toInt()
        canvas.drawCircle(cx, cy, r, iconBg)
        if (iconPlaying) {
            // Dreieck
            val p = android.graphics.Path()
            p.moveTo(cx - r * 0.35f, cy - r * 0.5f); p.lineTo(cx + r * 0.55f, cy); p.lineTo(cx - r * 0.35f, cy + r * 0.5f); p.close()
            canvas.drawPath(p, iconPaint)
        } else {
            canvas.drawRoundRect(cx - r * 0.45f, cy - r * 0.5f, cx - r * 0.12f, cy + r * 0.5f, 4f, 4f, iconPaint)
            canvas.drawRoundRect(cx + r * 0.12f, cy - r * 0.5f, cx + r * 0.45f, cy + r * 0.5f, 4f, 4f, iconPaint)
        }
        postInvalidateDelayed(40)
    }

    private fun dist(e: MotionEvent) = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
    private fun angle(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
    private fun midX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2f
    private fun midY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2f

    // --------------------------------------------------------------- Zeichnen

    /** Steht die Aufnahme? Dann kleine Pause-Marken in Videokacheln und Video-Overlays. */
    var showIdleMarkers = false
        set(v) { field = v; invalidate() }

    private fun drawIdleMarkers(canvas: Canvas) {
        if (!showIdleMarkers) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt() }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
        fun mark(r: RectF) {
            val s = 10f * resources.displayMetrics.density
            val cx = r.right - s * 1.8f; val cy = r.top + s * 1.8f
            canvas.drawCircle(cx, cy, s * 1.3f, bg)
            canvas.drawRoundRect(cx - s * 0.45f, cy - s * 0.5f, cx - s * 0.12f, cy + s * 0.5f, 2f, 2f, p)
            canvas.drawRoundRect(cx + s * 0.12f, cy - s * 0.5f, cx + s * 0.45f, cy + s * 0.5f, 2f, 2f, p)
        }
        mosaic?.let { m -> if (m.layout != Mosaic.LAYOUT_NONE) Mosaic.rects(m.layout).forEachIndexed { i, r ->
            if (m.tiles.getOrNull(i)?.kind == Mosaic.KIND_VIDEO) mark(RectF(toPxX(r.left), toPxY(r.top), toPxX(r.right), toPxY(r.bottom)))
        } }
        store.items.filterIsInstance<VideoOverlay>().forEach { o -> if (!o.isBackground) mark(rectOf(o, -1)) }
    }

    override fun onDraw(canvas: Canvas) {
        computeFrameRect()
        drawSnapLines(canvas)
        drawIdleMarkers(canvas)
        drawPlayIcon(canvas)
        mosaic?.let { m ->
            if (m.selected >= 0 && m.layout != Mosaic.LAYOUT_NONE) {
                val r = Mosaic.rects(m.layout)[m.selected]
                canvas.drawRoundRect(toPxX(r.left) + 6, toPxY(r.top) + 6, toPxX(r.right) - 6, toPxY(r.bottom) - 6, 10f, 10f, framePaint)
            }
        }
        val o = store.selected() ?: return
        val cx = toPxX(o.cx); val cy = toPxY(o.cy)
        val halfW = o.widthFrac * frameRect.width() / 2f
        val halfH = halfW * o.aspect
        canvas.save()
        canvas.rotate(o.rotationDeg, cx, cy)
        canvas.drawRoundRect(cx - halfW - 6, cy - halfH - 6, cx + halfW + 6, cy + halfH + 6, 10f, 10f, framePaint)
        canvas.restore()
    }
}
