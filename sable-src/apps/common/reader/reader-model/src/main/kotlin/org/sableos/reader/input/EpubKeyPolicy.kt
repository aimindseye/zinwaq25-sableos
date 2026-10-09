package org.sableos.reader.input

import org.sableos.reader.capability.ReaderCapabilities

/** What the EPUB screen must do for a key press; Left/Right stay apart from forward/back so RTL books turn right. */
enum class EpubKeyCommand {
    TURN_LEFT,
    TURN_RIGHT,
    FORWARD,
    BACKWARD,
    TOGGLE_BOOKMARK,
    OPEN_CONTENTS,
    OPEN_FIND,
    OPEN_APPEARANCE,
    TOGGLE_CHROME,
    ESCAPE,
}

/**
 * EPUB keyboard policy layered on [ReaderKeyMap]. Pure so it is JVM-testable.
 *
 * - Escape is the only key handled while an overlay is open or a text field owns input (the screen's back handling
 *   closes the overlay first); the hardware Back key is left to the system back dispatcher.
 * - Scroll-mode arrows, Page and Space keys are not handled: the web view scrolls itself, nothing is faked.
 * - Left/Right turn by physical side so the navigator applies the publication's reading progression.
 */
object EpubKeyPolicy {
    fun decide(input: KeyInput, scrollMode: Boolean, editableFocused: Boolean, overlayOpen: Boolean): EpubKeyCommand? {
        val context = KeyContext(
            surface = if (scrollMode) ReaderSurface.EPUB_SCROLL else ReaderSurface.EPUB_PAGED,
            capabilities = ReaderCapabilities.EPUB,
            editableFocused = editableFocused,
        )
        val action = (ReaderKeyMap.resolve(input, context) as? KeyResolution.Handled)?.action
        return when {
            action == null || input.key == ReaderKey.BACK -> null
            action == ReaderAction.DISMISS_OR_BACK -> EpubKeyCommand.ESCAPE
            overlayOpen -> null
            else -> navigationOrPanel(input, action)
        }
    }

    private fun navigationOrPanel(input: KeyInput, action: ReaderAction): EpubKeyCommand? = when (action) {
        ReaderAction.PAGE_NEXT, ReaderAction.PAGE_PREVIOUS -> when (input.key) {
            ReaderKey.LEFT -> EpubKeyCommand.TURN_LEFT
            ReaderKey.RIGHT -> EpubKeyCommand.TURN_RIGHT
            else -> if (action == ReaderAction.PAGE_NEXT) EpubKeyCommand.FORWARD else EpubKeyCommand.BACKWARD
        }
        ReaderAction.ADVANCE -> EpubKeyCommand.FORWARD
        ReaderAction.REVERSE -> EpubKeyCommand.BACKWARD
        ReaderAction.TOGGLE_BOOKMARK -> EpubKeyCommand.TOGGLE_BOOKMARK
        ReaderAction.OPEN_CONTENTS -> EpubKeyCommand.OPEN_CONTENTS
        ReaderAction.OPEN_FIND -> EpubKeyCommand.OPEN_FIND
        ReaderAction.OPEN_APPEARANCE -> EpubKeyCommand.OPEN_APPEARANCE
        ReaderAction.TOGGLE_CHROME -> EpubKeyCommand.TOGGLE_CHROME
        else -> null
    }
}
