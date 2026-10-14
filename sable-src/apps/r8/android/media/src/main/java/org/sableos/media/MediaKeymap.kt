package org.sableos.media

import android.view.KeyEvent

/**
 * Sable Media single-key commands (MEDIA_GALLERY_UX.md, "Input contract").
 *
 * The Q25 keyboard has Alt, Shift, Sym and letters but no Fn or Ctrl. Symbols
 * such as `/`, `+` and `-` are typed with Alt or Sym, so they are matched on
 * the character the key produces, not on the key code.
 */
internal enum class MediaKeyCommand {
    PlayPause,
    Previous,
    Next,
    SeekBack,
    SeekForward,
    VolumeUp,
    VolumeDown,
    Search,
    ViewMode,
    Favorite,
    Delete,
    Queue,
    Info,
    Latest,
    Albums,
    Back,
}

/** A key-down event reduced to what the keymap needs, so the mapping is testable on the JVM. */
internal data class MediaKeyPress(
    val keyCode: Int,
    /** The character the key produces with the current meta state, or null. */
    val char: Char? = null,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val ctrl: Boolean = false,
    val repeatCount: Int = 0,
)

/** Where Back (or Esc) goes from a destination: one logical layer, never out of the app from a sub-screen. */
internal sealed interface MediaBackStep {
    data object CloseSearch : MediaBackStep

    data class Navigate(
        val destination: MediaDestination,
    ) : MediaBackStep
}

internal object MediaKeymap {
    /** Shift+J/K or Shift+Left/Right seek step. */
    const val SEEK_STEP_MS = 10_000L

    /**
     * Maps one key-down to a command, or null when the key is left to the focused view.
     *
     * @param textInputActive a text field has focus: TEXT_INPUT_ALWAYS_WINS, so only Esc is taken.
     * @param onNowPlaying Left/Right mean previous/next only on Now playing; elsewhere they move focus
     *   between tabs and row actions.
     */
    fun command(
        press: MediaKeyPress,
        textInputActive: Boolean,
        onNowPlaying: Boolean,
    ): MediaKeyCommand? {
        val command =
            when {
                press.keyCode == KeyEvent.KEYCODE_ESCAPE -> MediaKeyCommand.Back

                textInputActive || press.ctrl -> null

                // Symbols are matched first: on the Q25 they are Alt (or Sym) plus a letter key.
                symbolCommand(press.char) != null -> symbolCommand(press.char)

                // Any other Alt+letter types a symbol, which is not a command.
                press.alt -> null

                press.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> arrowCommand(press.shift, onNowPlaying, forward = false)

                press.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> arrowCommand(press.shift, onNowPlaying, forward = true)

                else -> letterCommand(press.keyCode, press.shift)
            }
        // Holding a key repeats only seek and volume; a held Space must not toggle play/pause.
        return command?.takeIf { press.repeatCount == 0 || it in REPEATABLE }
    }

    /** One layer back from [destination]; null means Back leaves the app (a top-level destination). */
    fun backStep(
        destination: MediaDestination,
        searchOpen: Boolean,
    ): MediaBackStep? =
        when {
            searchOpen -> MediaBackStep.CloseSearch

            destination == MediaDestination.Queue -> MediaBackStep.Navigate(MediaDestination.NowPlaying)

            destination == MediaDestination.Settings -> MediaBackStep.Navigate(MediaDestination.Collection)

            destination == MediaDestination.PlaylistEditor -> MediaBackStep.Navigate(MediaDestination.Collection)

            destination == MediaDestination.ManageStations ||
                destination == MediaDestination.AddStation -> MediaBackStep.Navigate(MediaDestination.Radio)

            else -> null
        }

    /** The library pivot after [current] for the V (view mode) key. */
    fun nextPivot(current: LibraryPivot): LibraryPivot {
        val pivots = LibraryPivot.entries
        return pivots[(pivots.indexOf(current) + 1) % pivots.size]
    }

    private val REPEATABLE =
        setOf(
            MediaKeyCommand.SeekBack,
            MediaKeyCommand.SeekForward,
            MediaKeyCommand.VolumeUp,
            MediaKeyCommand.VolumeDown,
        )

    private fun symbolCommand(char: Char?): MediaKeyCommand? =
        when (char) {
            '/' -> MediaKeyCommand.Search
            '+', '=' -> MediaKeyCommand.VolumeUp
            '-' -> MediaKeyCommand.VolumeDown
            else -> null
        }

    private fun arrowCommand(
        shift: Boolean,
        onNowPlaying: Boolean,
        forward: Boolean,
    ): MediaKeyCommand? =
        when {
            !onNowPlaying -> null
            shift && forward -> MediaKeyCommand.SeekForward
            shift -> MediaKeyCommand.SeekBack
            forward -> MediaKeyCommand.Next
            else -> MediaKeyCommand.Previous
        }

    @Suppress("CyclomaticComplexMethod")
    private fun letterCommand(
        keyCode: Int,
        shift: Boolean,
    ): MediaKeyCommand? =
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> MediaKeyCommand.PlayPause.takeIf { !shift }
            KeyEvent.KEYCODE_J -> if (shift) MediaKeyCommand.SeekBack else MediaKeyCommand.Previous
            KeyEvent.KEYCODE_K -> if (shift) MediaKeyCommand.SeekForward else MediaKeyCommand.Next
            KeyEvent.KEYCODE_P -> MediaKeyCommand.Previous.takeIf { !shift }
            KeyEvent.KEYCODE_N -> MediaKeyCommand.Next.takeIf { !shift }
            KeyEvent.KEYCODE_S -> MediaKeyCommand.Search.takeIf { !shift }
            KeyEvent.KEYCODE_V -> MediaKeyCommand.ViewMode.takeIf { !shift }
            KeyEvent.KEYCODE_F -> MediaKeyCommand.Favorite.takeIf { !shift }
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DEL -> MediaKeyCommand.Delete.takeIf { !shift }
            KeyEvent.KEYCODE_Q -> MediaKeyCommand.Queue.takeIf { !shift }
            KeyEvent.KEYCODE_I -> MediaKeyCommand.Info.takeIf { !shift }
            KeyEvent.KEYCODE_T -> MediaKeyCommand.Latest.takeIf { !shift }
            KeyEvent.KEYCODE_A -> MediaKeyCommand.Albums.takeIf { !shift }
            else -> null
        }
}

/** LIBRARY_SEARCH_EXPLICIT: filters the local collection by title, artist or album as the user types. */
internal object MediaLibrarySearch {
    /** Every whitespace-separated term must appear in the title, artist or album (case-insensitive). */
    fun matches(
        item: LocalAudioItem,
        query: String,
    ): Boolean {
        val terms = terms(query)
        if (terms.isEmpty()) return true
        val haystack = listOf(item.title, item.artist, item.album).joinToString("\n") { it.lowercase() }
        return terms.all { it in haystack }
    }

    fun matchesName(
        name: String,
        query: String,
    ): Boolean {
        val terms = terms(query)
        val lower = name.lowercase()
        return terms.all { it in lower }
    }

    fun filter(
        items: List<LocalAudioItem>,
        query: String,
    ): List<LocalAudioItem> =
        if (terms(query).isEmpty()) {
            items
        } else {
            items.filter { matches(it, query) }
        }

    private fun terms(query: String): List<String> =
        query
            .trim()
            .lowercase()
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")
}
