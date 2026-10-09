package org.sableos.reader.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubKeyPolicyTest {
    private fun key(k: ReaderKey, shift: Boolean = false) = KeyInput(k, shift = shift)

    private fun letter(c: Char) = KeyInput(ReaderKey.CHARACTER, character = c)

    private fun decide(
        input: KeyInput,
        scroll: Boolean = false,
        editing: Boolean = false,
        overlay: Boolean = false,
    ) = EpubKeyPolicy.decide(input, scroll, editing, overlay)

    @Test
    fun pagedArrowsTurnByPhysicalSideAndPageKeysGoForwardBack() {
        assertEquals(EpubKeyCommand.TURN_RIGHT, decide(key(ReaderKey.RIGHT)))
        assertEquals(EpubKeyCommand.TURN_LEFT, decide(key(ReaderKey.LEFT)))
        assertEquals(EpubKeyCommand.FORWARD, decide(key(ReaderKey.PAGE_DOWN)))
        assertEquals(EpubKeyCommand.BACKWARD, decide(key(ReaderKey.PAGE_UP)))
        assertEquals(EpubKeyCommand.FORWARD, decide(key(ReaderKey.SPACE)))
        assertEquals(EpubKeyCommand.BACKWARD, decide(key(ReaderKey.SPACE, shift = true)))
    }

    @Test
    fun scrollModeLeavesScrollingToTheWebView() {
        listOf(ReaderKey.LEFT, ReaderKey.RIGHT, ReaderKey.UP, ReaderKey.DOWN, ReaderKey.PAGE_UP, ReaderKey.PAGE_DOWN)
            .forEach { assertNull(it.name, decide(key(it), scroll = true)) }
    }

    @Test
    fun panelLettersAreMappedAndStayWithTextFields() {
        assertEquals(EpubKeyCommand.TOGGLE_BOOKMARK, decide(letter('b')))
        assertEquals(EpubKeyCommand.OPEN_CONTENTS, decide(letter('t')))
        assertEquals(EpubKeyCommand.OPEN_FIND, decide(letter('f')))
        assertEquals(EpubKeyCommand.OPEN_APPEARANCE, decide(letter('a')))
        assertNull(decide(letter('b'), editing = true))
        assertNull(decide(key(ReaderKey.SPACE), editing = true))
    }

    @Test
    fun overlaysOnlyHandleEscape() {
        assertNull(decide(key(ReaderKey.RIGHT), overlay = true))
        assertNull(decide(letter('f'), overlay = true))
        assertEquals(EpubKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE), overlay = true))
        assertEquals(EpubKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE), editing = true))
    }

    @Test
    fun hardwareBackIsLeftToTheSystemBackDispatcher() {
        assertNull(decide(key(ReaderKey.BACK)))
    }

    @Test
    fun commandChordsAreNeverHandled() {
        assertNull(decide(KeyInput(ReaderKey.CHARACTER, character = 'b', ctrl = true)))
    }
}
