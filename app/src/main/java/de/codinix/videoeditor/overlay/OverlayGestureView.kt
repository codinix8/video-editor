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
                        t.rotationDeg = mosaicStartRot + (angle(e) - startAngle)
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
                    o.rotationDeg = startRot + (angle(e) - startAngle)
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
                o.cx = o.cx.coerceIn(-0.5f, 1.5f); o.cy = o.cy.coerceIn(-0.5f, 1.5f)
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
                if (store.selectedId != newSel) {
                    store.selectedId = newSel
                    onSelectionChanged?.invoke(active)
                }
                active = null; mosaicTile = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun dist(e: MotionEvent) = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
    private fun angle(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
    private fun midX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2f
    private fun midY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2f

    // --------------------------------------------------------------- Zeichnen

    override fun onDraw(canvas: Canvas) {
        computeFrameRect()
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
