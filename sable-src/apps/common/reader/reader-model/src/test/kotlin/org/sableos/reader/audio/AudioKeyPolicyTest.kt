package org.sableos.reader.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.reader.input.KeyInput
import org.sableos.reader.input.ReaderKey

class AudioKeyPolicyTest {
    private fun key(k: ReaderKey, shift: Boolean = false) = KeyInput(k, shift = shift)

    private fun letter(c: Char) = KeyInput(ReaderKey.CHARACTER, character = c)

    private fun decide(input: KeyInput, editing: Boolean = false, overlay: Boolean = false) =
        AudioKeyPolicy.decide(input, editing, overlay)

    @Test
    fun theTransportKeysAreMapped() {
        assertEquals(AudioKeyCommand.PLAY_PAUSE, decide(key(ReaderKey.SPACE)))
        assertEquals(AudioKeyCommand.PLAY_PAUSE, decide(key(ReaderKey.SPACE, shift = true)))
        assertEquals(AudioKeyCommand.SEEK_BACK, decide(key(ReaderKey.LEFT)))
        assertEquals(AudioKeyCommand.SEEK_FORWARD, decide(key(ReaderKey.RIGHT)))
        assertEquals(AudioKeyCommand.PREVIOUS_CHAPTER, decide(key(ReaderKey.PAGE_UP)))
        assertEquals(AudioKeyCommand.NEXT_CHAPTER, decide(key(ReaderKey.PAGE_DOWN)))
    }

    @Test
    fun panelLettersAreMappedAndThereIsNoFind() {
        assertEquals(AudioKeyCommand.TOGGLE_BOOKMARK, decide(letter('b')))
        assertEquals(AudioKeyCommand.OPEN_CHAPTERS, decide(letter('t')))
        assertEquals(AudioKeyCommand.OPEN_SETTINGS, decide(letter('a')))
        assertNull(decide(letter('f')))
    }

    @Test
    fun keysWithoutAnAudioMeaningPassThrough() {
        assertNull(decide(key(ReaderKey.UP)))
        assertNull(decide(key(ReaderKey.DOWN)))
        assertNull(decide(key(ReaderKey.ACTIVATE)))
        assertNull(decide(key(ReaderKey.MENU)))
        assertNull(decide(key(ReaderKey.BACK)))
    }

    @Test
    fun overlaysAndTextFieldsOnlyEverSeeEscape() {
        assertNull(decide(key(ReaderKey.SPACE), overlay = true))
        assertNull(decide(key(ReaderKey.SPACE), editing = true))
        assertNull(decide(letter('b'), editing = true))
        assertEquals(AudioKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE), overlay = true))
        assertEquals(AudioKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE), editing = true))
    }

    @Test
    fun chordsAreNeverHandled() {
        assertNull(decide(KeyInput(ReaderKey.SPACE, ctrl = true)))
    }

    @Test
    fun repeatedKeyDownsNeverRepeatPlayPauseSeeksChaptersBookmarkOrEscape() {
        listOf(
            ReaderKey.SPACE, ReaderKey.LEFT, ReaderKey.RIGHT, ReaderKey.PAGE_UP, ReaderKey.PAGE_DOWN, ReaderKey.ESCAPE,
        ).forEach { assertNull("$it", decide(KeyInput(it, repeated = true))) }
        listOf('b', 't', 'a').forEach { assertNull("$it", decide(KeyInput(ReaderKey.CHARACTER, it, repeated = true))) }
        assertEquals(AudioKeyCommand.PLAY_PAUSE, decide(key(ReaderKey.SPACE)))
        assertEquals(AudioKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE)))
    }
}
