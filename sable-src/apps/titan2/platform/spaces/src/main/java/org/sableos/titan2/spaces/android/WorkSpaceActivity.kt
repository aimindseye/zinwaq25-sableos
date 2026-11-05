package org.sableos.titan2.spaces.android

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.window.OnBackInvokedDispatcher
import org.sableos.titan2.spaces.android.SpacesViews.Companion.BODY_SP
import org.sableos.titan2.spaces.android.SpacesViews.Companion.GAP
import org.sableos.titan2.spaces.android.SpacesViews.Companion.PAD
import org.sableos.titan2.spaces.android.SpacesViews.Companion.SMALL_SP
import org.sableos.titan2.spaces.android.SpacesViews.Companion.TITLE_SP
import org.sableos.titan2.spaces.core.AppChoice
import org.sableos.titan2.spaces.core.SpaceState
import org.sableos.titan2.spaces.core.SpacesText.t
import org.sableos.titan2.spaces.core.WorkSpace
import org.sableos.titan2.theme.SablePalette

/**
 * The main user's Work space screen, opened from Sable Setup: make the Work space with Android's own
 * managed-profile setup, add apps the user already has to it, or ask the Work space to remove itself.
 * Adding and removing are confirmed again inside the Work space.
 */
class WorkSpaceActivity : Activity() {
    private lateinit var views: SpacesViews
    private lateinit var body: LinearLayout
    private var state = SpaceState.None
    private var apps: List<AppChoice> = emptyList()
    private var selected = 0
    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = SablePalette.read(this).also { it.applyTo(this) }
        views = SpacesViews(this, palette)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(views.px(PAD), views.px(PAD), views.px(PAD), views.px(PAD))
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(palette.window)
                isFocusable = false
                addView(body)
            }
        )
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) { finish() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val work = workSpace()
        val paused = work != null &&
            runCatching { getSystemService(UserManager::class.java).isQuietModeEnabled(work) }
                .getOrDefault(true)
        val allowed = runCatching {
            getSystemService(DevicePolicyManager::class.java)
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        }.getOrDefault(false)
        state = WorkSpace.state(hasWorkSpace = work != null, paused = paused, allowed = allowed)
        apps = if (state == SpaceState.On && work != null) {
            val inWork = launchable(work).map { it.componentName.packageName }.toSet()
            WorkSpace.addable(ownApps(), packageName, inWork)
        } else {
            emptyList()
        }
        selected = WorkSpace.move(selected, 0, apps.size)
        render()
    }

    /** The Work space: a profile of this phone where Sable Spaces is installed, which only work profiles have. */
    private fun workSpace(): UserHandle? = runCatching {
        getSystemService(CrossProfileApps::class.java).targetUserProfiles.firstOrNull()
    }.getOrNull()

    private fun ownApps(): List<AppChoice> = launchable(Process.myUserHandle()).map {
        AppChoice(it.componentName.packageName, it.label.toString())
    }

    private fun launchable(user: UserHandle): List<LauncherActivityInfo> = runCatching {
        getSystemService(LauncherApps::class.java).getActivityList(null, user)
    }.getOrDefault(emptyList())

    // ---- keys ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> move(-1)

            KeyEvent.KEYCODE_DPAD_DOWN -> move(1)

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER
            -> primary()

            KeyEvent.KEYCODE_R -> if (state == SpaceState.On || state == SpaceState.Paused) remove()

            KeyEvent.KEYCODE_ESCAPE -> finish()

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun move(delta: Int) {
        selected = WorkSpace.move(selected, delta, apps.size)
        render()
    }

    private fun primary() {
        when (state) {
            SpaceState.None -> make()
            SpaceState.On -> apps.getOrNull(selected)?.let(::add)
            SpaceState.NotAllowed, SpaceState.Paused -> Unit
        }
    }

    // ---- actions ----

    private fun make() {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            SpacesAdminReceiver.component(this)
        )
        try {
            startActivityForResult(intent, REQUEST_MAKE)
        } catch (_: ActivityNotFoundException) {
            say(t("not_allowed"))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MAKE) {
            message = t(if (resultCode == RESULT_OK) "making" else "cancelled")
        }
    }

    private fun add(app: AppChoice) {
        forward(
            Intent(WorkSpace.ACTION_ADD_APP)
                .putExtra(WorkSpace.EXTRA_PACKAGE, app.packageName)
                .putExtra(WorkSpace.EXTRA_LABEL, app.label)
        )
    }

    private fun remove() = forward(Intent(WorkSpace.ACTION_REMOVE))

    /** Into the Work space through the forwarding it set up; it has no package, so Android picks the forwarder. */
    private fun forward(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            say(t("could_not_open"))
        } catch (_: SecurityException) {
            say(t("could_not_open"))
        }
    }

    private fun say(line: String) {
        message = line
        render()
    }

    // ---- view ----

    private fun render() {
        val palette = views.palette
        body.removeAllViews()
        body.addView(views.text(t("work_space"), TITLE_SP, palette.text, bold = true))
        val detailColor = if (state == SpaceState.NotAllowed) palette.warn else palette.muted
        body.addView(views.text(t(WorkSpace.detailKey(state)), BODY_SP, detailColor))
        message?.let { body.addView(views.text(it, SMALL_SP, palette.accent)) }
        when (state) {
            SpaceState.None -> body.addView(views.button(t("make")) { make() })

            SpaceState.On -> {
                if (apps.isEmpty()) body.addView(views.text(t("no_apps"), BODY_SP, palette.muted))
                apps.forEachIndexed { i, app -> body.addView(row(i, app)) }
                body.addView(views.button(t("remove")) { remove() })
                body.addView(views.text(t("hint"), SMALL_SP, palette.muted))
            }

            SpaceState.Paused -> body.addView(views.button(t("remove")) { remove() })

            SpaceState.NotAllowed -> Unit
        }
    }

    private fun row(i: Int, app: AppChoice) =
        views.text(app.label, BODY_SP, views.palette.text).apply {
            val on = i == selected
            val palette = views.palette
            setPadding(views.px(GAP), views.px(GAP), views.px(GAP), views.px(GAP))
            background = views.box(
                if (on) palette.surface else palette.window,
                if (on) palette.accent else palette.window
            )
            setOnClickListener {
                selected = i
                add(app)
            }
        }

    private companion object {
        const val REQUEST_MAKE = 1
    }
}
