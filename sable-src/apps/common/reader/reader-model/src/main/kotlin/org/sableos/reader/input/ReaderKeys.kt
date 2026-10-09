package org.sableos.reader.input

/**
 * Reader keys: the surface subset of the canonical normalized-key vocabulary
 * (`platform_sable` docs/NORMALIZED_KEY_INPUT_CONTRACT.md @
 * 80e3097bdef4cd2d5cbfd8ebde16390c29767918: Up, Down, Left, Right, Activate, Space, Back,
 * Escape, PageUp, PageDown, Printable ...). This is a consumer of that contract, not a second platform vocabulary:
 * [ACTIVATE] is the canonical "Activate" (D-pad centre, Enter, keypad Enter) and [CHARACTER] is "Printable".
 * Kinds the Reader has no behaviour for (Tab, Backspace, MoveHome, MoveEnd, Other) are deliberately not modelled and
 * are never mapped, so the focused editor/platform keeps them. The Android layer maps `KeyEvent` codes to these
 * (no scan codes, no device branching).
 */
enum class ReaderKey {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    PAGE_UP,
    PAGE_DOWN,
    SPACE,
    ACTIVATE,
    MENU,
    ESCAPE,
    BACK,
    CHARACTER,
}

/** One key press with modifiers. [character] is set only for [ReaderKey.CHARACTER]. */
data class KeyInput(
    val key: ReaderKey,
    val character: Char? = null,
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    /** True for an auto-repeated key-down (Android `KeyEvent.repeatCount > 0`); false for the initial press. */
    val repeated: Boolean = false,
) {
    val hasCommandModifier: Boolean get() = ctrl || alt || meta
}

/** Direction in which a paged publication advances when the user moves to the physical right. */
enum class ReadingDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

/** The reading surface that currently owns the keyboard. */
enum class ReaderSurface {
    EPUB_PAGED,
    EPUB_SCROLL,
    PDF_PAGED,
    COMIC_PAGED,
    COMIC_VERTICAL_PAGER,
    COMIC_CONTINUOUS,
    AUDIO,
}

/**
 * Semantic actions. Engines implement these; key handling never talks to an engine directly.
 *
 * Repeat semantics (canonical `ONE_SHOT_REPEAT_GUARD`): one press = one state transition for every action except the
 * ones [repeatsWhileHeld] explicitly names. A repeated key-down of any other action is ignored by [ReaderKeyMap].
 */
enum class ReaderAction {
    PAGE_NEXT,
    PAGE_PREVIOUS,
    ADVANCE,
    REVERSE,
    SCROLL_UP,
    SCROLL_DOWN,
    SCROLL_PAGE_UP,
    SCROLL_PAGE_DOWN,
    TOGGLE_BOOKMARK,
    OPEN_CONTENTS,
    OPEN_FIND,
    OPEN_APPEARANCE,
    TOGGLE_CHROME,
    DISMISS_OR_BACK,
    PLAY_PAUSE,
    SEEK_BACKWARD,
    SEEK_FORWARD,
    CHAPTER_PREVIOUS,
    CHAPTER_NEXT,
}

/**
 * The only actions the Reader defines as continuous: holding the key scrolls line by line. Everything else (page
 * turns, chapter changes, seeks, play/pause, bookmark, contents, find, appearance, chrome toggle, dismiss) is one
 * press = one transition, so a repeated key-down must never run it again. Android generating repeated key-downs does
 * not by itself define new repeat behaviour.
 */
val ReaderAction.repeatsWhileHeld: Boolean
    get() = this == ReaderAction.SCROLL_UP || this == ReaderAction.SCROLL_DOWN
