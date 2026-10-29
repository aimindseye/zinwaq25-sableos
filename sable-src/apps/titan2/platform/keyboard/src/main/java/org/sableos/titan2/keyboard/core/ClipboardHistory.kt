package org.sableos.titan2.keyboard.core

/**
 * Clipboard history (opt-in, off by default). Pure Kotlin so every rule is JVM-tested. Entries live
 * only in memory: nothing is written to storage, so a reboot or a restart of the keyboard empties
 * it, and turning the setting off clears it. Copies the system marks sensitive are never recorded.
 * The interaction is a behaviour reference only; no Pastiera code is used.
 */
class ClipboardHistory(private val maxEntries: Int = MAX_ENTRIES) {
    private val items = ArrayList<String>()

    /** Newest first. */
    val entries: List<String> get() = items.toList()

    /**
     * Records a copy. Blank text, sensitive clips and text over [MAX_CHARS] are skipped; copying
     * text already in the history moves it to the top. Returns true when the history changed.
     */
    fun add(text: CharSequence?, sensitive: Boolean): Boolean {
        val s = text?.toString().orEmpty()
        val wanted =
            !sensitive && s.isNotBlank() && s.length <= MAX_CHARS && items.firstOrNull() != s
        if (wanted) {
            items.remove(s)
            items.add(0, s)
            while (items.size > maxEntries) items.removeAt(items.lastIndex)
        }
        return wanted
    }

    fun remove(index: Int) {
        if (index in items.indices) items.removeAt(index)
    }

    fun clear() = items.clear()

    companion object {
        const val MAX_ENTRIES = 20

        /** Longer copies (a whole document) are left to the system clipboard. */
        const val MAX_CHARS = 10_000

        const val PREVIEW_CHARS = 24

        /** One line of at most [max] characters: whitespace runs collapse to a space. */
        fun preview(text: String, max: Int = PREVIEW_CHARS): String {
            val line = text.trim().replace(WHITESPACE, " ")
            return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
        }

        private val WHITESPACE = Regex("\\s+")
    }
}

/** What a key does while the history picker is open. */
enum class ClipAction { Next, Previous, Paste, Remove, Close, Ignore, CloseAndPass }

/**
 * The history picker shown in the keyboard strip. Hold Alt+Shift and press V to open it (held keys
 * only: Alt+letter types a digit or symbol and Shift+letter a capital, and the Q25 has no Ctrl).
 * The constants mirror android.view.KeyEvent (stable public ABI).
 */
object ClipPicker {
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_BACK = 4
    const val KEYCODE_V = 50
    const val KEYCODE_SPACE = 62
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_NUMPAD_ENTER = 160
    private val MODIFIERS = setOf(57, 58, 59, 60, 63) // Alt, Shift (left, right), Sym

    /** Entries shown after the position chip; the strip is one row on a 411 dp screen. */
    const val VISIBLE = 3

    fun opens(keyCode: Int, altHeld: Boolean, shiftHeld: Boolean, ctrlOrMeta: Boolean): Boolean =
        keyCode == KEYCODE_V && altHeld && shiftHeld && !ctrlOrMeta

    /**
     * Space and the trackpad move, Enter or a trackpad click pastes, Backspace removes the entry,
     * Back closes. Any other key closes the picker and types as usual.
     */
    fun action(keyCode: Int, shift: Boolean): ClipAction = when (keyCode) {
        KEYCODE_SPACE -> if (shift) ClipAction.Previous else ClipAction.Next
        KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_DOWN -> ClipAction.Next
        KEYCODE_DPAD_LEFT, KEYCODE_DPAD_UP -> ClipAction.Previous
        KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER, KEYCODE_DPAD_CENTER -> ClipAction.Paste
        KEYCODE_DEL -> ClipAction.Remove
        KEYCODE_BACK, KEYCODE_ESCAPE -> ClipAction.Close
        in MODIFIERS -> ClipAction.Ignore
        else -> ClipAction.CloseAndPass
    }

    /** [selected] moved by [step], wrapping round the [count] entries. */
    fun move(selected: Int, step: Int, count: Int): Int =
        if (count <= 0) 0 else ((selected + step) % count + count) % count

    /**
     * Chips for the strip: "2/7", then the selected entry and the ones after it. Index 0 of the
     * result is the position chip; chip i > 0 is entry [selected] + i - 1.
     */
    fun chips(entries: List<String>, selected: Int): List<String> {
        if (entries.isEmpty()) return listOf("Clipboard empty")
        val from = selected.coerceIn(0, entries.lastIndex)
        val shown = entries.drop(from).take(VISIBLE).map { ClipboardHistory.preview(it) }
        return listOf("${from + 1}/${entries.size}") + shown
    }

    /** The entry a tapped chip stands for, or null for the position chip. */
    fun entryForChip(chip: Int, selected: Int): Int? = if (chip <= 0) null else selected + chip - 1
}
