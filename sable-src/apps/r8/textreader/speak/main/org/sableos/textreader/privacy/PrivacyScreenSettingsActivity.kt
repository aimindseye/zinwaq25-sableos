package org.sableos.textreader.privacy

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import org.vaachak.textreader.R

/**
 * Privacy Screen settings: how dark the shade is. Opened from the service's page in Accessibility
 * settings and by holding the Quick Settings tile. The choice applies at once if the shade is up.
 */
class PrivacyScreenSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.sable_privacy_screen_label)
        val pad = dp(16)
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
            }
        column.addView(
            TextView(this).apply {
                setText(R.string.sable_privacy_screen_darkness)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            },
        )
        column.addView(
            TextView(this).apply {
                setText(R.string.sable_privacy_screen_darkness_summary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(4), 0, dp(8))
            },
        )
        val current = PrivacyDarkness.read(this)
        val group = RadioGroup(this)
        PrivacyDarkness.Level.entries.forEach { level ->
            group.addView(
                RadioButton(this).apply {
                    id = View.generateViewId()
                    tag = level
                    text = getString(labelFor(level))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    minHeight = dp(48)
                    isChecked = level == current
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        group.setOnCheckedChangeListener { radios, checkedId ->
            val level = radios.findViewById<View>(checkedId)?.tag as? PrivacyDarkness.Level ?: return@setOnCheckedChangeListener
            PrivacyDarkness.write(this, level)
        }
        column.addView(group)
        val scroll = ScrollView(this).apply { addView(column) }
        // Keep the page between the status bar and the navigation bar (edge to edge from Android 15).
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    private fun labelFor(level: PrivacyDarkness.Level): Int =
        when (level) {
            PrivacyDarkness.Level.Dim -> R.string.sable_privacy_screen_darkness_dim
            PrivacyDarkness.Level.Dark -> R.string.sable_privacy_screen_darkness_dark
            PrivacyDarkness.Level.Black -> R.string.sable_privacy_screen_darkness_black
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
