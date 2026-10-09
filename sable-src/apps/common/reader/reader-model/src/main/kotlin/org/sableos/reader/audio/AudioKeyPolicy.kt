package org.sableos.reader.audio

import org.sableos.reader.capability.ReaderCapabilities
import org.sableos.reader.input.KeyContext
import org.sableos.reader.input.KeyInput
import org.sableos.reader.input.KeyResolution
import org.sableos.reader.input.ReaderAction
import org.sableos.reader.input.ReaderKey
import org.sableos.reader.input.ReaderKeyMap
import org.sableos.reader.input.ReaderSurface

/** What the audiobook screen must do for a key press. */
enum class AudioKeyCommand {
    PLAY_PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
    PREVIOUS_CHAPTER,
    NEXT_CHAPTER,
    TOGGLE_BOOKMARK,
    OPEN_CHAPTERS,
    OPEN_SETTINGS,
    ESCAPE,
}

/**
 * Audiobook keyboard policy layered on [ReaderKeyMap]: Space plays or pauses, Left/Right seek by the title's skip
 * interval, PageUp/PageDown change chapter, `B` bookmarks the position, `T` lists chapters, `A` opens playback
 * settings. Only Escape is handled while an overlay is open or a text field owns input. Volume and media keys are
 * never touched: they stay with the system (and with the media session).
 */
object AudioKeyPolicy {
    fun decide(input: KeyInput, editableFocused: Boolean, overlayOpen: Boolean): AudioKeyCommand? {
        val context = KeyContext(ReaderSurface.AUDIO, ReaderCapabilities.AUDIOBOOK, editableFocused = editableFocused)
        val action = (ReaderKeyMap.resolve(input, context) as? KeyResolution.Handled)?.action
        return when {
            action == null || input.key == ReaderKey.BACK -> null
            action == ReaderAction.DISMISS_OR_BACK -> AudioKeyCommand.ESCAPE
            overlayOpen -> null
            else -> command(action)
        }
    }

    private fun command(action: ReaderAction): AudioKeyCommand? = when (action) {
        ReaderAction.PLAY_PAUSE -> AudioKeyCommand.PLAY_PAUSE
        ReaderAction.SEEK_BACKWARD -> AudioKeyCommand.SEEK_BACK
        ReaderAction.SEEK_FORWARD -> AudioKeyCommand.SEEK_FORWARD
        ReaderAction.CHAPTER_PREVIOUS -> AudioKeyCommand.PREVIOUS_CHAPTER
        ReaderAction.CHAPTER_NEXT -> AudioKeyCommand.NEXT_CHAPTER
        ReaderAction.TOGGLE_BOOKMARK -> AudioKeyCommand.TOGGLE_BOOKMARK
        ReaderAction.OPEN_CONTENTS -> AudioKeyCommand.OPEN_CHAPTERS
        ReaderAction.OPEN_APPEARANCE -> AudioKeyCommand.OPEN_SETTINGS
        else -> null
    }
}
