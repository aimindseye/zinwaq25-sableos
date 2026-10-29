/*
 * Sable Terminal: ConnectBot for SableOS on keyboards without Ctrl or Esc
 * Copyright 2026 SableOS contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.sable

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.connectbot.service.KeyDispatcher
import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SableTerminalKeysTest {
    private val sent = mutableListOf<Pair<Int, Int>>()
    private val keys = TerminalKeyListener(KeyDispatcher { mods, key -> sent += mods to key })

    private fun event(action: Int, code: Int, repeat: Int = 0) = KeyEvent(0L, 0L, action, code, repeat)

    private fun press(code: Int): Pair<Boolean, Boolean> =
        SableTerminalKeys.handle(event(KeyEvent.ACTION_DOWN, code), keys) to
            SableTerminalKeys.handle(event(KeyEvent.ACTION_UP, code), keys)

    @Test
    fun menuIsAStickyCtrl() {
        assertEquals(true to true, press(KeyEvent.KEYCODE_MENU))
        assertTrue(keys.isCtrlActive())
        keys.sendPressedKey('c'.code)
        assertEquals(listOf(4 to 'c'.code), sent)
        assertFalse(keys.isCtrlActive())
    }

    @Test
    fun menuTwiceLocksAndThreeTimesReleases() {
        press(KeyEvent.KEYCODE_MENU)
        press(KeyEvent.KEYCODE_MENU)
        keys.sendPressedKey('c'.code)
        assertTrue(keys.isCtrlActive())
        press(KeyEvent.KEYCODE_MENU)
        assertFalse(keys.isCtrlActive())
    }

    @Test
    fun backWithCtrlOnSendsEscAndStaysInTheConsole() {
        press(KeyEvent.KEYCODE_MENU)
        assertEquals(true to true, press(KeyEvent.KEYCODE_BACK))
        assertEquals(listOf(0 to VTermKey.ESCAPE), sent)
        assertFalse(keys.isCtrlActive())
    }

    @Test
    fun plainBackAndOtherKeysAreLeftAlone() {
        assertEquals(false to false, press(KeyEvent.KEYCODE_BACK))
        assertEquals(false to false, press(KeyEvent.KEYCODE_C))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun theSameEventSeenTwiceActsOnce() {
        val down = event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)
        assertTrue(SableTerminalKeys.handle(down, keys))
        assertTrue(SableTerminalKeys.handle(down, keys))
        assertEquals(ModifierAfterOnePress, keys.getModifierState().ctrlState.name)
    }

    @Test
    fun decideIsPure() {
        assertEquals(SableTerminalKey.Ctrl, SableTerminalKeys.decide(82, down = true, repeat = 0, ctrlActive = false, escapePending = false))
        assertEquals(SableTerminalKey.Swallow, SableTerminalKeys.decide(82, down = true, repeat = 1, ctrlActive = true, escapePending = false))
        assertEquals(SableTerminalKey.None, SableTerminalKeys.decide(4, down = true, repeat = 0, ctrlActive = false, escapePending = false))
        assertEquals(SableTerminalKey.Swallow, SableTerminalKeys.decide(4, down = false, repeat = 0, ctrlActive = false, escapePending = true))
    }

    private companion object {
        const val ModifierAfterOnePress = "TRANSIENT"
    }
}
