package org.sableos.reader.comic

import org.sableos.reader.capability.ReaderCapabilities
import org.sableos.reader.input.KeyContext
import org.sableos.reader.input.KeyInput
import org.sableos.reader.input.KeyResolution
import org.sableos.reader.input.ReaderAction
import org.sableos.reader.input.ReaderKey
import org.sableos.reader.input.ReaderKeyMap
import org.sableos.reader.input.ReaderSurface
import org.sableos.reader.input.ReadingDirection

/** What the comic screen must do for a key press. */
enum class ComicKeyCommand {
    NEXT_PAGE,
    PREVIOUS_PAGE,
    SCROLL_LINE_DOWN,
    SCROLL_LINE_UP,
    SCROLL_PAGE_DOWN,
    SCROLL_PAGE_UP,
    TOGGLE_BOOKMARK,
    OPEN_CONTENTS,
    OPEN_APPEARANCE,
    TOGGLE_CHROME,
    ESCAPE,
}

/**
 * Comic keyboard policy layered on [ReaderKeyMap]; pure, so every mode is JVM-tested. Paged modes follow the
 * reading direction (in right-to-left, Left is "next"), the vertical pager pages with Up/Down, and the continuous
 * modes scroll. Only Escape is handled while an overlay is open or a text field owns input.
 */
object ComicKeyPolicy {
    fun surfaceOf(mode: ComicReadingMode): ReaderSurface = when (mode) {
        ComicReadingMode.PAGED_LTR, ComicReadingMode.PAGED_RTL -> ReaderSurface.COMIC_PAGED
        ComicReadingMode.VERTICAL_PAGER -> ReaderSurface.COMIC_VERTICAL_PAGER
        ComicReadingMode.CONTINUOUS_VERTICAL, ComicReadingMode.WEBTOON -> ReaderSurface.COMIC_CONTINUOUS
    }

    fun decide(
        input: KeyInput,
        mode: ComicReadingMode,
        editableFocused: Boolean,
        overlayOpen: Boolean,
    ): ComicKeyCommand? {
        val context = KeyContext(
            surface = surfaceOf(mode),
            capabilities = ReaderCapabilities.COMIC,
            direction = if (mode.isRightToLeft) ReadingDirection.RIGHT_TO_LEFT else ReadingDirection.LEFT_TO_RIGHT,
            editableFocused = editableFocused,
        )
        val action = (ReaderKeyMap.resolve(input, context) as? KeyResolution.Handled)?.action
        return when {
            action == null || input.key == ReaderKey.BACK -> null
            action == ReaderAction.DISMISS_OR_BACK -> ComicKeyCommand.ESCAPE
            overlayOpen -> null
            else -> command(action, mode)
        }
    }

    private fun command(action: ReaderAction, mode: ComicReadingMode): ComicKeyCommand? =
        movement(action, mode) ?: panel(action)

    private fun movement(action: ReaderAction, mode: ComicReadingMode): ComicKeyCommand? = when (action) {
        ReaderAction.PAGE_NEXT -> ComicKeyCommand.NEXT_PAGE
        ReaderAction.PAGE_PREVIOUS -> ComicKeyCommand.PREVIOUS_PAGE
        ReaderAction.ADVANCE -> if (mode.isContinuous) ComicKeyCommand.SCROLL_PAGE_DOWN else ComicKeyCommand.NEXT_PAGE
        ReaderAction.REVERSE -> if (mode.isContinuous) ComicKeyCommand.SCROLL_PAGE_UP else ComicKeyCommand.PREVIOUS_PAGE
        ReaderAction.SCROLL_DOWN -> ComicKeyCommand.SCROLL_LINE_DOWN
        ReaderAction.SCROLL_UP -> ComicKeyCommand.SCROLL_LINE_UP
        ReaderAction.SCROLL_PAGE_DOWN -> ComicKeyCommand.SCROLL_PAGE_DOWN
        ReaderAction.SCROLL_PAGE_UP -> ComicKeyCommand.SCROLL_PAGE_UP
        else -> null
    }

    private fun panel(action: ReaderAction): ComicKeyCommand? = when (action) {
        ReaderAction.TOGGLE_BOOKMARK -> ComicKeyCommand.TOGGLE_BOOKMARK
        ReaderAction.OPEN_CONTENTS -> ComicKeyCommand.OPEN_CONTENTS
        ReaderAction.OPEN_APPEARANCE -> ComicKeyCommand.OPEN_APPEARANCE
        ReaderAction.TOGGLE_CHROME -> ComicKeyCommand.TOGGLE_CHROME
        else -> null
    }
}
