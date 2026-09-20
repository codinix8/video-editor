package de.codinix.videoeditor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kleine Gesten-Animation für Tipps: zwei Fingerkuppen, die sich spreizen und drehen
 * (zoomen/drehen), danach eine Fingerkuppe, die zieht (verschieben). Läuft in Schleife.
 */
class GestureHintView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 230 }
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 70; style = Paint.Style.STROKE; strokeWidth = 3f }
    private var t = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = h * 0.14f
        val cx = w / 2f; val cy = h / 2f
        if (t < 0.5f) {
            // Phase 1: spreizen + drehen
            val p = ease(t / 0.5f)
            val spread = h * 0.18f + p * h * 0.32f
            val ang = p * 0.9
            val dx = (cos(ang) * spread).toFloat(); val dy = (sin(ang) * spread).toFloat()
            canvas.drawCircle(cx, cy, spread, trail)
            canvas.drawCircle(cx - dx, cy - dy, r, dot)
            canvas.drawCircle(cx + dx, cy + dy, r, dot)
        } else {
            // Phase 2: ein Finger zieht nach rechts
            val p = ease((t - 0.5f) / 0.5f)
            val x0 = w * 0.3f; val x1 = w * 0.7f
            canvas.drawLine(x0, cy, x1, cy, trail)
            canvas.drawCircle(x0 + (x1 - x0) * p, cy, r, dot)
        }
    }

    private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }
}
