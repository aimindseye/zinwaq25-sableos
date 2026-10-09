package org.sableos.reader.input

import org.sableos.reader.capability.ReaderCapabilities

/** Everything the key map needs to decide; built fresh for each key press by the active screen. */
data class KeyContext(
    val surface: ReaderSurface,
    val capabilities: ReaderCapabilities,
    val direction: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    /** True while a text field owns input: printable keys and Space must reach it untouched. */
    val editableFocused: Boolean = false,
)

/** Outcome of resolving a key: consume it as [Handled], or let the focused view receive it. */
sealed interface KeyResolution {
    data class Handled(val action: ReaderAction) : KeyResolution

    data object PassThrough : KeyResolution

    /** A repeated key-down of a one-shot action: swallowed, the action must not run a second time. */
    data object RepeatIgnored : KeyResolution
}

/**
 * Semantic keyboard map shared by every Reader engine (architecture: "Keyboard-first interaction").
 *
 * The keys are the Reader's surface subset of the canonical normalized-key contract (see [ReaderKey]); this map adds
 * only context-local navigation/activation after the focused editor has had first refusal. It invents no
 * MoveHome/MoveEnd (or Tab/Backspace) behaviour: those pass through to the editor/platform.
 *
 * - Escape/Back always resolve to [ReaderAction.DISMISS_OR_BACK] so the surface can close transient UI before leaving.
 * - While an editable field owns input nothing else is handled (text and Space stay with the field).
 * - Command-modified keys (Ctrl/Alt/Meta) are never ours.
 * - A capability the engine does not have is not handled at all: the key passes through, no fake control.
 * - One-shot repeat guard: a repeated key-down ([KeyInput.repeated]) never runs an action unless
 *   [repeatsWhileHeld] names it (line scrolling); it resolves to [KeyResolution.RepeatIgnored], including Escape.
 */
object ReaderKeyMap {
    fun resolve(input: KeyInput, context: KeyContext): KeyResolution {
        val action = when {
            input.key == ReaderKey.ESCAPE || input.key == ReaderKey.BACK -> ReaderAction.DISMISS_OR_BACK
            context.editableFocused || input.hasCommandModifier -> null
            else -> resolveVisible(input, context)
        }
        return when {
            action == null -> KeyResolution.PassThrough
            input.repeated && !action.repeatsWhileHeld -> KeyResolution.RepeatIgnored
            else -> KeyResolution.Handled(action)
        }
    }

    private fun resolveVisible(input: KeyInput, context: KeyContext): ReaderAction? = when (input.key) {
        ReaderKey.LEFT, ReaderKey.RIGHT -> horizontal(input.key, context)
        ReaderKey.UP, ReaderKey.DOWN -> vertical(input.key, context.surface)
        ReaderKey.PAGE_UP, ReaderKey.PAGE_DOWN -> pageKeys(input.key, context.surface)
        ReaderKey.SPACE -> space(input.shift, context.surface)
        ReaderKey.ACTIVATE, ReaderKey.MENU ->
            ReaderAction.TOGGLE_CHROME.takeUnless { context.surface == ReaderSurface.AUDIO }
        ReaderKey.CHARACTER -> letter(input.character, context.capabilities)
        ReaderKey.ESCAPE, ReaderKey.BACK -> ReaderAction.DISMISS_OR_BACK
    }

    private fun isNext(key: ReaderKey, direction: ReadingDirection): Boolean =
        (key == ReaderKey.RIGHT) == (direction == ReadingDirection.LEFT_TO_RIGHT)

    private fun horizontal(key: ReaderKey, context: KeyContext): ReaderAction? = when (context.surface) {
        ReaderSurface.EPUB_PAGED, ReaderSurface.PDF_PAGED, ReaderSurface.COMIC_PAGED ->
            if (isNext(key, context.direction)) ReaderAction.PAGE_NEXT else ReaderAction.PAGE_PREVIOUS
        ReaderSurface.AUDIO ->
            if (key == ReaderKey.RIGHT) ReaderAction.SEEK_FORWARD else ReaderAction.SEEK_BACKWARD
        ReaderSurface.EPUB_SCROLL, ReaderSurface.COMIC_VERTICAL_PAGER, ReaderSurface.COMIC_CONTINUOUS -> null
    }

    private fun vertical(key: ReaderKey, surface: ReaderSurface): ReaderAction? = when (surface) {
        ReaderSurface.EPUB_SCROLL, ReaderSurface.COMIC_CONTINUOUS ->
            if (key == ReaderKey.DOWN) ReaderAction.SCROLL_DOWN else ReaderAction.SCROLL_UP
        ReaderSurface.COMIC_VERTICAL_PAGER ->
            if (key == ReaderKey.DOWN) ReaderAction.PAGE_NEXT else ReaderAction.PAGE_PREVIOUS
        ReaderSurface.EPUB_PAGED, ReaderSurface.PDF_PAGED, ReaderSurface.COMIC_PAGED, ReaderSurface.AUDIO -> null
    }

    private fun pageKeys(key: ReaderKey, surface: ReaderSurface): ReaderAction {
        val forward = key == ReaderKey.PAGE_DOWN
        return when (surface) {
            ReaderSurface.EPUB_SCROLL, ReaderSurface.COMIC_CONTINUOUS ->
                if (forward) ReaderAction.SCROLL_PAGE_DOWN else ReaderAction.SCROLL_PAGE_UP
            ReaderSurface.AUDIO -> if (forward) ReaderAction.CHAPTER_NEXT else ReaderAction.CHAPTER_PREVIOUS
            else -> if (forward) ReaderAction.PAGE_NEXT else ReaderAction.PAGE_PREVIOUS
        }
    }

    private fun space(shift: Boolean, surface: ReaderSurface): ReaderAction =
        if (surface == ReaderSurface.AUDIO) {
            ReaderAction.PLAY_PAUSE
        } else if (shift) {
            ReaderAction.REVERSE
        } else {
            ReaderAction.ADVANCE
        }

    private fun letter(character: Char?, capabilities: ReaderCapabilities): ReaderAction? =
        when (character?.lowercaseChar()) {
            'b' -> ReaderAction.TOGGLE_BOOKMARK.takeIf { capabilities.bookmarks }
            't' -> ReaderAction.OPEN_CONTENTS.takeIf { capabilities.contents }
            'f' -> ReaderAction.OPEN_FIND.takeIf { capabilities.textSearch }
            'a' -> ReaderAction.OPEN_APPEARANCE.takeIf { capabilities.appearance }
            else -> null
        }
}
