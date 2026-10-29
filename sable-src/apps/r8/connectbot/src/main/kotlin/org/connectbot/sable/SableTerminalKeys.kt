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
import org.connectbot.service.TerminalKeyListener

/** What a key does in the console on a keyboard with no Ctrl or Esc (the Zinwa Q25). */
enum class SableTerminalKey { Ctrl, Esc, Swallow, None }

/**
 * The Q25 has no Ctrl or Esc key. In the console the Menu (BlackBerry) key is a sticky Ctrl, as the
 * on-screen Ctrl is: press it, then C, for Ctrl+C; twice locks it, three times releases it. Back
 * while Ctrl is on sends Esc instead of leaving the console. Everything else is untouched.
 */
object SableTerminalKeys {
    const val KEYCODE_BACK = 4
    const val KEYCODE_MENU = 82

    /** Back sent Esc on key-down; its repeats and key-up must not leave the console. */
    private var escapePending = false
    private var lastEvent: KeyEvent? = null
    private var lastResult = false

    fun decide(
        keyCode: Int,
        down: Boolean,
        repeat: Int,
        ctrlActive: Boolean,
        escapePending: Boolean,
    ): SableTerminalKey = when {
        keyCode == KEYCODE_MENU -> if (down && repeat == 0) SableTerminalKey.Ctrl else SableTerminalKey.Swallow
        keyCode == KEYCODE_BACK && down && repeat == 0 && ctrlActive -> SableTerminalKey.Esc
        keyCode == KEYCODE_BACK && escapePending -> SableTerminalKey.Swallow
        else -> SableTerminalKey.None
    }

    /** True when the key was used here. The same event may arrive twice (preview and intercept). */
    fun handle(event: KeyEvent, keys: TerminalKeyListener?): Boolean {
        if (event === lastEvent) return lastResult
        lastEvent = event
        val down = event.action == KeyEvent.ACTION_DOWN
        val action =
            if (keys == null) {
                SableTerminalKey.None
            } else {
                decide(event.keyCode, down, event.repeatCount, keys.isCtrlActive(), escapePending)
            }
        when (action) {
            SableTerminalKey.Ctrl -> keys?.metaPress(TerminalKeyListener.CTRL_ON, true)
            SableTerminalKey.Esc -> {
                escapePending = true
                // Esc alone: drop the Ctrl that asked for it, locked or not.
                while (keys?.isCtrlActive() == true) keys.metaPress(TerminalKeyListener.CTRL_ON, true)
                keys?.sendEscape()
            }
            SableTerminalKey.Swallow, SableTerminalKey.None -> Unit
        }
        if (event.keyCode == KEYCODE_BACK && !down) escapePending = false
        lastResult = action != SableTerminalKey.None
        return lastResult
    }
}
