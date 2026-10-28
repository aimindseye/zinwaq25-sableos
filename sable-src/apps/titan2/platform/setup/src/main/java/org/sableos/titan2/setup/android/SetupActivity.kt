package org.sableos.titan2.setup.android

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.edit
import org.sableos.titan2.setup.android.SetupStyle.BG
import org.sableos.titan2.setup.android.SetupStyle.DETAIL_SP
import org.sableos.titan2.setup.android.SetupStyle.DIM
import org.sableos.titan2.setup.android.SetupStyle.KEY
import org.sableos.titan2.setup.android.SetupStyle.PAD_H
import org.sableos.titan2.setup.android.SetupStyle.PAD_V
import org.sableos.titan2.setup.android.SetupStyle.ROW_TITLE_SP
import org.sableos.titan2.setup.android.SetupStyle.SPACING
import org.sableos.titan2.setup.android.SetupStyle.SUMMARY_SP
import org.sableos.titan2.setup.android.SetupStyle.TEXT
import org.sableos.titan2.setup.android.SetupStyle.TITLE_SP
import org.sableos.titan2.setup.android.SetupStyle.WARN
import org.sableos.titan2.setup.core.Action
import org.sableos.titan2.setup.core.Item
import org.sableos.titan2.setup.core.LaunchReport
import org.sableos.titan2.setup.core.Readiness
import org.sableos.titan2.setup.core.SetupModel
import org.sableos.titan2.setup.core.SetupRows
import org.sableos.titan2.setup.core.TargetStatus
import org.sableos.titan2.theme.SablePalette

/**
 * Post-provisioning readiness checklist. This is not the Android Setup Wizard (that is a privileged system
 * package and is left alone); it is a normal app that shows what it can observe and opens the matching screen.
 * Nothing here changes a setting by itself: every action opens a screen the user confirms, and a screen that
 * cannot be opened is shown as unavailable instead of silently doing nothing. The one choice made in Setup
 * itself is the Weather city, which Sable Weather stores; that step is offered once, and Skip keeps the
 * city Weather already has.
 */
class SetupActivity : Activity() {
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var launcher: Launcher
    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * dp).toInt()

    private var inWeatherStep = false
    private val backFromStep = OnBackInvokedCallback { closeWeatherStep(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SetupStyle.palette = SablePalette.read(this).also { it.applyTo(this) }
        launcher = Launcher(this)
        body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(PAD_H), px(PAD_V), px(PAD_H), px(PAD_V))
            }
        scroll =
            ScrollView(this).apply {
                setBackgroundColor(BG)
                addView(body)
            }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val offerWeather = !weatherStepAsked() &&
            launcher.resolve(Action.WeatherCity) == TargetStatus.Resolvable
        when {
            inWeatherStep -> Unit
            offerWeather -> showWeatherStep()
            else -> render(null)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (inWeatherStep && event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) closeWeatherStep(null)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showWeatherStep() {
        inWeatherStep = true
        body.removeAllViews()
        body.addView(WeatherCityView(this, WeatherCityClient(this), ::closeWeatherStep).build())
        scroll.scrollTo(0, 0)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            backFromStep
        )
    }

    /** Back, Escape, Skip and a successful choice all end the step; it is not offered again by itself. */
    private fun closeWeatherStep(note: String?) {
        if (!inWeatherStep) return
        inWeatherStep = false
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backFromStep)
        prefs().edit { putBoolean(KEY_WEATHER_ASKED, true) }
        render(null, note)
        scroll.scrollTo(0, 0)
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun weatherStepAsked() = prefs().getBoolean(KEY_WEATHER_ASKED, false)

    private fun render(failure: String?, note: String? = null) {
        body.removeAllViews()
        val items = SetupRows.build(SetupReader(this).snapshot(), launcher::resolve)
        body.addView(text(SetupModel.TITLE, TITLE_SP, TEXT, true))
        body.addView(text(SetupModel.SELF_DESCRIPTION, DETAIL_SP, DIM, false))
        body.addView(
            text(SetupModel.profileLine(SysProps.get("ro.sable.profile.id")), DETAIL_SP, DIM, false)
        )
        val ready = if (SetupModel.isReady(items)) " · no action required" else ""
        body.addView(
            text(SetupModel.summary(items) + ready, SUMMARY_SP, TEXT, false)
                .apply { setPadding(0, px(SPACING), 0, px(SPACING)) }
        )
        if (failure != null) body.addView(text(failure, DETAIL_SP, WARN, true))
        if (note != null) body.addView(text(note, DETAIL_SP, TEXT, true))
        items.forEach { addRow(it) }
        body.addView(
            text(SetupModel.FOOTNOTE, DETAIL_SP, DIM, false).apply {
                setPadding(0, px(SPACING), 0, 0)
            }
        )
    }

    private fun addRow(item: Item) {
        body.addView(
            text(marker(item.state) + item.title, ROW_TITLE_SP, TEXT, true)
                .apply { setPadding(0, px(SPACING), 0, 0) }
        )
        body.addView(text(item.detail, DETAIL_SP, DIM, false))
        val label = SetupRows.buttonLabel(item) ?: return
        body.addView(
            Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(TEXT)
                setBackgroundColor(KEY)
                setOnClickListener {
                    if (item.action == Action.WeatherCity) {
                        showWeatherStep()
                    } else {
                        render(LaunchReport.message(item.action.label, launcher.run(item.action)))
                    }
                }
            }
        )
    }

    private fun marker(state: Readiness): String = when (state) {
        Readiness.Satisfied -> "✓ "
        Readiness.ActionRequired -> "○ "
        Readiness.Informational -> "› "
        Readiness.Unavailable -> "✕ "
        Readiness.Unknown -> "? "
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private companion object {
        const val PREFS = "sable_setup"
        const val KEY_WEATHER_ASKED = "weather_city_step_done"
    }
}
