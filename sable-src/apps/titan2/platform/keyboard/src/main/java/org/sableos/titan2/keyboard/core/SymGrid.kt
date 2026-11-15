package org.sableos.titan2.keyboard.core

/**
 * The BlackBerry-style symbol grid: tap Sym once and a chart of symbols opens, each labelled with the letter key
 * that types it. Pressing that letter types the symbol and closes the chart, Sym again shows the next page, and Back
 * closes it. Pure, so it is unit-tested on a JVM; the constants mirror android.view.KeyEvent (stable public ABI).
 */
object SymGrid {
    const val KEYCODE_BACK = 4
    const val KEYCODE_A = 29
    const val KEYCODE_Z = 54
    const val KEYCODE_ALT_RIGHT = 58
    const val KEYCODE_SYM = 63
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_K = 39
    const val TAP_MAX_MS = 350L

    /** The letter keys in the order of the physical rows; symbol i of a page sits on letter i. */
    const val LETTERS = "qwertyuiopasdfghjklzxcvbnm"

    private const val ALPHABET = 26

    /** Pages of exactly 26 symbols, one per letter key. Page 1 holds the programmer's symbols. */
    val PAGES: List<List<String>> = listOf(
        "[ ] { } ( ) < > \\ | / ~ ` ^ _ - + = * & % # @ $ ! ?",
        "€ £ ¥ ¢ © ® ™ ° ± × ÷ § ¶ • … « » ¿ ¡ “ ” ‘ ’ – — µ",
        "← → ↑ ↓ ≤ ≥ ≠ ≈ ∞ √ ∑ π Ω Δ ½ ¼ ¾ ¹ ² ³ ✓ ♥ ☺ ₹ : ;"
    ).map { it.split(' ') }

    /** True for the key that is Sym on the device: Sym itself, or Right Alt on the Q25. */
    fun isSymKey(keyCode: Int): Boolean = keyCode == KEYCODE_SYM || keyCode == KEYCODE_ALT_RIGHT

    /** A Sym press counts as a tap, not a held modifier, when released quickly with no key between. */
    fun isTap(heldMs: Long, chorded: Boolean): Boolean = !chorded && heldMs in 0..TAP_MAX_MS

    /** Alt+Shift+K shows or hides the on-screen keyboard (K for keyboard; Alt+Shift+R and +V are taken). */
    fun togglesSoftKeyboard(
        keyCode: Int,
        alt: Boolean,
        shift: Boolean,
        ctrlOrMeta: Boolean
    ): Boolean = keyCode == KEYCODE_K && alt && shift && !ctrlOrMeta

    /** The letter a key code stands for (A to Z), or null. */
    fun letterOf(keyCode: Int): Char? =
        if (keyCode in KEYCODE_A..KEYCODE_Z) 'a' + (keyCode - KEYCODE_A) else null

    /** The symbol typed by [letter] on [page], or null when it is not a letter. */
    fun symbolFor(page: Int, letter: Char): String? {
        val i = LETTERS.indexOf(letter.lowercaseChar())
        return if (i < 0) null else PAGES[page.mod(PAGES.size)][i]
    }

    fun nextPage(page: Int): Int = (page + 1) % PAGES.size

    /** What a key does while the grid is open. */
    sealed interface Action {
        data class Pick(val symbol: String) : Action
        data object NextPage : Action
        data object Close : Action
        data object Ignore : Action
        data object ClosePass : Action
    }

    fun action(keyCode: Int, page: Int): Action {
        val symbol = letterOf(keyCode)?.let { symbolFor(page, it) }
        return when {
            symbol != null -> Action.Pick(symbol)
            isSymKey(keyCode) -> Action.NextPage
            keyCode == KEYCODE_BACK || keyCode == KEYCODE_ESCAPE -> Action.Close
            isModifier(keyCode) -> Action.Ignore
            else -> Action.ClosePass
        }
    }

    private fun isModifier(keyCode: Int) = keyCode in MODIFIERS

    private val MODIFIERS = setOf(57, 59, 60)

    /** Chips for the strip: "Sym 1/3", then "q [", "w ]" and so on. Chip i > 0 is letter i - 1 of the page. */
    fun chips(page: Int): List<String> {
        val p = page.mod(PAGES.size)
        return listOf("Sym ${p + 1}/${PAGES.size}") +
            PAGES[p].mapIndexed { i, s -> "${LETTERS[i]} $s" }
    }

    /** The symbol a tapped chip stands for, or null for the header chip. */
    fun symbolForChip(chip: Int, page: Int): String? =
        if (chip <= 0 || chip > ALPHABET) null else PAGES[page.mod(PAGES.size)][chip - 1]
}
