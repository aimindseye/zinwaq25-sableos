package org.sableos.tools.core

/** A key press, reduced to what command mapping needs. Codes are Android KeyEvent constants copied as numbers. */
data class KeyPress(val keyCode: Int, val unicode: Int = 0, val meta: Int = 0, val repeat: Int = 0)

sealed interface Command {
    data class Move(val dx: Int, val dy: Int) : Command
    data object Open : Command
    data object Actions : Command
    data object Back : Command
    data object Search : Command
    data class FilterAppend(val ch: Char) : Command
    data object FilterDelete : Command
    data object Calibrate : Command
    data object Reset : Command
    data object Hold : Command
    data object Info : Command
    data object Copy : Command
    data object Toggle : Command
    data object NextField : Command
    data object PrevField : Command

    /** The key is not a command here; let the platform/view handle it (text fields, key capture). */
    data object PassThrough : Command
}

/** Where focus is, which decides what a key means. */
sealed interface KeyContext {
    /** Home list, no text field active: printable keys type-to-filter. */
    data class Home(val filterActive: Boolean) : KeyContext

    /** Inside a tool. Single-letter commands are local to the tool kind. */
    data class InTool(val kind: ToolKind, val calibratable: Boolean) : KeyContext

    /** A text field owns input: only Back/Escape are commands. */
    data object TextField : KeyContext
}

/**
 * DESIGN-KF-C keyboard-first map:
 * ```
 * printable key   type-to-filter tool names when no text field is active
 * arrows          move focus          Enter        open
 * Menu / Fn+Enter actions/details     Back/Esc     return one level
 * /               explicit search     C/R/H/I      calibrate/reset/hold/info, tool context only
 * ```
 * Single-letter commands are disabled while a text field owns input (TEXT_INPUT_ALWAYS_WINS).
 */
object KeyCommands {
    const val KEYCODE_BACK = 4
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_SPACE = 62
    const val KEYCODE_TAB = 61
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_SLASH = 76
    const val KEYCODE_MENU = 82
    const val KEYCODE_SEARCH = 84
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_NUMPAD_ENTER = 160
    const val META_SHIFT_ON = 0x1
    const val META_ALT_ON = 0x2
    const val META_SYM_ON = 0x4
    const val META_FUNCTION_ON = 0x8
    const val META_CTRL_ON = 0x1000

    private const val FIRST_PRINTABLE = 0x20

    fun map(ctx: KeyContext, k: KeyPress): Command = when {
        // Key capture sees every key as data, Back included; it leaves through [DoubleBackExit].
        ctx is KeyContext.InTool && ctx.kind == ToolKind.KEY_CAPTURE -> Command.PassThrough

        k.keyCode == KEYCODE_BACK || k.keyCode == KEYCODE_ESCAPE -> Command.Back

        else -> when (ctx) {
            KeyContext.TextField -> Command.PassThrough

            is KeyContext.Home -> common(k) ?: home(ctx, k)

            is KeyContext.InTool ->
                common(k)
                    ?: if (ctx.kind == ToolKind.TEXT_TEST) Command.PassThrough else tool(ctx, k)
        }
    }

    private fun isEnter(k: KeyPress) =
        k.keyCode == KEYCODE_ENTER || k.keyCode == KEYCODE_NUMPAD_ENTER ||
            k.keyCode == KEYCODE_DPAD_CENTER

    private fun common(k: KeyPress): Command? = when {
        k.keyCode == KEYCODE_DPAD_UP -> Command.Move(0, -1)

        k.keyCode == KEYCODE_DPAD_DOWN -> Command.Move(0, 1)

        k.keyCode == KEYCODE_DPAD_LEFT -> Command.Move(-1, 0)

        k.keyCode == KEYCODE_DPAD_RIGHT -> Command.Move(1, 0)

        k.keyCode == KEYCODE_MENU -> Command.Actions

        isEnter(k) && k.meta and META_FUNCTION_ON != 0 -> Command.Actions

        isEnter(k) -> Command.Open

        k.keyCode == KEYCODE_TAB -> if (k.meta and META_SHIFT_ON !=
            0
        ) {
            Command.PrevField
        } else {
            Command.NextField
        }

        k.meta and META_CTRL_ON != 0 && k.unicode.lower() == 'c' -> Command.Copy

        else -> null
    }

    private fun Int.lower(): Char? = if (this >=
        FIRST_PRINTABLE
    ) {
        Character.toChars(this)[0].lowercaseChar()
    } else {
        null
    }

    /** The printable character of a plain key press; null with Ctrl/Alt or for non-printing keys. */
    private fun plainChar(k: KeyPress): Char? =
        if (k.meta and (META_CTRL_ON or META_ALT_ON) != 0) null else k.unicode.lower()

    private fun home(ctx: KeyContext.Home, k: KeyPress): Command {
        val ch = plainChar(k)
        val idle = !ctx.filterActive
        return when {
            k.keyCode == KEYCODE_SEARCH -> Command.Search
            k.keyCode == KEYCODE_DEL -> if (idle) Command.PassThrough else Command.FilterDelete
            ch == null -> Command.PassThrough
            ch == '/' && idle -> Command.Search
            ch == ' ' && idle -> Command.PassThrough
            ch.isLetterOrDigit() || ch in FILTER_SYMBOLS -> Command.FilterAppend(ch)
            else -> Command.PassThrough
        }
    }

    private const val FILTER_SYMBOLS = " *#"

    private fun tool(ctx: KeyContext.InTool, k: KeyPress): Command {
        val measuring = ctx.kind == ToolKind.MEASUREMENT
        val ch = plainChar(k)
        return when {
            k.keyCode == KEYCODE_SPACE && ctx.kind == ToolKind.REPORT -> Command.Toggle
            ch == 'i' -> Command.Info
            ch == 'c' && measuring && ctx.calibratable -> Command.Calibrate
            ch == 'r' && measuring -> Command.Reset
            ch == 'h' && measuring -> Command.Hold
            else -> Command.PassThrough
        }
    }

    /** Context for a tool screen. */
    fun contextFor(tool: Tool, textFieldFocused: Boolean): KeyContext = if (textFieldFocused) {
        KeyContext.TextField
    } else {
        KeyContext.InTool(tool.kind, tool.capability?.calibratable == true)
    }

    /**
     * Key-capture screens (key viewer, pointer test) consume every key including Back, so leaving needs Back/Escape
     * twice in a row; any other key in between resets the count.
     */
    class DoubleBackExit(private val needed: Int = 2) {
        private var count = 0

        /** Feed every key-up; returns true when the screen should close. */
        fun onKeyUp(keyCode: Int): Boolean {
            count = if (keyCode == KEYCODE_BACK || keyCode == KEYCODE_ESCAPE) count + 1 else 0
            return count >= needed
        }
    }
}
