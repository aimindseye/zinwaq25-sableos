package org.sableos.media

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaKeymapTest {
    @Suppress("LongParameterList")
    private fun cmd(
        keyCode: Int,
        char: Char? = null,
        shift: Boolean = false,
        alt: Boolean = false,
        ctrl: Boolean = false,
        repeat: Int = 0,
        typing: Boolean = false,
        nowPlaying: Boolean = false,
    ): MediaKeyCommand? =
        MediaKeymap.command(
            MediaKeyPress(keyCode, char, shift, alt, ctrl, repeat),
            textInputActive = typing,
            onNowPlaying = nowPlaying,
        )

    @Test
    fun designInputContractLetters() {
        assertEquals(MediaKeyCommand.PlayPause, cmd(KeyEvent.KEYCODE_SPACE, ' '))
        assertEquals(MediaKeyCommand.Previous, cmd(KeyEvent.KEYCODE_J, 'j'))
        assertEquals(MediaKeyCommand.Next, cmd(KeyEvent.KEYCODE_K, 'k'))
        assertEquals(MediaKeyCommand.Search, cmd(KeyEvent.KEYCODE_S, 's'))
        assertEquals(MediaKeyCommand.ViewMode, cmd(KeyEvent.KEYCODE_V, 'v'))
        assertEquals(MediaKeyCommand.Favorite, cmd(KeyEvent.KEYCODE_F, 'f'))
        assertEquals(MediaKeyCommand.Delete, cmd(KeyEvent.KEYCODE_D, 'd'))
        assertEquals(MediaKeyCommand.Delete, cmd(KeyEvent.KEYCODE_DEL))
        assertEquals(MediaKeyCommand.Queue, cmd(KeyEvent.KEYCODE_Q, 'q'))
        assertEquals(MediaKeyCommand.Info, cmd(KeyEvent.KEYCODE_I, 'i'))
        assertEquals(MediaKeyCommand.Latest, cmd(KeyEvent.KEYCODE_T, 't'))
        assertEquals(MediaKeyCommand.Albums, cmd(KeyEvent.KEYCODE_A, 'a'))
        assertEquals(MediaKeyCommand.Back, cmd(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun nAndPAreNextAndPrevious() {
        assertEquals(MediaKeyCommand.Next, cmd(KeyEvent.KEYCODE_N, 'n'))
        assertEquals(MediaKeyCommand.Previous, cmd(KeyEvent.KEYCODE_P, 'p'))
    }

    @Test
    fun shiftJkSeeks() {
        assertEquals(MediaKeyCommand.SeekBack, cmd(KeyEvent.KEYCODE_J, 'J', shift = true))
        assertEquals(MediaKeyCommand.SeekForward, cmd(KeyEvent.KEYCODE_K, 'K', shift = true))
    }

    @Test
    fun arrowsAreTrackKeysOnlyOnNowPlaying() {
        assertNull(cmd(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(cmd(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(MediaKeyCommand.Previous, cmd(KeyEvent.KEYCODE_DPAD_LEFT, nowPlaying = true))
        assertEquals(MediaKeyCommand.Next, cmd(KeyEvent.KEYCODE_DPAD_RIGHT, nowPlaying = true))
        assertEquals(MediaKeyCommand.SeekBack, cmd(KeyEvent.KEYCODE_DPAD_LEFT, shift = true, nowPlaying = true))
        assertEquals(MediaKeyCommand.SeekForward, cmd(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true, nowPlaying = true))
        // Up/Down always move focus.
        assertNull(cmd(KeyEvent.KEYCODE_DPAD_DOWN, nowPlaying = true))
    }

    @Test
    fun symbolsMatchOnTheTypedCharacterIncludingAlt() {
        // On the Q25, `/`, `+` and `-` come from Alt (or Sym) plus a letter key.
        assertEquals(MediaKeyCommand.Search, cmd(KeyEvent.KEYCODE_SLASH, '/'))
        assertEquals(MediaKeyCommand.Search, cmd(KeyEvent.KEYCODE_O, '/', alt = true))
        assertEquals(MediaKeyCommand.VolumeUp, cmd(KeyEvent.KEYCODE_PLUS, '+'))
        assertEquals(MediaKeyCommand.VolumeUp, cmd(KeyEvent.KEYCODE_EQUALS, '='))
        assertEquals(MediaKeyCommand.VolumeDown, cmd(KeyEvent.KEYCODE_MINUS, '-'))
        assertEquals(MediaKeyCommand.VolumeDown, cmd(KeyEvent.KEYCODE_G, '-', alt = true))
    }

    @Test
    fun altLettersThatAreNotCommandSymbolsAreIgnored() {
        assertNull(cmd(KeyEvent.KEYCODE_J, ';', alt = true))
        assertNull(cmd(KeyEvent.KEYCODE_SPACE, ' ', alt = true))
    }

    @Test
    fun ctrlChordsAreLeftAlone() {
        assertNull(cmd(KeyEvent.KEYCODE_S, 's', ctrl = true))
        assertNull(cmd(KeyEvent.KEYCODE_SLASH, '/', ctrl = true))
    }

    @Test
    fun textInputAlwaysWins() {
        listOf(
            KeyEvent.KEYCODE_SPACE to ' ',
            KeyEvent.KEYCODE_J to 'j',
            KeyEvent.KEYCODE_D to 'd',
            KeyEvent.KEYCODE_SLASH to '/',
            KeyEvent.KEYCODE_MINUS to '-',
        ).forEach { (code, char) ->
            assertNull("key $char while typing", cmd(code, char, typing = true))
        }
        assertNull(cmd(KeyEvent.KEYCODE_DEL, typing = true))
        assertNull(cmd(KeyEvent.KEYCODE_DPAD_LEFT, typing = true, nowPlaying = true))
        // Esc still cancels.
        assertEquals(MediaKeyCommand.Back, cmd(KeyEvent.KEYCODE_ESCAPE, typing = true))
    }

    @Test
    fun repeatsOnlyRepeatSeekAndVolume() {
        assertNull(cmd(KeyEvent.KEYCODE_SPACE, ' ', repeat = 1))
        assertNull(cmd(KeyEvent.KEYCODE_D, 'd', repeat = 2))
        assertNull(cmd(KeyEvent.KEYCODE_ESCAPE, repeat = 1))
        assertEquals(MediaKeyCommand.SeekForward, cmd(KeyEvent.KEYCODE_K, 'K', shift = true, repeat = 3))
        assertEquals(MediaKeyCommand.VolumeUp, cmd(KeyEvent.KEYCODE_PLUS, '+', repeat = 3))
    }

    @Test
    fun unmappedLettersStayWithTheListForLetterJump() {
        assertNull(cmd(KeyEvent.KEYCODE_B, 'b'))
        assertNull(cmd(KeyEvent.KEYCODE_M, 'm'))
        assertNull(cmd(KeyEvent.KEYCODE_ENTER))
        assertNull(cmd(KeyEvent.KEYCODE_Z, 'z'))
    }

    @Test
    fun backIsOneLayer() {
        assertEquals(MediaBackStep.CloseSearch, MediaKeymap.backStep(MediaDestination.Collection, searchOpen = true))
        assertEquals(
            MediaBackStep.Navigate(MediaDestination.NowPlaying),
            MediaKeymap.backStep(MediaDestination.Queue, searchOpen = false),
        )
        assertEquals(
            MediaBackStep.Navigate(MediaDestination.Radio),
            MediaKeymap.backStep(MediaDestination.AddStation, searchOpen = false),
        )
        assertEquals(
            MediaBackStep.Navigate(MediaDestination.Radio),
            MediaKeymap.backStep(MediaDestination.ManageStations, searchOpen = false),
        )
        assertEquals(
            MediaBackStep.Navigate(MediaDestination.Collection),
            MediaKeymap.backStep(MediaDestination.PlaylistEditor, searchOpen = false),
        )
        assertEquals(
            MediaBackStep.Navigate(MediaDestination.Collection),
            MediaKeymap.backStep(MediaDestination.Settings, searchOpen = false),
        )
        assertNull(MediaKeymap.backStep(MediaDestination.Collection, searchOpen = false))
        assertNull(MediaKeymap.backStep(MediaDestination.NowPlaying, searchOpen = false))
    }

    @Test
    fun viewModeCyclesEveryPivot() {
        var pivot = LibraryPivot.Songs
        val seen = mutableListOf(pivot)
        repeat(LibraryPivot.entries.size) {
            pivot = MediaKeymap.nextPivot(pivot)
            seen += pivot
        }
        assertEquals(LibraryPivot.entries + LibraryPivot.Songs, seen)
    }
}

class MediaLibrarySearchTest {
    private val items =
        listOf(
            LocalAudioItem("u1", "k1", "Blue in Green", "Miles Davis", "Kind of Blue", 0L),
            LocalAudioItem("u2", "k2", "So What", "Miles Davis", "Kind of Blue", 0L),
            LocalAudioItem("u3", "k3", "Teardrop", "Massive Attack", "Mezzanine", 0L),
            LocalAudioItem("u4", "k4", "Angel", "Massive Attack", "Mezzanine", 0L),
        )

    @Test
    fun emptyQueryKeepsEverything() {
        assertEquals(items, MediaLibrarySearch.filter(items, ""))
        assertEquals(items, MediaLibrarySearch.filter(items, "   "))
    }

    @Test
    fun matchesTitleArtistOrAlbumCaseInsensitively() {
        assertEquals(listOf("u3"), MediaLibrarySearch.filter(items, "TEAR").map { it.uri })
        assertEquals(listOf("u1", "u2"), MediaLibrarySearch.filter(items, "miles").map { it.uri })
        assertEquals(listOf("u3", "u4"), MediaLibrarySearch.filter(items, "mezz").map { it.uri })
    }

    @Test
    fun everyTermMustMatchSomewhere() {
        assertEquals(listOf("u1"), MediaLibrarySearch.filter(items, "blue green").map { it.uri })
        assertEquals(listOf("u4"), MediaLibrarySearch.filter(items, "massive angel").map { it.uri })
        assertTrue(MediaLibrarySearch.filter(items, "miles mezzanine").isEmpty())
    }

    @Test
    fun namesMatchForPlaylists() {
        assertTrue(MediaLibrarySearch.matchesName("Road Trip", "trip"))
        assertTrue(MediaLibrarySearch.matchesName("Road Trip", ""))
        assertFalse(MediaLibrarySearch.matchesName("Road Trip", "jazz"))
    }
}
