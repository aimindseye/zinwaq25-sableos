package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyShortcutsTest {
    private val send = KeyShortcuts.IME_ACTION_SEND
    private val noEnterAction = 0x40000000

    @Test fun androidConstantsArePinned() {
        // android.view.KeyEvent and EditorInfo values.
        assertEquals(8, KeyShortcuts.KEYCODE_1)
        assertEquals(51, KeyShortcuts.KEYCODE_W)
        assertEquals(33, KeyShortcuts.KEYCODE_E)
        assertEquals(46, KeyShortcuts.KEYCODE_R)
        assertEquals(4, KeyShortcuts.IME_ACTION_SEND)
    }

    @Test fun ctrlPicksAShowingCandidate() {
        assertEquals(0, KeyShortcuts.candidateIndex(KeyShortcuts.KEYCODE_W, true, false, 3))
        assertEquals(2, KeyShortcuts.candidateIndex(KeyShortcuts.KEYCODE_3, true, false, 3))
        assertEquals(1, KeyShortcuts.candidateIndex(KeyShortcuts.KEYCODE_E, true, false, 2))
    }

    @Test fun withoutThatCandidateCtrlStaysAnAppShortcut() {
        assertNull(KeyShortcuts.candidateIndex(KeyShortcuts.KEYCODE_W, true, false, 0))
        assertNull(KeyShortcuts.candidateIndex(KeyShortcuts.KEYCODE_R, true, false, 2))
        assertNull(KeyShortcuts.candidateIndex(KeyShortcuts.KEYCODE_W, false, false, 3))
        assertNull(KeyShortcuts.candidateIndex(KeyShortcuts.KEYCODE_W, true, true, 3))
        assertNull(KeyShortcuts.candidateIndex(29, true, false, 3)) // Ctrl+A
    }

    @Test fun enterSendsOnlyWhenOnAndTheFieldOffersSend() {
        assertTrue(KeyShortcuts.enterSends(true, send, false, false, false))
        assertTrue(KeyShortcuts.enterSends(true, send or noEnterAction, false, false, false))
        assertFalse(KeyShortcuts.enterSends(false, send, false, false, false))
        assertFalse(KeyShortcuts.enterSends(true, 6, false, false, false)) // IME_ACTION_DONE
        assertFalse(KeyShortcuts.enterSends(true, null, false, false, false))
    }

    @Test fun modifiedEnterKeepsItsPlatformMeaning() {
        assertFalse(KeyShortcuts.enterSends(true, send, true, false, false))
        assertFalse(KeyShortcuts.enterSends(true, send, false, true, false))
        assertFalse(KeyShortcuts.enterSends(true, send, false, false, true))
    }
}
