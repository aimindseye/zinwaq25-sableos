package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyCommandsTest {
    private val home = KeyContext.Home(filterActive = false)
    private val filtering = KeyContext.Home(filterActive = true)
    private fun ch(c: Char, meta: Int = 0) = KeyPress(0, c.code, meta)

    @Test fun navigationKeys() {
        assertEquals(
            Command.Move(0, -1),
            KeyCommands.map(home, KeyPress(KeyCommands.KEYCODE_DPAD_UP))
        )
        assertEquals(
            Command.Move(1, 0),
            KeyCommands.map(home, KeyPress(KeyCommands.KEYCODE_DPAD_RIGHT))
        )
        assertEquals(Command.Open, KeyCommands.map(home, KeyPress(KeyCommands.KEYCODE_ENTER)))
        assertEquals(Command.Actions, KeyCommands.map(home, KeyPress(KeyCommands.KEYCODE_MENU)))
        assertEquals(
            Command.Actions,
            KeyCommands.map(
                home,
                KeyPress(KeyCommands.KEYCODE_ENTER, meta = KeyCommands.META_FUNCTION_ON)
            )
        )
        assertEquals(Command.Back, KeyCommands.map(home, KeyPress(KeyCommands.KEYCODE_ESCAPE)))
        assertEquals(Command.Back, KeyCommands.map(home, KeyPress(KeyCommands.KEYCODE_BACK)))
    }

    @Test fun printableKeysFilterOnHome() {
        assertEquals(Command.FilterAppend('c'), KeyCommands.map(home, ch('C')))
        assertEquals(Command.Search, KeyCommands.map(home, ch('/')))
        assertEquals(Command.FilterAppend('#'), KeyCommands.map(filtering, ch('#')))
        assertEquals(
            Command.FilterDelete,
            KeyCommands.map(filtering, KeyPress(KeyCommands.KEYCODE_DEL))
        )
        assertEquals(Command.PassThrough, KeyCommands.map(home, KeyPress(KeyCommands.KEYCODE_DEL)))
    }

    @Test fun letterCommandsAreToolLocal() {
        val measure = KeyContext.InTool(ToolKind.MEASUREMENT, calibratable = true)
        assertEquals(Command.Calibrate, KeyCommands.map(measure, ch('c')))
        assertEquals(Command.Reset, KeyCommands.map(measure, ch('r')))
        assertEquals(Command.Hold, KeyCommands.map(measure, ch('h')))
        assertEquals(Command.Info, KeyCommands.map(measure, ch('i')))
        val noCal = KeyContext.InTool(ToolKind.MEASUREMENT, calibratable = false)
        assertEquals(Command.PassThrough, KeyCommands.map(noCal, ch('c')))
        val info = KeyContext.InTool(ToolKind.INFO, calibratable = false)
        assertEquals(Command.PassThrough, KeyCommands.map(info, ch('r')))
        assertEquals(Command.PassThrough, KeyCommands.map(info, ch('h')))
        assertEquals(Command.Copy, KeyCommands.map(info, ch('c', KeyCommands.META_CTRL_ON)))
    }

    @Test fun textFieldWins() {
        listOf('c', 'r', 'h', 'i', '/', 'a').forEach {
            assertEquals(Command.PassThrough, KeyCommands.map(KeyContext.TextField, ch(it)))
        }
        assertEquals(
            Command.Back,
            KeyCommands.map(KeyContext.TextField, KeyPress(KeyCommands.KEYCODE_BACK))
        )
        assertEquals(
            KeyContext.TextField,
            KeyCommands.contextFor(Tool.COMPASS, textFieldFocused = true)
        )
    }

    @Test fun keyCaptureTakesEveryKeyAndNeedsDoubleBack() {
        val cap = KeyCommands.contextFor(Tool.KEY_VIEWER, textFieldFocused = false)
        assertEquals(Command.PassThrough, KeyCommands.map(cap, KeyPress(KeyCommands.KEYCODE_BACK)))
        assertEquals(
            Command.PassThrough,
            KeyCommands.map(cap, KeyPress(KeyCommands.KEYCODE_DPAD_UP))
        )
        val exit = KeyCommands.DoubleBackExit()
        assertFalse(exit.onKeyUp(KeyCommands.KEYCODE_BACK))
        assertFalse(exit.onKeyUp(KeyCommands.KEYCODE_ENTER))
        assertFalse(exit.onKeyUp(KeyCommands.KEYCODE_ESCAPE))
        assertTrue(exit.onKeyUp(KeyCommands.KEYCODE_BACK))
    }

    @Test fun reportSpaceToggles() {
        val r = KeyContext.InTool(ToolKind.REPORT, calibratable = false)
        assertEquals(
            Command.Toggle,
            KeyCommands.map(r, KeyPress(KeyCommands.KEYCODE_SPACE, ' '.code))
        )
        assertEquals(Command.NextField, KeyCommands.map(r, KeyPress(KeyCommands.KEYCODE_TAB)))
        assertEquals(
            Command.PrevField,
            KeyCommands.map(r, KeyPress(KeyCommands.KEYCODE_TAB, meta = KeyCommands.META_SHIFT_ON))
        )
    }

    @Test fun keyEventFormatMatchesKeyProbe() {
        assertEquals("SHIFT+ALT", KeyEventFormat.metaNames(0x3))
        assertEquals("U+0041 'A'", KeyEventFormat.charText(0x41))
        val l = KeyEventFormat.line(
            "DOWN",
            KeyEventFormat.ProbeKey("KEYCODE_A", 29, 30),
            KeyEventFormat.ProbeChar(0, 0x61),
            "kbd",
            0
        )
        assertEquals("DOWN KEYCODE_A(29) scan=30 meta=- char=U+0061 'a' dev=kbd", l)
    }
}
