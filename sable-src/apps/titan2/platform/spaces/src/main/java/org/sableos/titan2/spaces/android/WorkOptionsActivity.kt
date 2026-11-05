package org.sableos.titan2.spaces.android

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import android.view.KeyEvent
import android.widget.LinearLayout
import android.window.OnBackInvokedDispatcher
import org.sableos.titan2.spaces.android.SpacesViews.Companion.BODY_SP
import org.sableos.titan2.spaces.android.SpacesViews.Companion.GAP
import org.sableos.titan2.spaces.android.SpacesViews.Companion.PAD
import org.sableos.titan2.spaces.android.SpacesViews.Companion.SMALL_SP
import org.sableos.titan2.spaces.android.SpacesViews.Companion.TITLE_SP
import org.sableos.titan2.spaces.core.SpacesText.t
import org.sableos.titan2.spaces.core.WorkOption
import org.sableos.titan2.spaces.core.WorkSpace
import org.sableos.titan2.theme.SablePalette

/**
 * Inside the Work space: its options. Copying out of Work, sharing into Work and screenshots of work apps
 * each start as Allowed; Enter on one switches it between Allowed and Blocked. L opens Android's own screen
 * for a Work lock, separate from the phone's. Any app of the main user can open this screen through the
 * forwarding, but only a key press or tap here changes anything.
 */
class WorkOptionsActivity : Activity() {
    private lateinit var dpm: DevicePolicyManager
    private lateinit var views: SpacesViews
    private lateinit var body: LinearLayout
    private var selected = 0
    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isProfileOwnerApp(packageName)) {
            finish()
            return
        }
        val palette = SablePalette.read(this).also { it.applyTo(this) }
        views = SpacesViews(this, palette)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(views.px(PAD), views.px(PAD), views.px(PAD), views.px(PAD))
            setBackgroundColor(palette.window)
        }
        setContentView(body)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) { finish() }
        render()
    }

    private fun isBlocked(option: WorkOption): Boolean {
        val admin = SpacesAdminReceiver.component(this)
        return when (option) {
            WorkOption.COPY_OUT -> restricted(UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE)
            WorkOption.SHARE_IN -> restricted(UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE)
            WorkOption.SCREENSHOTS -> dpm.getScreenCaptureDisabled(admin)
        }
    }

    private fun restricted(key: String): Boolean =
        dpm.getUserRestrictions(SpacesAdminReceiver.component(this)).getBoolean(key, false)

    private fun toggle(option: WorkOption) {
        val admin = SpacesAdminReceiver.component(this)
        val block = !isBlocked(option)
        try {
            when (option) {
                WorkOption.COPY_OUT -> restrict(
                    UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE,
                    block
                )

                WorkOption.SHARE_IN -> restrict(
                    UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE,
                    block
                )

                WorkOption.SCREENSHOTS -> dpm.setScreenCaptureDisabled(admin, block)
            }
            message = null
        } catch (e: SecurityException) {
            Log.w(TAG, "Android refused to change ${option.name}", e)
            message = t("options_failed")
        }
        render()
    }

    private fun restrict(key: String, block: Boolean) {
        val admin = SpacesAdminReceiver.component(this)
        if (block) dpm.addUserRestriction(admin, key) else dpm.clearUserRestriction(admin, key)
    }

    /** Android's own screen for the Work space's lock, started here so it applies to Work. */
    private fun workLock() {
        try {
            startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
        } catch (_: ActivityNotFoundException) {
            message = t("options_failed")
            render()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val options = WorkOption.entries
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> select(WorkSpace.move(selected, -1, options.size))

            KeyEvent.KEYCODE_DPAD_DOWN -> select(WorkSpace.move(selected, 1, options.size))

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER
            -> toggle(options[selected])

            KeyEvent.KEYCODE_L -> workLock()

            KeyEvent.KEYCODE_ESCAPE -> finish()

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun select(index: Int) {
        selected = index
        render()
    }

    private fun render() {
        val palette = views.palette
        body.removeAllViews()
        body.addView(views.text(t("options"), TITLE_SP, palette.text, bold = true))
        body.addView(views.text(t("options_intro"), BODY_SP, palette.muted))
        WorkOption.entries.forEachIndexed { i, option ->
            val blocked = isBlocked(option)
            val line = t("option_line", t(option.key), t(WorkSpace.optionValueKey(blocked)))
            body.addView(
                views.text(line, BODY_SP, if (blocked) palette.warn else palette.text).apply {
                    val on = i == selected
                    setPadding(views.px(GAP), views.px(GAP), views.px(GAP), views.px(GAP))
                    background = views.box(
                        if (on) palette.surface else palette.window,
                        if (on) palette.accent else palette.window
                    )
                    setOnClickListener {
                        selected = i
                        toggle(option)
                    }
                }
            )
        }
        message?.let { body.addView(views.text(it, SMALL_SP, palette.warn)) }
        body.addView(views.button(t("work_lock")) { workLock() })
        body.addView(views.text(t("work_lock_about"), SMALL_SP, palette.muted))
        body.addView(views.text(t("options_hint"), SMALL_SP, palette.muted))
    }

    private companion object {
        const val TAG = "SableSpaces"
    }
}
