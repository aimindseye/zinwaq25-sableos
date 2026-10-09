package org.sableos.titan2.keyboard.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import org.sableos.titan2.keyboard.core.KeyLayout
import org.sableos.titan2.keyboard.core.Language
import org.sableos.titan2.keyboard.core.Languages
import org.sableos.titan2.keyboard.core.SoftKeyboardGeometry
import org.sableos.titan2.keyboard.core.SoftShift

/**
 * On-screen keyboard: letters, an Alt layer (one-shot, shows the Alt legend on every key), a digits/symbols page,
 * space, backspace, enter. Plain Views so it works in any IME window.
 *
 * The system navigation bar on Android 15+/16 draws over the IME window and carries the hide-keyboard chevron and the
 * IME switcher, so this view pads itself by the navigation-bar insets instead of drawing under them. There is no
 * in-view hide key: the system chevron does that.
 */
class SoftKeyboardView(
    context: Context,
    private val layout: KeyLayout,
    private val autoCapQuery: () -> Boolean = { false },
    private val onEvent: (Event) -> Unit
) : LinearLayout(context) {
    sealed interface Event {
        data class Text(val s: String) : Event
        data object Backspace : Event
        data object Enter : Event
        data object Hide : Event
        data object NextLanguage : Event
    }

    private companion object {
        const val PAD_H_DP = 4
        const val PAD_V_DP = 6
        const val NAV_FALLBACK_DP = 48
        const val WIDE_KEY_WEIGHT = 1.5f
        const val SPACE_WEIGHT = 4f
        const val ROW_HEIGHT_DP = SoftKeyboardGeometry.ROW_HEIGHT_DP
        const val DENSE_ROW_SIZE = 11
        const val DENSE_KEY_SP = 18f
        const val KEY_SP = 22f
        const val LONG_LABEL_SP = 17f
        const val STRIP_PAD_DP = 4
        const val STRIP_CORNER_DP = 10
        const val STRIP_ELEVATION_DP = 12
        const val VARIANT_SP = 24f
        const val VARIANT_MIN_W_DP = 44
        const val VARIANT_MIN_H_DP = 48
        const val KEY_CORNER_DP = 8
        const val KEY_MARGIN_V_DP = 3
        const val REPEAT_INTERVAL_MS = 50L
        const val REPEAT_DELAY_MS = 400L
    }

    private var symbols = false
    private val shift = SoftShift()
    private var language: Language = Languages.English
    private val handler = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var alt = false

    private val bg = Color.parseColor("#0C1E2D")
    private val keyBg = Color.parseColor("#1E3A52")
    private val keyOn = Color.parseColor("#2F7FB8")
    private val fn = Color.parseColor("#2A4D6B")
    private val fg = Color.WHITE
    private val dp = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        setBackgroundColor(bg)
        setPadding(px(PAD_H_DP), px(PAD_V_DP), px(PAD_H_DP), px(PAD_V_DP))
        setOnApplyWindowInsetsListener { v, insets ->
            val types =
                WindowInsets.Type.systemBars() or WindowInsets.Type.tappableElement() or
                    WindowInsets.Type.displayCutout()
            val i = insets.getInsets(types)
            // Some IME windows report 0 for the nav bar they still draw over;
            // never go below the system's nav bar height.
            val bottom = maxOf(i.bottom, navBarHeightPx())
            v.setPadding(
                px(PAD_H_DP) + i.left,
                px(PAD_V_DP),
                px(PAD_H_DP) + i.right,
                px(PAD_V_DP) + bottom
            )
            insets
        }
        setPadding(px(PAD_H_DP), px(PAD_V_DP), px(PAD_H_DP), px(PAD_V_DP) + navBarHeightPx())
        build()
    }

    fun setLanguage(l: Language) {
        language = l
        alt = false
        build()
    }

    /** Re-evaluate sentence-start auto-capitalisation (call when a field starts). */
    fun refreshAutoCap() {
        shift.autoCap(autoCapQuery())
        build()
    }

    private fun typed(e: Event) {
        onEvent(e)
        shift.autoCap(autoCapQuery())
        build()
    }

    override fun onDetachedFromWindow() {
        popup?.dismiss()
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    private fun navBarHeightPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else px(NAV_FALLBACK_DP)
    }

    private fun px(v: Int) = (v * dp).toInt()

    private fun build() {
        removeAllViews()
        if (symbols) buildSymbolRows() else buildLetterRows()
        addView(bottomRow(), rowParams())
    }

    private fun buildSymbolRows() {
        row(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), false)
        row(listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "/"), false)
        row(listOf("(", ")", "!", "?", ":", ";", "\"", "'", ",", "."), false)
    }

    private fun buildLetterRows() {
        row(language.rowKeys(0, shift.upper), true)
        row(language.rowKeys(1, shift.upper), true)
        val r = newRow()
        if (language.hasShift) r.addView(shiftKey())
        val third = language.rowKeys(2, shift.upper)
        third.forEach { k -> r.addView(letterKey(k, third.size)) }
        r.addView(backspaceKey())
        addView(r, rowParams())
    }

    private fun shiftKey(): View {
        val sl = shift.state
        return key(
            if (sl == SoftShift.State.Lock) "⇪" else "⇧",
            WIDE_KEY_WEIGHT,
            fn,
            KeyOpts(
                active = sl != SoftShift.State.Off,
                onLong = {
                    shift.longPress()
                    build()
                }
            )
        ) {
            shift.tap(SystemClock.uptimeMillis())
            build()
        }
    }

    private fun backspaceKey(): View =
        key("⌫", WIDE_KEY_WEIGHT, fn, KeyOpts(repeat = true)) { onEvent(Event.Backspace) }

    private fun bottomRow(): LinearLayout {
        val bottom = newRow()
        if (!symbols && language.latin) {
            bottom.addView(
                key("Alt", WIDE_KEY_WEIGHT, fn, KeyOpts(active = alt)) {
                    alt = !alt
                    build()
                }
            )
        }
        bottom.addView(
            key(if (symbols) "abc" else "123", WIDE_KEY_WEIGHT, fn) {
                symbols = !symbols
                alt = false
                build()
            }
        )
        bottom.addView(punctKey(","))
        bottom.addView(
            key(
                spaceLabel(),
                SPACE_WEIGHT,
                keyBg,
                KeyOpts(onLong = {
                    onEvent(Event.NextLanguage)
                })
            ) {
                typed(Event.Text(" "))
            }
        )
        bottom.addView(punctKey("."))
        if (symbols) bottom.addView(backspaceKey())
        bottom.addView(key("↵", WIDE_KEY_WEIGHT, fn) { typed(Event.Enter) })
        return bottom
    }

    private fun spaceLabel(): String {
        if (language.tag == Languages.English.tag) return "space"
        val region = language.tag.substringBefore('_').uppercase()
        val suffix = if (language.layout.isEmpty()) "" else "·ABC"
        return "space · $region$suffix"
    }

    private fun punctKey(p: String): View {
        val extras = language.punct[p] ?: emptyList()
        val showVariants: () -> Unit = { pickVariant(extras) { v -> typed(Event.Text(v)) } }
        val onLong = if (extras.isEmpty()) null else showVariants
        return key(p, 1f, keyBg, KeyOpts(onLong = onLong)) { typed(Event.Text(p)) }
    }

    private fun newRow() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity =
            Gravity.CENTER
    }
    private fun rowParams() = LayoutParams(LayoutParams.MATCH_PARENT, px(ROW_HEIGHT_DP))

    private fun row(keys: List<String>, letters: Boolean) {
        val r = newRow()
        keys.forEach { s -> r.addView(if (letters) letterKey(s, keys.size) else textKey(s)) }
        addView(r, rowParams())
    }

    /** Combining marks (vowel signs, tashkeel) are shown on a dotted circle so they are visible on their own. */
    private fun display(k: String): String = if (k.isNotEmpty() &&
        Character.getType(k[0]).let {
            it == Character.NON_SPACING_MARK.toInt() ||
                it == Character.COMBINING_SPACING_MARK.toInt()
        }
    ) {
        "\u25CC$k"
    } else {
        k
    }

    private fun letterKey(k: String, rowSize: Int): View {
        val sp = if (rowSize >= DENSE_ROW_SIZE) DENSE_KEY_SP else KEY_SP
        val single = k.length == 1
        if (alt && language.latin && single) {
            val c = k[0].lowercaseChar()
            val out = layout.alt[c] ?: k
            return key(out, 1f, keyBg, KeyOpts(hint = k, sp = sp)) {
                alt = false
                typed(Event.Text(out))
            }
        }
        val extras = (
            listOfNotNull(
                if (language.latin &&
                    single
                ) {
                    layout.alt[k[0].lowercaseChar()]
                } else {
                    null
                }
            ) +
                (if (single) language.variants(k[0]) else emptyList()) +
                (language.more[k] ?: emptyList())
            ).distinct()
        val showVariants: () -> Unit = {
            pickVariant(extras) { v ->
                shift.letterTyped()
                typed(Event.Text(v))
            }
        }
        val onLong = if (extras.isEmpty()) null else showVariants
        return key(
            display(k),
            1f,
            keyBg,
            KeyOpts(hint = extras.firstOrNull(), sp = sp, onLong = onLong)
        ) {
            shift.letterTyped()
            typed(Event.Text(k))
        }
    }

    private var popupAnchor: View? = null

    /** One candidate commits straight away; several open a strip above the key. */
    private fun pickVariant(options: List<String>, commit: (String) -> Unit) {
        if (options.size == 1) {
            commit(options[0])
            return
        }
        val anchor = popupAnchor ?: return
        popup?.dismiss()
        val strip = variantStrip()
        val pw = PopupWindow(
            strip,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            elevation = STRIP_ELEVATION_DP * dp
        }
        options.forEach { o -> strip.addView(variantButton(o, pw, commit)) }
        strip.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        popup = pw
        pw.showAsDropDown(anchor, 0, -(anchor.height + strip.measuredHeight + px(STRIP_PAD_DP)))
    }

    private fun variantStrip(): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        setPadding(px(STRIP_PAD_DP), px(STRIP_PAD_DP), px(STRIP_PAD_DP), px(STRIP_PAD_DP))
        background =
            GradientDrawable().apply {
                cornerRadius = STRIP_CORNER_DP * dp
                setColor(Color.parseColor("#0A1824"))
                setStroke(px(1), keyOn)
            }
    }

    private fun variantButton(o: String, pw: PopupWindow, commit: (String) -> Unit): Button =
        Button(context).apply {
            text = display(o)
            isAllCaps = false
            setTextColor(fg)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, VARIANT_SP)
            minWidth = 0
            minimumWidth = px(VARIANT_MIN_W_DP)
            minHeight = 0
            minimumHeight = px(VARIANT_MIN_H_DP)
            background = GradientDrawable().apply {
                cornerRadius = KEY_CORNER_DP * dp
                setColor(fn)
            }
            layoutParams =
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, px(ROW_HEIGHT_DP)).apply {
                    setMargins(px(2), 0, px(2), 0)
                }
            setOnClickListener {
                pw.dismiss()
                commit(o)
            }
        }

    private fun textKey(s: String): View = key(s, 1f, keyBg) { typed(Event.Text(s)) }

    /** Optional per-key behaviour and styling for [key]. */
    private class KeyOpts(
        val active: Boolean = false,
        val hint: String? = null,
        val repeat: Boolean = false,
        val sp: Float? = null,
        val onLong: (() -> Unit)? = null
    )

    private fun key(
        label: String,
        weight: Float,
        color: Int,
        opts: KeyOpts = KeyOpts(),
        click: () -> Unit
    ): View {
        val hintText = opts.hint
        val onLong = opts.onLong
        return Button(context).apply {
            text = label
            isAllCaps = false
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                opts.sp ?: if (label.length > 2) LONG_LABEL_SP else KEY_SP
            )
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            background =
                GradientDrawable().apply {
                    cornerRadius = KEY_CORNER_DP * dp
                    setColor(if (opts.active) keyOn else color)
                }
            stateListAnimator = null
            layoutParams =
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                    setMargins(px(2), px(KEY_MARGIN_V_DP), px(2), px(KEY_MARGIN_V_DP))
                }
            contentDescription = if (hintText != null) "$label, alternate $hintText" else label
            if (opts.repeat) {
                installRepeat(this, click)
            } else {
                setOnClickListener { click() }
                if (onLong != null) {
                    setOnLongClickListener {
                        popupAnchor = it
                        onLong()
                        true
                    }
                }
            }
        }
    }

    /** Press-and-hold repeats (backspace): first fire on touch-down, then 400 ms delay, then every 50 ms. */
    private fun installRepeat(target: View, click: () -> Unit) {
        val tick = object : Runnable {
            override fun run() {
                click()
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        target.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    click()
                    handler.postDelayed(tick, REPEAT_DELAY_MS)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(tick)
                }
            }
            true
        }
    }
}
