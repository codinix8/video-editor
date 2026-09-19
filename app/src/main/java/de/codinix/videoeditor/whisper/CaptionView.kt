package de.codinix.videoeditor.whisper

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Zeigt in der Review den zum Abspielstand passenden Untertitel über dem Video.
 * Der sichtbare Frame füllt die View (fillCenter wie beim Player, resize_mode zoom).
 */
class CaptionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var captions: List<Caption> = emptyList()
        set(v) { field = v; invalidate() }
    private var timeMs = 0L
    private var current: Caption? = null

    fun setTime(ms: Long) {
        timeMs = ms
        val c = captions.firstOrNull { ms >= it.startMs && ms < it.endMs }
        if (c !== current) { current = c; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        val c = current ?: return
        CaptionStyle.draw(canvas, c.text, width, height)
    }
}
