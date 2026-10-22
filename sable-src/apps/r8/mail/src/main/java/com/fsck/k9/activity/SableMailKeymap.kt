package com.fsck.k9.activity

import android.view.KeyEvent

/**
 * Sable Mail single-key commands (MAIL_CALENDAR_PRODUCTIVITY_UX.md, "Shared keyboard contract").
 *
 * Copied into the Thunderbird checkout by apps/r8/mail/apply_sable_flavor.py. It replaces the
 * upstream K-9 hotkeys, whose J/K/N/P directions and S (select) disagree with the Sable contract.
 * The Q25 keyboard has Alt, Shift, Sym and letters but no Fn or Ctrl, so symbols are matched on the
 * typed character and Ctrl+Enter send is also Alt+Enter.
 *
 * V, L, E and A follow the approved artifact (sable-mail-calendar-titan2.svg): V archive, L reply all,
 * E expand the collapsed quoted text, A the message's attachments. The design text's table gives V to
 * "view/filter mode"; the artifact wins, so the sort/view cycle stays on O only.
 */
enum class SableMailCommand {
    COMPOSE,
    TOP,
    NEXT,
    PREVIOUS,
    REPLY,
    REPLY_ALL,
    FORWARD,
    ARCHIVE,
    EXPAND_QUOTE,
    ATTACHMENTS,
    DELETE,
    SEARCH,
    VIEW_MODE,
    PAGE_DOWN,
    PAGE_UP,
    BACK,

    // Upstream K-9 actions kept on keys the Sable contract leaves free.
    SELECT,
    TOGGLE_FLAG,
    MOVE,
    COPY,
    TOGGLE_READ,
    REVERSE_SORT,
}

/** Compose-screen keys: Ctrl+Enter (Alt+Enter on the Q25) sends, Esc cancels. */
enum class SableComposeCommand {
    SEND,
    CANCEL,
}

/** A key-down reduced to what the keymap needs, so the mapping is testable on the JVM. */
data class SableMailKeyPress(
    val keyCode: Int,
    /** The character the key produces with the current meta state, or null. */
    val char: Char? = null,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val ctrl: Boolean = false,
    val repeatCount: Int = 0,
) {
    companion object {
        fun from(event: KeyEvent): SableMailKeyPress {
            val unicode = event.getUnicodeChar(event.metaState)
            return SableMailKeyPress(
                keyCode = event.keyCode,
                char = if (unicode > 0) unicode.toChar() else null,
                shift = event.isShiftPressed,
                alt = event.isAltPressed,
                ctrl = event.isCtrlPressed || event.isMetaPressed,
                repeatCount = event.repeatCount,
            )
        }
    }
}

/** How the message list moves its keyboard focus. */
enum class SableListMove {
    NEXT,
    PREVIOUS,
    PAGE_DOWN,
    PAGE_UP,
    TOP,
}

object SableMailKeymap {
    /**
     * Maps one key-down in the message list or reader to a command, or null when the key is left to
     * the focused view (and, for [upstreamFallbackAllowed] keys, to upstream handling).
     *
     * @param textInputActive a text field (search) has focus: TEXT_INPUT_ALWAYS_WINS, only Esc is taken.
     */
    fun command(
        press: SableMailKeyPress,
        textInputActive: Boolean,
    ): SableMailCommand? {
        val command =
            when {
                press.keyCode == KeyEvent.KEYCODE_ESCAPE -> SableMailCommand.BACK
                textInputActive || press.ctrl -> null
                // On the Q25 `/` is typed with Alt or Sym, so it is matched on the character.
                press.char == '/' -> SableMailCommand.SEARCH
                // Any other Alt chord types a symbol, which is not a command.
                press.alt -> null
                press.keyCode == KeyEvent.KEYCODE_SPACE ->
                    if (press.shift) SableMailCommand.PAGE_UP else SableMailCommand.PAGE_DOWN
                press.keyCode == KeyEvent.KEYCODE_DEL || press.keyCode == KeyEvent.KEYCODE_FORWARD_DEL ->
                    SableMailCommand.DELETE.takeIf { !press.shift }
                press.shift -> null
                else -> LETTERS[press.keyCode]
            }
        // Holding a key repeats only movement and paging; a held D must not delete twice.
        return command?.takeIf { press.repeatCount == 0 || it in REPEATABLE }
    }

    /**
     * Keys the Sable map does not own that may still reach the upstream handler: volume-key and
     * Left/Right message navigation in the reader. Upstream letter hotkeys never run.
     */
    fun upstreamFallbackAllowed(
        press: SableMailKeyPress,
        textInputActive: Boolean,
    ): Boolean =
        !textInputActive &&
            !press.ctrl &&
            !press.alt &&
            !press.shift &&
            press.keyCode in UPSTREAM_KEYS

    /** Compose screen: Ctrl+Enter or Alt+Enter sends (MAIL_CTRL_ENTER_SEND), Esc cancels. */
    fun composeCommand(press: SableMailKeyPress): SableComposeCommand? =
        when {
            press.repeatCount > 0 -> null
            press.keyCode == KeyEvent.KEYCODE_ESCAPE -> SableComposeCommand.CANCEL
            (press.keyCode == KeyEvent.KEYCODE_ENTER || press.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
                (press.ctrl || press.alt) -> SableComposeCommand.SEND
            else -> null
        }

    /**
     * Adapter position the message list focuses for [move], or null for an empty list.
     *
     * @param focused the focused position, or -1 when no row has focus.
     */
    fun listTarget(
        move: SableListMove,
        focused: Int,
        firstVisible: Int,
        lastVisible: Int,
        itemCount: Int,
    ): Int? {
        if (itemCount <= 0) return null
        val anchor = if (focused >= 0) focused else firstVisible.coerceAtLeast(0)
        val page = (lastVisible - firstVisible).coerceAtLeast(1)
        val target =
            when (move) {
                SableListMove.TOP -> 0
                SableListMove.NEXT -> if (focused >= 0) focused + 1 else anchor
                SableListMove.PREVIOUS -> if (focused >= 0) focused - 1 else anchor
                SableListMove.PAGE_DOWN -> anchor + page
                SableListMove.PAGE_UP -> anchor - page
            }
        return target.coerceIn(0, itemCount - 1)
    }

    private val LETTERS =
        mapOf(
            KeyEvent.KEYCODE_C to SableMailCommand.COMPOSE,
            KeyEvent.KEYCODE_T to SableMailCommand.TOP,
            KeyEvent.KEYCODE_J to SableMailCommand.NEXT,
            KeyEvent.KEYCODE_N to SableMailCommand.NEXT,
            KeyEvent.KEYCODE_K to SableMailCommand.PREVIOUS,
            KeyEvent.KEYCODE_P to SableMailCommand.PREVIOUS,
            KeyEvent.KEYCODE_R to SableMailCommand.REPLY,
            KeyEvent.KEYCODE_L to SableMailCommand.REPLY_ALL,
            KeyEvent.KEYCODE_F to SableMailCommand.FORWARD,
            KeyEvent.KEYCODE_D to SableMailCommand.DELETE,
            KeyEvent.KEYCODE_S to SableMailCommand.SEARCH,
            KeyEvent.KEYCODE_V to SableMailCommand.ARCHIVE,
            KeyEvent.KEYCODE_E to SableMailCommand.EXPAND_QUOTE,
            KeyEvent.KEYCODE_A to SableMailCommand.ATTACHMENTS,
            KeyEvent.KEYCODE_O to SableMailCommand.VIEW_MODE,
            KeyEvent.KEYCODE_X to SableMailCommand.SELECT,
            KeyEvent.KEYCODE_G to SableMailCommand.TOGGLE_FLAG,
            KeyEvent.KEYCODE_M to SableMailCommand.MOVE,
            KeyEvent.KEYCODE_Y to SableMailCommand.COPY,
            KeyEvent.KEYCODE_Z to SableMailCommand.TOGGLE_READ,
            KeyEvent.KEYCODE_I to SableMailCommand.REVERSE_SORT,
        )

    private val REPEATABLE =
        setOf(
            SableMailCommand.NEXT,
            SableMailCommand.PREVIOUS,
            SableMailCommand.PAGE_DOWN,
            SableMailCommand.PAGE_UP,
        )

    private val UPSTREAM_KEYS =
        setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
}

/**
 * MAIL_SPLIT_PANE on a square keyboard screen: thread list left, reader right. On a near-square
 * display at least [MIN_SPLIT_WIDTH_DP] wide (the Q25 is 720x720 at 193 dpi, about 597 dp) the split
 * view is used whatever the split-view setting says; elsewhere the setting decides as upstream.
 */
object SableMailLayout {
    const val MIN_SPLIT_WIDTH_DP = 560
    private const val MAX_SQUARE_RATIO = 1.25f

    fun forceSplitView(
        widthDp: Int,
        heightDp: Int,
    ): Boolean {
        if (widthDp < MIN_SPLIT_WIDTH_DP || heightDp <= 0) return false
        val ratio = maxOf(widthDp, heightDp).toFloat() / minOf(widthDp, heightDp).toFloat()
        return ratio <= MAX_SQUARE_RATIO
    }

    /** The Bottom Command Dock prompt and hint lines (artifact states 1 and 2). */
    const val DOCK_PROMPT = "> mail search / command…"
    const val DOCK_LIST_HINT = "[C Compose] [R Reply] [/ Search] [J/K Move] [T Latest]"
    const val DOCK_READER_HINT = "Reader: L All · F Fwd · V Archive · D Del · E Quote · A Attach"
}

/**
 * MAIL_QUOTED_TEXT_COLLAPSED_BY_DEFAULT: the reader shows quoted history collapsed (a one-line marker
 * per quote) until E expands it. Only CSS is added; JavaScript stays off and remote content stays
 * blocked as before.
 */
object SableMailQuotes {
    const val MARKER = "Quoted text hidden · E shows it"

    fun html(
        body: String,
        expanded: Boolean,
    ): String = if (expanded) body else COLLAPSED_STYLE + body

    private const val COLLAPSED_STYLE =
        "<style>" +
            "blockquote{font-size:0 !important;border-left:0 !important;padding:0 !important;margin:0 !important}" +
            "blockquote>*{display:none !important}" +
            "blockquote::before{content:\"$MARKER\";font-size:13px;opacity:.7;font-style:italic}" +
            "blockquote blockquote::before{content:none}" +
            "</style>"
}
