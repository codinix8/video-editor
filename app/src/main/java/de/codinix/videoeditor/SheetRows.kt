package de.codinix.videoeditor

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * Baukasten für Einstellungs-Fenster im TikTok-Stil: Abschnittstitel außerhalb, pro Abschnitt eine
 * Karte, darin Zeilen (Bezeichnung links, Wert/Schalter rechts) mit feinen Trennlinien.
 */
class Sections(private val ctx: Context) {
    private val dp = ctx.resources.displayMetrics.density
    val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private var currentCard: LinearLayout? = null

    fun section(title: CharSequence?): Sections {
        if (title != null) root.addView(TextView(ctx).apply {
            text = title; textSize = 13f; setTextColor(0xFF9A9BA6.toInt())
            setPadding((4 * dp).toInt(), (if (root.childCount == 0) 4 else 18) * dp.toInt(), 0, (6 * dp).toInt())
        })
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Sheet.CARD); cornerRadius = 16 * dp }
            setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
        }
        root.addView(card); currentCard = card
        return this
    }

    private fun addRow(v: View) {
        val card = currentCard ?: section(null).let { currentCard!! }
        if (card.childCount > 0) card.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1); setBackgroundColor(0x1AFFFFFF)
        })
        card.addView(v)
    }

    private fun rowBase(label: CharSequence, sub: CharSequence? = null): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = (54 * dp).toInt()
        setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply { text = label; textSize = 16f; setTextColor(Color.WHITE) })
            if (sub != null) addView(TextView(ctx).apply { text = sub; textSize = 12.5f; setTextColor(0xFF9A9BA6.toInt()); setPadding(0, (2 * dp).toInt(), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** Zeile mit Schalter. */
    fun switch(label: CharSequence, checked: Boolean, sub: CharSequence? = null, locked: Boolean = false, onLocked: (() -> Unit)? = null, onChange: (Boolean) -> Unit): MaterialSwitch {
        if (locked) {
            val row = rowBase(label, sub)
            row.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_crown); setPadding(0, 0, (10 * dp).toInt(), 0) })
            val sw = MaterialSwitch(ctx).apply { isChecked = false; isEnabled = false }
            row.addView(sw); row.alpha = 0.5f
            row.setOnClickListener { onLocked?.invoke() }
            addRow(row); return sw
        }
        val sw = MaterialSwitch(ctx).apply {
            isChecked = checked
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Sheet.ACCENT, 0xFF4A4B55.toInt()))
            trackDecorationTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        val row = rowBase(label, sub); row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        addRow(row); return sw
    }

    /** Zeile mit Auswahlwert rechts; Antippen öffnet ein Untermenü mit Auswahlpunkten. */
    fun choice(label: CharSequence, options: List<String>, selected: Int, sub: CharSequence? = null,
               locked: Set<Int> = emptySet(), onLocked: (() -> Unit)? = null, onPick: (Int) -> Unit): TextView {
        val value = TextView(ctx).apply { text = options.getOrNull(selected) ?: ""; textSize = 15f; setTextColor(0xFFBDBEC8.toInt()) }
        val chevron = ImageView(ctx).apply { setImageResource(R.drawable.ic_chevron_right); alpha = 0.6f; setPadding((6 * dp).toInt(), 0, 0, 0) }
        val row = rowBase(label, sub); row.addView(value); row.addView(chevron)
        var current = selected
        row.setOnClickListener {
            lateinit var dlg: android.app.Dialog
            dlg = Sheet(ctx).setTitle(label).setView(radioList(ctx, options, current, locked, onLocked) { i ->
                current = i; value.text = options[i]; onPick(i); dlg.dismiss()
            }).show()
        }
        addRow(row); return value
    }

    /** Ganze Zeile gesperrt (Pro): Krone, halbe Deckkraft, Antippen ruft [onLocked]. */
    fun lockedRow(label: CharSequence, sub: CharSequence? = null, onLocked: () -> Unit): View {
        val row = rowBase(label, sub)
        row.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_crown) })
        row.alpha = 0.5f
        row.setOnClickListener { onLocked() }
        addRow(row); return row
    }

    /** Zeile mit Regler (0..max) und Wertanzeige. */
    fun slider(label: CharSequence, value: Int, max: Int = 100, format: (Int) -> String = { "$it %" }, onChange: (Int) -> Unit, onRelease: ((Int) -> Unit)? = null): SeekBar {
        val valueText = TextView(ctx).apply { text = format(value); textSize = 15f; setTextColor(0xFFBDBEC8.toInt()) }
        val head = rowBase(label); head.addView(valueText)
        head.setPadding(0, (8 * dp).toInt(), 0, 0); head.minimumHeight = 0
        val seek = SeekBar(ctx).apply {
            this.max = max; progress = value
            progressTintList = ColorStateList.valueOf(Sheet.ACCENT); thumbTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(0xFF4A4B55.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) { valueText.text = format(v); onChange(v) }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { onRelease?.invoke(sb.progress) }
            })
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(head); addView(seek); setPadding(0, 0, 0, (8 * dp).toInt()) }
        addRow(col); return seek
    }

    /** Zeile als Knopf (Text, optional Untertitel), rechts Pfeil. */
    fun button(label: CharSequence, sub: CharSequence? = null, destructive: Boolean = false, onClick: () -> Unit) {
        val row = rowBase(label, sub)
        (row.getChildAt(0) as LinearLayout).let { (it.getChildAt(0) as TextView).setTextColor(if (destructive) Sheet.ACCENT else Color.WHITE) }
        row.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_chevron_right); alpha = 0.6f })
        row.setOnClickListener { onClick() }
        addRow(row)
    }

    /** Freier Text (Hinweis) als Zeile. */
    fun note(text: CharSequence) {
        addRow(TextView(ctx).apply { this.text = text; textSize = 13f; setTextColor(0xFF9A9BA6.toInt()); setLineSpacing(0f, 1.2f); setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt()) })
    }

    /** Eigene Ansicht als Zeile. */
    fun custom(v: View) { addRow(v) }

    companion object {
        /** [locked]: Einträge mit Krone und halber Deckkraft; Antippen ruft [onLocked] statt [onPick]. */
        fun radioList(ctx: Context, labels: List<String>, selected: Int, locked: Set<Int> = emptySet(), onLocked: (() -> Unit)? = null, onPick: (Int) -> Unit): View {
            val dp = ctx.resources.displayMetrics.density
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            labels.forEachIndexed { i, label ->
                val isLocked = i in locked
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
                    addView(TextView(ctx).apply { text = label; textSize = 16f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    if (isLocked) addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_crown); setPadding(0, 0, (10 * dp).toInt(), 0) })
                    addView(MaterialRadioButton(ctx).apply {
                        isChecked = i == selected; isClickable = false
                        buttonTintList = ColorStateList.valueOf(if (i == selected) Sheet.ACCENT else 0xFF8A8B96.toInt())
                    })
                    if (isLocked) alpha = 0.5f
                    setOnClickListener { if (isLocked) onLocked?.invoke() else onPick(i) }
                }
                col.addView(row)
                if (i < labels.lastIndex) col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1); setBackgroundColor(0x1AFFFFFF) })
            }
            return col
        }
    }
}
