package org.sableos.phone.android

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.phone.core.DialKeys
import org.sableos.phone.core.PhoneSearch
import org.sableos.phone.core.PhoneText.t

/**
 * The dial pad: the number, up to [MAX_SUGGESTIONS] matching contacts to call instead, the twelve keys (hold 1
 * for voicemail, hold 0 for +) and Delete and Call. The hardware keys type into it through [type].
 */
@SuppressLint("ViewConstructor")
class DialPadView(context: Context, private val ui: Ui, private val listener: Listener) :
    LinearLayout(context) {
    interface Listener {
        fun onCall(number: String)

        fun onNumberChanged(number: String)

        fun onVoicemail()
    }

    private val display: TextView
    private val suggestions: LinearLayout
    var number: String = ""
        private set

    init {
        orientation = VERTICAL
        setPadding(ui.dp(Ui.PAD), 0, ui.dp(Ui.PAD), 0)
        display = ui.text(NUMBER_SP, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            hint = t("enter_number")
            setHintTextColor(ui.palette.muted)
        }
        addView(display)
        suggestions = LinearLayout(context).apply { orientation = VERTICAL }
        addView(suggestions)
        val grid = GridLayout(context).apply { columnCount = COLUMNS }
        "123456789*0#".forEach { c -> grid.addView(key(c), cell()) }
        addView(grid)
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }
        bottom.addView(
            ui.button(t("delete"), ui.palette.surface, ui.palette.text) { backspace() }.apply {
                setOnLongClickListener {
                    setNumber("")
                    true
                }
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ui.dp(GAP) }
        )
        bottom.addView(
            ui.button(t("call"), Ui.GREEN, Ui.ON_COLOR) { listener.onCall(number) },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f)
        )
        val bottomParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        bottomParams.topMargin = ui.dp(GAP)
        addView(bottom, bottomParams)
    }

    private fun key(c: Char): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        minimumHeight = ui.dp(KEY_HEIGHT)
        background = ui.focusBackground(ui.palette.surface)
        addView(ui.text(KEY_SP, bold = true, s = c.toString()).apply { gravity = Gravity.CENTER })
        val letters = DialKeys.lettersFor(c)
        if (letters.isNotEmpty()) {
            addView(
                ui.text(LETTERS_SP, ui.palette.muted, s = letters).apply {
                    gravity =
                        Gravity.CENTER
                }
            )
        }
        setOnClickListener { type(c) }
        setOnLongClickListener {
            when (c) {
                '1' -> listener.onVoicemail()
                '0' -> type('+')
                else -> return@setOnLongClickListener false
            }
            true
        }
    }

    private fun cell() = gridCell(ui, GAP)

    fun type(c: Char) = setNumber(number + c)

    fun backspace() = setNumber(number.dropLast(1))

    fun setNumber(value: String) {
        number = value.take(MAX_LENGTH)
        display.text = number
        listener.onNumberChanged(number)
    }

    /** The contacts whose numbers contain what is typed, each a row that calls that number. */
    fun showSuggestions(people: List<PhoneSearch.Person>) {
        suggestions.removeAllViews()
        if (number.isEmpty()) return
        people.take(MAX_SUGGESTIONS).forEach { p ->
            val n =
                p.numbers.firstOrNull { PhoneSearch.numberMatches(it, number) } ?: p.numbers.first()
            suggestions.addView(
                ui.text(
                    Ui.SMALL_SP,
                    s = t("suggestion", p.name, CallInfo.formatted(context, n))
                ).apply {
                    isFocusable = true
                    isClickable = true
                    maxLines = 1
                    background = ui.focusBackground()
                    setPadding(ui.dp(Ui.PAD), ui.dp(GAP / 2), ui.dp(Ui.PAD), ui.dp(GAP / 2))
                    setOnClickListener { listener.onCall(n) }
                }
            )
        }
    }

    private companion object {
        const val COLUMNS = 3
        const val GAP = 6
        const val KEY_HEIGHT = 44
        const val NUMBER_SP = 26f
        const val KEY_SP = 20f
        const val LETTERS_SP = 9f
        const val MAX_LENGTH = 64
        const val MAX_SUGGESTIONS = 2
    }
}
