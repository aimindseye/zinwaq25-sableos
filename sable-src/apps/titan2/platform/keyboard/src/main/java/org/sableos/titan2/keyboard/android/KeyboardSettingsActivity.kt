package org.sableos.titan2.keyboard.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Plain-View settings so it works with the hardware keyboard (Tab/arrows/Enter) with no extra dependencies. */
class KeyboardSettingsActivity : Activity() {
    private companion object {
        const val PAD_PX = 24
        const val TITLE_SP = 20f
        const val NOTE_SP = 12f
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
                text = "Sable Keyboard"
                textSize = TITLE_SP
            }
        )
        col.addView(
            TextView(this).apply {
                text =
                    "Layout: provisional until key legends are captured on this device."
                textSize = NOTE_SP
            }
        )
        fun sw(label: String, key: String, def: Boolean) = col.addView(
            Switch(this).apply {
                text = label
                isChecked = prefs.getBool(key, def)
                setOnCheckedChangeListener { _, v -> prefs.setBool(key, v) }
            }
        )
        sw("Auto-capitalise sentences", "autoCap", true)
        sw("Double space inserts period", "doubleSpace", true)
        sw("Long-press a letter for its Alt character", "longPressAlt", true)
        sw("Letters type digits in numeric fields", "numericAutoAlt", true)
        sw("Nav mode (IJKL arrows) enabled", "navMode", true)
        sw("Alt+Space toggles Nav mode", "altSpaceNav", true)
        sw("Show on-screen keyboard for numeric/PIN fields", "softForNumeric", true)
        sw("Always show on-screen keyboard", "forceSoft", false)
        sw("Compact status strip when the on-screen keyboard is hidden", "compactStrip", true)
        col.addView(
            Button(this).apply {
                text = "Enable / choose input method"
                setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            }
        )
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
