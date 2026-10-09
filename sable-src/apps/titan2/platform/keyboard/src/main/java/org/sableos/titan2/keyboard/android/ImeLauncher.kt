package org.sableos.titan2.keyboard.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import org.sableos.titan2.keyboard.core.ImeAction
import org.sableos.titan2.keyboard.core.ImeLaunchOutcome
import org.sableos.titan2.keyboard.core.ImeStatus
import org.sableos.titan2.keyboard.core.ImeStatusModel

/**
 * Observes the keyboard setup through public APIs and opens the screens where the user can change it. It reads
 * the selected and enabled input methods; it never writes them, and a failed open is reported, not swallowed.
 */
class ImeLauncher(private val activity: Activity) {
    fun status(): ImeStatus {
        val imm =
            activity.getSystemService(InputMethodManager::class.java) ?: return ImeStatus.Unknown
        return try {
            val selected = Settings.Secure.getString(
                activity.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            ImeStatusModel.evaluate(selected, imm.enabledInputMethodList.map { it.id })
        } catch (_: SecurityException) {
            ImeStatus.Unknown
        } catch (_: RuntimeException) {
            ImeStatus.Unknown
        }
    }

    fun run(action: ImeAction): ImeLaunchOutcome = try {
        when (action) {
            ImeAction.OpenInputMethodSettings ->
                activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

            ImeAction.ShowPicker ->
                activity.getSystemService(
                    InputMethodManager::class.java
                )?.showInputMethodPicker()
                    ?: throw ActivityNotFoundException()
        }
        ImeLaunchOutcome.Launched
    } catch (_: ActivityNotFoundException) {
        ImeLaunchOutcome.TargetMissing
    } catch (_: SecurityException) {
        ImeLaunchOutcome.Denied
    } catch (_: RuntimeException) {
        ImeLaunchOutcome.Failed
    }
}
