package de.codinix.videoeditor

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Einheitliches Fenster von unten (TikTok-artig): Titel mit X, Inhalt in einer abgerundeten Karte,
 * Aktionen als Reihe unten. API bewusst an AlertDialog.Builder angelehnt, damit bestehende Dialoge
 * mit minimalen Änderungen umgestellt werden können.
 */
class Sheet(private val ctx: Context) {
    private val dp = ctx.resources.displayMetrics.density
    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private var content: View? = null
    private var positive: Pair<CharSequence, ((DialogInterface, Int) -> Unit)?>? = null
    private var negative: Pair<CharSequence, ((DialogInterface, Int) -> Unit)?>? = null
    private var neutral: Pair<CharSequence, ((DialogInterface, Int) -> Unit)?>? = null
    private var onCancel: ((DialogInterface) -> Unit)? = null
    private var onDismiss: ((DialogInterface) -> Unit)? = null
    private var cancelable = true
    private var tall = false
    private var plain = false

    /** Inhalt bringt eigene Karten mit (Sections) – keine umschließende Karte. */
    fun setSections(sec: Sections) = apply { content = sec.root; plain = true }

    private var selfScrolling = true

    /** Feste Höhe (~80 % des Bildschirms). [selfScrolling]: Inhalt scrollt selbst (Listen), sonst wird er eingebettet. */
    fun setTall(t: Boolean, selfScrolling: Boolean = true) = apply { tall = t; this.selfScrolling = selfScrolling }

    fun setTitle(res: Int) = apply { title = ctx.getString(res) }
    fun setTitle(t: CharSequence) = apply { title = t }
    fun setMessage(res: Int) = apply { message = ctx.getString(res) }
    fun setMessage(m: CharSequence) = apply { message = m }
    fun setView(v: View) = apply { content = v }
    fun setCancelable(c: Boolean) = apply { cancelable = c }
    fun setPositiveButton(res: Int, l: ((DialogInterface, Int) -> Unit)?) = apply { positive = ctx.getString(res) to l }
    fun setPositiveButton(t: CharSequence, l: ((DialogInterface, Int) -> Unit)?) = apply { positive = t to l }
    fun setNegativeButton(res: Int, l: ((DialogInterface, Int) -> Unit)?) = apply { negative = ctx.getString(res) to l }
    fun setNegativeButton(t: CharSequence, l: ((DialogInterface, Int) -> Unit)?) = apply { negative = t to l }
    fun setNeutralButton(res: Int, l: ((DialogInterface, Int) -> Unit)?) = apply { neutral = ctx.getString(res) to l }
    fun setNeutralButton(t: CharSequence, l: ((DialogInterface, Int) -> Unit)?) = apply { neutral = t to l }
    fun setOnCancelListener(l: (DialogInterface) -> Unit) = apply { onCancel = l }
    fun setOnDismissListener(l: (DialogInterface) -> Unit) = apply { onDismiss = l }

    fun create(): BottomSheetDialog {
        val dlg = BottomSheetDialog(ctx)
        val pad = (16 * dp).toInt()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad)
            background = GradientDrawable().apply {
                setColor(BG); cornerRadii = floatArrayOf(24 * dp, 24 * dp, 24 * dp, 24 * dp, 0f, 0f, 0f, 0f)
            }
        }
        // Griff
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (4 * dp).toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = (10 * dp).toInt() }
            background = GradientDrawable().apply { setColor(0x55FFFFFF); cornerRadius = 2 * dp }
        })
        // Titelzeile mit X
        val head = FrameLayout(ctx)
        head.addView(TextView(ctx).apply {
            text = title ?: ""; textSize = 18f; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding((40 * dp).toInt(), (6 * dp).toInt(), (40 * dp).toInt(), (6 * dp).toInt())
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        head.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_close); background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x22FFFFFF) }
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { dlg.cancel() }
        }, FrameLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt(), Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(head)

        // Inhalt: Nachricht und/oder eigene Ansicht in Karte
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            if (!plain) {
                background = GradientDrawable().apply { setColor(CARD); cornerRadius = 16 * dp }
                setPadding(pad, pad / 2, pad, pad / 2)
            }
        }
        message?.let { card.addView(TextView(ctx).apply { text = it; textSize = 15f; setTextColor(0xFFDDDDE2.toInt()); setPadding(0, pad / 2, 0, pad / 2); setTextIsSelectable(true) }) }
        content?.let { v0 ->
            // Doppelte Scroll-Container auflösen: liefert der Dialog schon einen ScrollView, dessen Inhalt nehmen
            var v = v0
            if (!tall && v is ScrollView && v.childCount == 1) { val inner = v.getChildAt(0); v.removeView(inner); v = inner }
            (v.parent as? android.view.ViewGroup)?.removeView(v)
            card.addView(v, if (tall) LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) else
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        if (tall && selfScrolling) {
            root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = (8 * dp).toInt() })
            val screenH = ctx.resources.displayMetrics.heightPixels
            root.layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (screenH * 0.82f).toInt())
        } else {
            val scroll = androidx.core.widget.NestedScrollView(ctx).apply {
                isVerticalScrollBarEnabled = true; scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                isNestedScrollingEnabled = true
                addView(card)
            }
            root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = (8 * dp).toInt() })
        }

        // Aktionen
        if (positive != null || negative != null || neutral != null) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, pad, 0, 0) }
            fun btn(label: CharSequence, primary: Boolean, l: ((DialogInterface, Int) -> Unit)?, which: Int): View =
                TextView(ctx).apply {
                    text = label; textSize = 15f; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(if (primary) Color.WHITE else 0xFFDDDDE2.toInt())
                    setPadding(pad, (13 * dp).toInt(), pad, (13 * dp).toInt())
                    background = GradientDrawable().apply { setColor(if (primary) ACCENT else CARD); cornerRadius = 12 * dp }
                    setOnClickListener { l?.invoke(dlg, which); dlg.dismiss() }
                }
            neutral?.let { (t, l) -> row.addView(btn(t, false, l, DialogInterface.BUTTON_NEUTRAL), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = pad / 2 }) }
            negative?.let { (t, l) -> row.addView(btn(t, false, { d, w -> l?.invoke(d, w) }, DialogInterface.BUTTON_NEGATIVE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = if (positive != null) pad / 2 else 0 }) }
            positive?.let { (t, l) -> row.addView(btn(t, true, l, DialogInterface.BUTTON_POSITIVE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
            root.addView(row)
        }
        dlg.setContentView(root)
        if (tall) {
            val screenH = ctx.resources.displayMetrics.heightPixels
            (root.parent as? View)?.layoutParams?.height = (screenH * 0.82f).toInt()
            dlg.behavior.peekHeight = (screenH * 0.82f).toInt()
        }
        dlg.setCancelable(cancelable)
        dlg.setOnCancelListener { onCancel?.invoke(it) }
        dlg.setOnDismissListener { onDismiss?.invoke(it) }
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        (root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dlg.behavior.skipCollapsed = true
        dlg.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        return dlg
    }

    fun show(): BottomSheetDialog = create().also { it.show() }

    companion object {
        const val BG = 0xFF161823.toInt()
        const val CARD = 0xFF262833.toInt()
        const val ACCENT = 0xFFFE2C55.toInt()

        /** Abschnittsüberschrift im Stil der Karten. */
        fun header(ctx: Context, text: CharSequence): TextView = TextView(ctx).apply {
            this.text = text; textSize = 12.5f; setTextColor(0xFF9A9BA6.toInt()); isAllCaps = false
            val dp = ctx.resources.displayMetrics.density
            setPadding(0, (18 * dp).toInt(), 0, (6 * dp).toInt())
        }
    }
}
