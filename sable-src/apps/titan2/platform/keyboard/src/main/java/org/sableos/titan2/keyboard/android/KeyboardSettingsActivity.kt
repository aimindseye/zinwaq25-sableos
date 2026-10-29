package org.sableos.titan2.keyboard.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.sableos.titan2.keyboard.core.ImeAction
import org.sableos.titan2.keyboard.core.ImeLaunchReport
import org.sableos.titan2.keyboard.core.ImeStatusModel
import org.sableos.titan2.keyboard.core.KeyboardText.t

/** Plain-View settings so it works with the hardware keyboard (Tab/arrows/Enter) with no extra dependencies. */
class KeyboardSettingsActivity : Activity() {
    private companion object {
        const val PAD_PX = 24
        const val TITLE_SP = 20f
        const val NOTE_SP = 12f
    }

    private var refreshStatus: () -> Unit = {}

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun addPreferenceSwitches(col: LinearLayout, prefs: KeyboardPrefs) {
        fun sw(label: String, key: String, def: Boolean) = col.addView(
            Switch(this).apply {
                text = label
                isChecked = prefs.getBool(key, def)
                setOnCheckedChangeListener { _, v -> prefs.setBool(key, v) }
            }
        )
        sw(t("auto_capitalise_sentences"), "autoCap", true)
        sw(t("double_space_inserts_period"), "doubleSpace", true)
        sw(t("autotext_dont_becomes_don_t"), AUTO_TEXT, true)
        sw(t("long_press_a_letter_for"), "longPressAlt", true)
        sw(t("letters_type_digits_in_numeric"), "numericAutoAlt", true)
        sw(t("nav_mode_ijkl_arrows_enabled"), "navMode", true)
        sw(t("alt_space_toggles_nav_mode"), "altSpaceNav", true)
        sw(t("show_on_screen_keyboard_for"), "softForNumeric", true)
        sw(t("always_show_on_screen_keyboard"), "forceSoft", false)
        sw(
            t("compact_status_strip_when_the"),
            "compactStrip",
            KeyboardPrefs.compactStripDefault()
        )
        sw(t("enter_sends_in_apps_that"), ENTER_SENDS, false)
        addClipboardSwitch(col, prefs)
    }

    /** Off by default. Turning it off forgets every entry; the button forgets them now. */
    private fun addClipboardSwitch(col: LinearLayout, prefs: KeyboardPrefs) {
        col.addView(
            Switch(this).apply {
                text = t("clipboard_history_alt_shift_v")
                isChecked = prefs.getBool(CLIP_HISTORY, false)
                setOnCheckedChangeListener { _, v ->
                    prefs.setBool(CLIP_HISTORY, v)
                    if (!v) ClipboardWatcher.history.clear()
                }
            }
        )
        col.addView(
            Button(this).apply {
                text = t("clear_clipboard_history")
                setOnClickListener {
                    ClipboardWatcher.history.clear()
                    Toast.makeText(
                        context,
                        t("clipboard_history_cleared"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    /** Buttons that open the keyboard's other screens. */
    private fun addScreenButtons(col: LinearLayout) {
        fun open(label: String, screen: Class<out Activity>) = col.addView(
            Button(this).apply {
                text = label
                setOnClickListener { startActivity(Intent(this@KeyboardSettingsActivity, screen)) }
            }
        )
        open(t("autotext_entries"), AutoTextActivity::class.java)
        open(t("key_event_probe_diagnostic"), KeyProbeActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = KeyboardPrefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PAD_PX, PAD_PX, PAD_PX, PAD_PX)
        }
        col.addView(
            TextView(this).apply {
                text = t("sable_keyboard")
                textSize = TITLE_SP
            }
        )
        col.addView(
            TextView(this).apply {
                text =
                    t("layout_provisional_until_key_legends")
                textSize = NOTE_SP
            }
        )
        addPreferenceSwitches(col, prefs)
        val status = TextView(this).apply { textSize = NOTE_SP }
        val failure = TextView(this).apply { textSize = NOTE_SP }
        col.addView(status)
        col.addView(failure)
        val launcher = ImeLauncher(this)
        fun refresh() {
            status.text = ImeStatusModel.describe(launcher.status())
        }
        ImeAction.values().forEach { action ->
            col.addView(
                Button(this).apply {
                    text = action.label
                    setOnClickListener {
                        failure.text =
                            ImeLaunchReport.message(action, launcher.run(action)).orEmpty()
                    }
                }
            )
        }
        addScreenButtons(col)
        refresh()
        refreshStatus = ::refresh
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
