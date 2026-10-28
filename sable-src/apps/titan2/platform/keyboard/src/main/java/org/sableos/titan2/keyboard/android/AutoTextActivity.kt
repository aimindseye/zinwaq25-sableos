package org.sableos.titan2.keyboard.android

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.sableos.titan2.keyboard.core.AutoText

/**
 * The user's AutoText list as plain text, one `shortcut=replacement` per line, so it is edited with the hardware
 * keyboard alone. The built-in English list is shown below it, read-only.
 */
class AutoTextActivity : Activity() {
    private companion object {
        const val PAD_PX = 24
        const val TITLE_SP = 20f
        const val NOTE_SP = 12f
        const val MIN_LINES = 6
        const val HELP =
            "One entry per line: shortcut=replacement, for example brb=be right back. " +
                "A shortcut is one word of letters or digits. Your entries work in every " +
                "language and replace a built-in one with the same shortcut. In a replacement, " +
                "%D is the date, %T the time and %V the build number. Backspace straight " +
                "after an expansion puts back what you typed."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = KeyboardPrefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PAD_PX, PAD_PX, PAD_PX, PAD_PX)
        }
        fun note(s: String, size: Float = NOTE_SP) = col.addView(
            TextView(this).apply {
                text = s
                textSize = size
            }
        )
        note("AutoText", TITLE_SP)
        note(HELP)
        // no suggestions: AutoText stays out of its own editor, so "ld=%D" is not expanded while typing it
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = MIN_LINES
            setText(AutoText.format(prefs.autoTextEntries))
        }
        col.addView(edit)
        val status = TextView(this).apply { textSize = NOTE_SP }
        col.addView(
            Button(this).apply {
                text = "Save"
                setOnClickListener {
                    val text = edit.text.toString()
                    val entries = AutoText.parse(text)
                    prefs.autoTextEntries = entries
                    val skipped = text.lines().count { it.isNotBlank() } - entries.size
                    status.text = "Saved ${entries.size} entries" +
                        if (skipped >
                            0
                        ) {
                            "; $skipped lines skipped (not shortcut=replacement)"
                        } else {
                            ""
                        }
                    edit.setText(AutoText.format(entries))
                }
            }
        )
        col.addView(status)
        note("Built in (English typing language)", TITLE_SP)
        note(AutoText.BUILT_IN.entries.joinToString("\n") { "${it.key} → ${it.value}" })
        setContentView(
            ScrollView(this).apply {
                addView(
                    col,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )
    }
}
