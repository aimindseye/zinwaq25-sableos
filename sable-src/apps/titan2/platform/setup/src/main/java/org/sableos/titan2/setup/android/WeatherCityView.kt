package org.sableos.titan2.setup.android

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.titan2.setup.core.WeatherCityStep

/**
 * The Weather city step: a filter field over Sable Weather's bundled catalog, up to
 * [WeatherCityStep.MAX_SHOWN] matches and Skip. Keyboard first: typing filters, Down moves into the
 * matches, Up/Down move between them, Enter chooses (Enter in the field takes the first match), and
 * typing on a match goes back to the field. [finish] gets the line to show on the checklist, or null
 * for Skip, which leaves Weather's city as it is.
 */
class WeatherCityView(
    private val activity: Activity,
    private val client: WeatherCityClient,
    private val finish: (String?) -> Unit
) {
    private val dp = activity.resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    private val current = label(SetupStyle.DETAIL_SP, Color.WHITE, false)
    private val status = label(SetupStyle.DETAIL_SP, SetupStyle.WARN, true)
    private val field = EditText(activity)
    private val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val skip = row("") { finish(null) }
    private var names: List<String> = emptyList()

    fun build(): View {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(label(SetupStyle.ROW_TITLE_SP, Color.WHITE, true, WeatherCityStep.TITLE))
        root.addView(current)
        root.addView(label(SetupStyle.DETAIL_SP, SetupStyle.DIM, false, WeatherCityStep.HELP))
        setUpField()
        root.addView(field)
        root.addView(status)
        root.addView(results)
        root.addView(skip)
        refresh("")
        field.requestFocus()
        return root
    }

    private fun setUpField() = field.apply {
        hint = WeatherCityStep.HINT
        setSingleLine()
        textSize = SetupStyle.ROW_TITLE_SP
        setTextColor(Color.WHITE)
        setHintTextColor(SetupStyle.DIM)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

                override fun afterTextChanged(s: Editable?) = refresh(s?.toString().orEmpty())
            }
        )
        setOnEditorActionListener { _, _, _ ->
            chooseFirst()
            true
        }
        setOnKeyListener { _, code, event -> fieldKey(code, event) }
    }

    /** Enter chooses the first match; Down leaves the field for the matches (or Skip). */
    private fun fieldKey(code: Int, event: KeyEvent): Boolean = when (code) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
            if (event.action == KeyEvent.ACTION_UP) chooseFirst()
            true
        }

        KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (event.action == KeyEvent.ACTION_DOWN) {
                (results.getChildAt(0) ?: skip).requestFocus()
            }
            true
        }

        else -> false
    }

    /** On a match, a printable key or Backspace goes back to the field so typing never gets lost. */
    private fun matchKey(code: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val typed = event.unicodeChar.takeIf { event.isPrintingKey && it != 0 }
        return when {
            typed != null -> {
                field.requestFocus()
                field.text.append(typed.toChar())
                field.setSelection(field.length())
                true
            }

            code == KeyEvent.KEYCODE_DEL -> {
                field.requestFocus()
                field.text.let { if (it.isNotEmpty()) it.delete(it.length - 1, it.length) }
                true
            }

            else -> false
        }
    }

    private fun refresh(query: String) {
        val found = client.search(query)
        names = found?.names.orEmpty()
        current.text = WeatherCityStep.currentLine(found?.selected)
        skip.text = WeatherCityStep.skipLabel(found?.selected)
        val problem = when {
            found == null -> WeatherCityStep.UNREACHABLE

            names.isEmpty() && WeatherCityStep.cleanQuery(query).isNotEmpty() ->
                WeatherCityStep.NO_MATCH

            else -> null
        }
        show(problem)
        results.removeAllViews()
        names.forEach { name ->
            results.addView(
                row(name) { choose(name) }.apply { setOnKeyListener { _, c, e -> matchKey(c, e) } }
            )
        }
    }

    private fun chooseFirst() {
        names.firstOrNull()?.let { choose(it) }
    }

    private fun choose(name: String) {
        val answer = client.select(name)
        val message = WeatherCityStep.resultMessage(answer, name)
        if (WeatherCityStep.succeeded(answer)) finish(message) else show(message)
    }

    private fun show(message: String?) {
        status.text = message.orEmpty()
        status.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun row(text: String, onClick: () -> Unit) = Button(activity).apply {
        this.text = text
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = 0
        minimumHeight = 0
        setPadding(px(ROW_PAD), px(ROW_PAD), px(ROW_PAD), px(ROW_PAD))
        textSize = SetupStyle.SUMMARY_SP
        setTextColor(Color.WHITE)
        setBackgroundColor(SetupStyle.KEY)
        // A visible focus mark: the arrow keys are the main way through this list.
        setOnFocusChangeListener { v, focused ->
            v.setBackgroundColor(if (focused) SetupStyle.FOCUS else SetupStyle.KEY)
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(ROW_GAP) }
    }

    private fun label(sp: Float, color: Int, bold: Boolean, s: String = "") =
        TextView(activity).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private companion object {
        const val ROW_PAD = 6
        const val ROW_GAP = 2
    }
}
