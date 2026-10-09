package org.sableos.titan2.keyboard.core

/**
 * Pure-Kotlin keyboard model. No Android imports, so every rule is unit-testable on a JVM.
 * Sable-owned: not derived from Pastiera/Plektra (GPL).
 */

enum class Mod { Shift, Alt, Sym }

sealed interface Key {
    data class Letter(val c: Char) : Key // 'a'..'z', lowercase
    data class Digit(val c: Char) : Key
    data class Punct(val c: Char) : Key // physical punctuation key, unshifted char
    data class Modifier(val m: Mod) : Key
    data object Ctrl : Key
    data object Space : Key
    data object Enter : Key
    data object Backspace : Key
    data object Delete : Key
    data object Tab : Key
    data object Escape : Key
    data object Up : Key
    data object Down : Key
    data object Left : Key
    data object Right : Key
    data object Home : Key
    data object End : Key
    data object PageUp : Key
    data object PageDown : Key
    data class Other(val code: Int) : Key
}

data class KeyEv(
    val key: Key,
    val down: Boolean,
    val time: Long,
    /** Android repeatCount: 0 first press, 1 = long-press threshold reached, >1 held repeats. */
    val repeat: Int = 0,
    val ctrlHeld: Boolean = false,
    /**
     * The character the DEVICE's own key character map (.kcm) produces for this key with Alt / Sym held, when it
     * differs from the plain character. Supplied by the Android glue via KeyEvent.getUnicodeChar(meta). Preferred
     * over Sable's provisional tables, so Alt/Sym follow the real printed legends without capturing them by hand
     * (Titan 2 stock: Alt+Q gives '0'; Sym has no kcm entry, so it falls back).
     */
    val nativeAlt: Char? = null,
    val nativeSym: Char? = null
)

enum class InputClass { Text, Number, Phone, Password, NumberPassword, None }

/** What the IME knows about the focused field. `before` is the text before the cursor (a few chars are enough). */
data class FieldCtx(
    val inputClass: InputClass = InputClass.Text,
    val before: String = "",
    val autoCapAllowed: Boolean = true
) {
    val editable: Boolean get() = inputClass != InputClass.None
}

sealed interface Out {
    /** Let Android handle the key normally. */
    data object Pass : Out

    /** Swallow the key. */
    data object Consume : Out
    data class Commit(val text: String) : Out
    data class DeleteBefore(val n: Int) : Out

    /** Send a navigation/edit key through the input connection (down+up). */
    data class Send(val key: Key, val shift: Boolean = false, val ctrl: Boolean = false) : Out
    data class NavMode(val on: Boolean) : Out
    data object ToggleSoftKeyboard : Out

    /** Switch to the next typing language (IME subtype). */
    data object NextLanguage : Out

    /**
     * A plain letter key for a language that composes (pinyin, kana, Hangul); [shift] is the Shift state at the
     * press.
     */
    data class Compose(val c: Char, val shift: Boolean) : Out
}

data class KeyboardConfig(
    val autoCap: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val longPressAlt: Boolean = true,
    /** Letters become their Alt digit in numeric/phone fields so pairing codes and PINs work with no Alt/Sym key. */
    val numericAutoAlt: Boolean = true,
    val navModeEnabled: Boolean = true,
    /** Alt+Space toggles Nav mode (a profile may bind a captured device key instead). */
    val altSpaceTogglesNav: Boolean = true,
    val doubleTapMs: Long = 350,
    /**
     * One-shot modifiers expire after this many ms; 0 = never (default, matches predictable physical-keyboard
     * behaviour).
     */
    val oneShotTimeoutMs: Long = 0,
    val holdCancelMs: Long = 600
)
