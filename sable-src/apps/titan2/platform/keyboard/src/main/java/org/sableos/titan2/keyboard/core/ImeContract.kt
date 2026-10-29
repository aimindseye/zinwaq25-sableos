package org.sableos.titan2.keyboard.core

import org.sableos.titan2.keyboard.core.KeyboardText.t

/**
 * The first-party input method as the canonical lane must provision it (interface request IR-005), plus the
 * product policy around it. This is a declaration and an observation model only: nothing here, or anywhere in
 * the keyboard app, writes a Setting, holds a role, uses root or a privileged helper, or switches the user's keyboard.
 */
object ImeContract {
    const val PACKAGE = "org.sableos.titan2.keyboard"
    const val SERVICE_CLASS = "$PACKAGE.android.SableImeService"
    const val SHORT_SERVICE_CLASS = ".android.SableImeService"
    const val COMPONENT = "$PACKAGE/$SHORT_SERVICE_CLASS"

    /** The user may install, enable and select any other IME, and the choice is never reverted by this app. */
    const val USER_SELECTABLE_THIRD_PARTY_IME = true
    const val FORCE_AFTER_USER_SELECTION = false

    /** Sable Keyboard is a factory default only; the canonical image provisions it once, at first boot. */
    const val FACTORY_DEFAULT_ONLY = true

    /** Typing before the first unlock must not need credential-protected storage. */
    const val CREDENTIAL_PROTECTED_STATE_REQUIRED = false
    const val PREFS_FILE = "sable_keyboard"

    /** True for an IME id in either flattened form ("pkg/.cls" or "pkg/pkg.cls"); never for a look-alike. */
    fun matches(id: String?): Boolean {
        val parts = id?.trim()?.split('/', limit = 2).orEmpty()
        if (parts.size != 2 || parts[0] != PACKAGE) return false
        return parts[1] == SHORT_SERVICE_CLASS || parts[1] == SERVICE_CLASS
    }
}

/** What the app can honestly say about the keyboard setup. [Unknown] is never turned into "not selected". */
enum class ImeStatus { SableSelected, SableEnabledOtherSelected, SableNotEnabled, Unknown }

enum class ImeAction(private val labelKey: String) {
    OpenInputMethodSettings("input_method_settings"),
    ShowPicker("keyboard_picker");

    val label: String get() = t(labelKey)
}

object ImeStatusModel {
    fun evaluate(selected: String?, enabled: List<String>?): ImeStatus = when {
        ImeContract.matches(selected) -> ImeStatus.SableSelected
        enabled == null || selected.isNullOrBlank() -> ImeStatus.Unknown
        enabled.any { ImeContract.matches(it) } -> ImeStatus.SableEnabledOtherSelected
        else -> ImeStatus.SableNotEnabled
    }

    fun describe(status: ImeStatus): String = when (status) {
        ImeStatus.SableSelected -> t("sable_keyboard_is_the_selected")

        ImeStatus.SableEnabledOtherSelected ->
            t("another_keyboard_is_selected_your")

        ImeStatus.SableNotEnabled -> t("sable_keyboard_is_not_enabled")

        ImeStatus.Unknown -> t("could_not_read_which_keyboard")
    }

    /** Screens the user may open. Opening one changes nothing by itself. */
    fun actions(status: ImeStatus): List<ImeAction> = when (status) {
        ImeStatus.SableSelected -> emptyList()
        ImeStatus.SableEnabledOtherSelected -> listOf(ImeAction.ShowPicker)
        ImeStatus.SableNotEnabled -> listOf(ImeAction.OpenInputMethodSettings)
        ImeStatus.Unknown -> listOf(ImeAction.OpenInputMethodSettings, ImeAction.ShowPicker)
    }

    /** There is no state in which this app changes the selected keyboard. */
    fun mayChangeSelection(): Boolean = ImeContract.FORCE_AFTER_USER_SELECTION
}

enum class ImeLaunchOutcome { Launched, TargetMissing, Denied, Failed }

object ImeLaunchReport {
    const val MAX_MESSAGE = 120

    /** One bounded line for a failed open; never carries exception text. */
    fun message(action: ImeAction, outcome: ImeLaunchOutcome): String? {
        val text =
            when (outcome) {
                ImeLaunchOutcome.Launched -> return null

                ImeLaunchOutcome.TargetMissing ->
                    t("could_not_open_not_available", action.label)

                ImeLaunchOutcome.Denied -> t("could_not_open_blocked_by", action.label)

                ImeLaunchOutcome.Failed -> t("could_not_open", action.label)
            }
        return text.take(MAX_MESSAGE)
    }
}
