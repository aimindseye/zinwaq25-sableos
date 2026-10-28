package org.sableos.titan2.setup.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import org.sableos.titan2.setup.core.Action
import org.sableos.titan2.setup.core.LaunchOutcome
import org.sableos.titan2.setup.core.SetupRows
import org.sableos.titan2.setup.core.TargetStatus

/**
 * Resolves and opens the screens the checklist points at. Nothing here writes a setting or a role: every
 * destination is a system or app screen the user confirms in. Failures are classified, never swallowed.
 */
class Launcher(private val activity: Activity) {
    private fun intent(action: Action): Intent? = when (action) {
        Action.None, Action.KeyboardPicker, Action.WeatherCity -> null

        Action.HomeStyle ->
            Intent(HOME_STYLE_ACTION).setPackage(SetupRows.SABLE_START_PACKAGE)

        Action.HomeSettings -> Intent(Settings.ACTION_HOME_SETTINGS)

        Action.KeyboardSettings -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

        Action.DefaultApps -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

        Action.NetworkSettings -> Intent(Settings.ACTION_WIRELESS_SETTINGS)

        Action.DisplayCompat -> activity.packageManager.getLaunchIntentForPackage(
            DISPLAY_PACKAGE
        )

        Action.RadioDiag -> activity.packageManager.getLaunchIntentForPackage(RADIO_PACKAGE)
    }

    /**
     * Checked before a button is offered. The keyboard picker has no intent; it needs its system service.
     * The Weather city step is Setup's own screen; it needs Sable Weather's provider.
     */
    fun resolve(action: Action): TargetStatus = try {
        val found = when (action) {
            Action.WeatherCity -> WeatherCityClient(activity).available()

            Action.KeyboardPicker ->
                activity.getSystemService(InputMethodManager::class.java) != null

            else -> intent(action)?.let {
                activity.packageManager.resolveActivity(it, 0) != null
            } ?: false
        }
        if (found) TargetStatus.Resolvable else TargetStatus.Missing
    } catch (_: SecurityException) {
        TargetStatus.Unverifiable
    } catch (_: RuntimeException) {
        TargetStatus.Unverifiable
    }

    fun run(action: Action): LaunchOutcome = try {
        if (action == Action.KeyboardPicker) {
            val imm = activity.getSystemService(InputMethodManager::class.java)
            if (imm == null) {
                LaunchOutcome.TargetMissing
            } else {
                imm.showInputMethodPicker()
                LaunchOutcome.Launched
            }
        } else {
            val target = intent(action)
            if (target == null) {
                LaunchOutcome.TargetMissing
            } else {
                activity.startActivity(target)
                LaunchOutcome.Launched
            }
        }
    } catch (_: ActivityNotFoundException) {
        LaunchOutcome.TargetMissing
    } catch (_: SecurityException) {
        LaunchOutcome.Denied
    } catch (_: RuntimeException) {
        LaunchOutcome.Failed
    }

    private companion object {
        const val HOME_STYLE_ACTION = "org.sableos.start.action.CHOOSE_HOME_STYLE"
        const val DISPLAY_PACKAGE = "org.sableos.titan2.displaycompat"
        const val RADIO_PACKAGE = "org.sableos.titan2.radiodiag"
    }
}
