package org.sableos.start.privacy

/**
 * All Apps keyboard behaviour (DESIGN-KF-D):
 *
 * ```text
 * Up/Down           move app focus        (platform focus traversal; not consumed here)
 * Enter             open app
 * Space             expand compact privacy detail
 * Menu / Fn+Enter   actions
 * / or Search       search
 * Printable letters type-to-jump when search is not active
 * ```
 *
 * Pure: the host passes public KeyEvent integers. No device scan codes, no
 * device names: Fn arrives as the public META_FUNCTION_ON flag.
 */
enum class AllAppsKeyAction {
    Open,
    ToggleDetail,
    Actions,
    Search,
    TypeToJump,

    /** A held one-shot key: swallowed so it neither re-fires nor leaks to the platform. */
    IgnoreRepeat,

    /** Not handled here: the platform keeps it (focus traversal, Back, text fields, shortcuts). */
    PassThrough,
}

data class AllAppsKeyDecision(
    val action: AllAppsKeyAction,
    val char: Char = NO_CHAR,
) {
    val consumed: Boolean get() = action != AllAppsKeyAction.PassThrough

    companion object {
        const val NO_CHAR = '\u0000'
    }
}

object AllAppsKeyPolicy {
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_SPACE = 62
    const val KEYCODE_ENTER = 66
    const val KEYCODE_SLASH = 76
    const val KEYCODE_MENU = 82
    const val KEYCODE_SEARCH = 84
    const val KEYCODE_NUMPAD_ENTER = 160
    const val META_SHIFT_ON = 0x1
    const val META_ALT_ON = 0x2
    const val META_FUNCTION_ON = 0x8
    const val META_CTRL_ON = 0x1000
    const val META_META_ON = 0x10000

    /**
     * @param editableFocused a text field owns focus: every key goes to it.
     * @param repeat held keys never repeat one-shot actions.
     */
    fun decide(
        keyCode: Int,
        unicode: Int,
        metaState: Int,
        repeat: Boolean = false,
        editableFocused: Boolean = false,
    ): AllAppsKeyDecision {
        val pass = AllAppsKeyDecision(AllAppsKeyAction.PassThrough)
        if (editableFocused) return pass
        val commandModifier = metaState and (META_CTRL_ON or META_META_ON) != 0
        val action =
            when (keyCode) {
                KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER, KEYCODE_DPAD_CENTER ->
                    if (metaState and META_FUNCTION_ON != 0) AllAppsKeyAction.Actions else AllAppsKeyAction.Open
                KEYCODE_MENU -> AllAppsKeyAction.Actions
                KEYCODE_SEARCH -> AllAppsKeyAction.Search
                KEYCODE_SPACE -> if (commandModifier) null else AllAppsKeyAction.ToggleDetail
                else -> null
            }
        if (action != null) {
            return if (repeat) ignoreRepeat() else AllAppsKeyDecision(action)
        }
        if (commandModifier || metaState and META_ALT_ON != 0) return pass
        val c = printable(unicode) ?: return pass
        if (c == '/') {
            return if (repeat) ignoreRepeat() else AllAppsKeyDecision(AllAppsKeyAction.Search)
        }
        if (!c.isLetterOrDigit()) return pass
        return if (repeat) ignoreRepeat() else AllAppsKeyDecision(AllAppsKeyAction.TypeToJump, c)
    }

    private fun ignoreRepeat() = AllAppsKeyDecision(AllAppsKeyAction.IgnoreRepeat)

    private fun printable(unicode: Int): Char? {
        if (unicode <= 0 || unicode > Char.MAX_VALUE.code) return null
        val c = unicode.toChar()
        return if (c.isISOControl() || c.isWhitespace()) null else c
    }
}

/** App actions offered from All Apps (Menu / Fn+Enter, the row's ⋯ button, or Peek). */
enum class AppAction(
    val label: String,
    val destructive: Boolean = false,
) {
    Open("open"),
    AppInfo("app info · permissions"),
    NotificationSettings("notification settings"),
    PinToStart("pin to start"),
    RemoveFromStart("remove from start"),
    AddToBaseBar("add to base bar"),
    RemoveFromBaseBar("remove from base bar"),
    Uninstall("uninstall", destructive = true),
    Disable("disable", destructive = true),
}

data class AppActionContext(
    val pinnedToStart: Boolean,
    /** null: this host has no base bar surface, so the action is not offered. */
    val inBaseBar: Boolean?,
    /** Notification settings can only be opened for apps of the user the launcher runs as. */
    val launcherUser: Boolean,
    /** Not a system app (or an updated one the user may remove) and not blocked by policy. */
    val canUninstall: Boolean,
    /** Android exposes disable for this app (eligible system app); host decides. */
    val canDisable: Boolean = false,
)

object AppActionPolicy {
    fun actions(context: AppActionContext): List<AppAction> =
        buildList {
            add(AppAction.Open)
            add(AppAction.AppInfo)
            if (context.launcherUser) add(AppAction.NotificationSettings)
            add(if (context.pinnedToStart) AppAction.RemoveFromStart else AppAction.PinToStart)
            when (context.inBaseBar) {
                true -> add(AppAction.RemoveFromBaseBar)
                false -> add(AppAction.AddToBaseBar)
                null -> Unit
            }
            if (context.canUninstall) add(AppAction.Uninstall)
            if (context.canDisable) add(AppAction.Disable)
        }

    /** Destructive actions need a second, explicit confirmation step (UNINSTALL_ONE_KEY=NO). */
    fun requiresConfirmation(action: AppAction): Boolean = action.destructive

    fun confirmationLabel(action: AppAction): String = "confirm ${action.label}"
}
