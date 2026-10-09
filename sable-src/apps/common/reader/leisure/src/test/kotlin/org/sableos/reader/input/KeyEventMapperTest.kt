package org.sableos.reader.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyEventMapperTest {
    private fun down(code: Int, meta: Int = 0, repeat: Int = 0) =
        KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, repeat, meta)

    private fun up(code: Int) = KeyEvent(KeyEvent.ACTION_UP, code)

    @Test
    fun semanticKeysAreMapped() {
        assertEquals(ReaderKey.LEFT, KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_DPAD_LEFT))?.key)
        assertEquals(ReaderKey.PAGE_DOWN, KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_PAGE_DOWN))?.key)
        assertEquals(ReaderKey.SPACE, KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_SPACE))?.key)
        assertEquals(ReaderKey.ESCAPE, KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_ESCAPE))?.key)
        assertEquals(ReaderKey.CHARACTER, KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_B))?.key)
    }

    @Test
    fun activateKeysAreDpadCenterEnterAndKeypadEnter() {
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
            .forEach { assertEquals("keycode $it", ReaderKey.ACTIVATE, KeyEventMapper.toKeyInput(down(it))?.key) }
        assertEquals(ReaderKey.PAGE_UP, KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_PAGE_UP))?.key)
    }

    @Test
    fun homeAndEndAreLeftToTheEditorOrPlatform() {
        listOf(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_HOME)
            .forEach { assertNull("keycode $it", KeyEventMapper.toKeyInput(down(it))) }
    }

    @Test
    fun repeatCountIsCarriedAsTheRepeatedFlag() {
        assertFalse(KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_ENTER, repeat = 0))?.repeated == true)
        assertTrue(KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_ENTER, repeat = 1))?.repeated == true)
        assertTrue(KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 3))?.repeated == true)
    }

    @Test
    fun volumeMediaAndBackAreNeverHijacked() {
        listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_BACK,
        ).forEach { assertNull("keycode $it", KeyEventMapper.toKeyInput(down(it))) }
    }

    @Test
    fun modifiersAreCarried() {
        val input = KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON))
        assertTrue(input?.ctrl == true)
        assertTrue(input?.hasCommandModifier == true)
        assertFalse(KeyEventMapper.toKeyInput(down(KeyEvent.KEYCODE_B))?.hasCommandModifier == true)
    }

    @Test
    fun dispatcherConsumesTheMatchingKeyUpOnlyAfterHandlingTheDown() {
        val dispatcher = ReaderKeyDispatcher()
        var calls = 0
        val unregister = dispatcher.register { calls++; it.key == ReaderKey.RIGHT }
        assertTrue(dispatcher.dispatch(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertTrue(dispatcher.dispatch(up(KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertFalse(dispatcher.dispatch(down(KeyEvent.KEYCODE_DPAD_LEFT)))
        assertFalse(dispatcher.dispatch(up(KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals(2, calls)
        unregister()
        assertFalse(dispatcher.dispatch(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
    }

    @Test
    fun aRepeatedKeyDownOfAConsumedKeyStaysConsumedEvenWhenThePolicyIgnoresIt() {
        val dispatcher = ReaderKeyDispatcher()
        val seen = mutableListOf<Boolean>()
        dispatcher.register { seen += it.repeated; !it.repeated }
        assertTrue(dispatcher.dispatch(down(KeyEvent.KEYCODE_ENTER)))
        assertTrue(dispatcher.dispatch(down(KeyEvent.KEYCODE_ENTER, repeat = 1)))
        assertTrue(dispatcher.dispatch(up(KeyEvent.KEYCODE_ENTER)))
        assertEquals(listOf(false, true), seen)
        assertFalse(dispatcher.dispatch(down(KeyEvent.KEYCODE_ENTER, repeat = 1)))
    }

    @Test
    fun aStaleUnregisterDoesNotRemoveANewerHandler() {
        val dispatcher = ReaderKeyDispatcher()
        val first = dispatcher.register { false }
        dispatcher.register { true }
        first()
        assertTrue(dispatcher.dispatch(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
    }
}
