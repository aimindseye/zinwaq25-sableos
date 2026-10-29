package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {
    // ---- recording ---------------------------------------------------------------------------

    @Test fun newestCopyComesFirst() {
        val h = ClipboardHistory()
        h.add("one", false)
        h.add("two", false)
        assertEquals(listOf("two", "one"), h.entries)
    }

    @Test fun copyingAgainMovesTheEntryToTheTop() {
        val h = ClipboardHistory()
        h.add("one", false)
        h.add("two", false)
        assertTrue(h.add("one", false))
        assertEquals(listOf("one", "two"), h.entries)
        assertFalse(h.add("one", false))
    }

    @Test fun sensitiveBlankAndHugeCopiesAreNeverRecorded() {
        val h = ClipboardHistory()
        assertFalse(h.add("hunter2", sensitive = true))
        assertFalse(h.add("   \n", false))
        assertFalse(h.add(null, false))
        assertFalse(h.add("x".repeat(ClipboardHistory.MAX_CHARS + 1), false))
        assertTrue(h.entries.isEmpty())
    }

    @Test fun historyIsBoundedAndDropsTheOldest() {
        val h = ClipboardHistory(maxEntries = 3)
        listOf("a", "b", "c", "d").forEach { h.add(it, false) }
        assertEquals(listOf("d", "c", "b"), h.entries)
        assertEquals(20, ClipboardHistory.MAX_ENTRIES)
    }

    @Test fun removeAndClear() {
        val h = ClipboardHistory()
        listOf("a", "b", "c").forEach { h.add(it, false) }
        h.remove(1)
        h.remove(9)
        assertEquals(listOf("c", "a"), h.entries)
        h.clear()
        assertTrue(h.entries.isEmpty())
    }

    @Test fun previewIsOneShortLine() {
        assertEquals("Meet at 6", ClipboardHistory.preview("  Meet\n\tat 6 "))
        val p = ClipboardHistory.preview("a".repeat(40))
        assertEquals(ClipboardHistory.PREVIEW_CHARS, p.length)
        assertTrue(p.endsWith("…"))
    }

    // ---- picker ------------------------------------------------------------------------------

    private fun opens(key: Int, alt: Boolean, shift: Boolean, ctrl: Boolean = false) =
        ClipPicker.opens(key, alt, shift, ctrl)

    @Test fun onlyHeldAltShiftVOpensThePicker() {
        val v = ClipPicker.KEYCODE_V
        assertTrue(opens(v, alt = true, shift = true))
        assertFalse(opens(v, alt = true, shift = false))
        assertFalse(opens(v, alt = false, shift = true))
        assertFalse(opens(v, alt = true, shift = true, ctrl = true))
        assertFalse(opens(ClipPicker.KEYCODE_SPACE, alt = true, shift = true))
    }

    @Test fun pickerKeys() {
        assertEquals(ClipAction.Next, ClipPicker.action(ClipPicker.KEYCODE_SPACE, shift = false))
        assertEquals(ClipAction.Previous, ClipPicker.action(ClipPicker.KEYCODE_SPACE, shift = true))
        assertEquals(
            ClipAction.Next,
            ClipPicker.action(ClipPicker.KEYCODE_DPAD_DOWN, shift = false)
        )
        assertEquals(
            ClipAction.Previous,
            ClipPicker.action(ClipPicker.KEYCODE_DPAD_LEFT, shift = false)
        )
        assertEquals(ClipAction.Paste, ClipPicker.action(ClipPicker.KEYCODE_ENTER, shift = false))
        assertEquals(
            ClipAction.Paste,
            ClipPicker.action(ClipPicker.KEYCODE_DPAD_CENTER, shift = false)
        )
        assertEquals(ClipAction.Remove, ClipPicker.action(ClipPicker.KEYCODE_DEL, shift = false))
        assertEquals(ClipAction.Close, ClipPicker.action(ClipPicker.KEYCODE_BACK, shift = false))
        assertEquals(ClipAction.Ignore, ClipPicker.action(59, shift = true))
        assertEquals(ClipAction.CloseAndPass, ClipPicker.action(29, shift = false))
    }

    @Test fun movingWrapsRound() {
        assertEquals(1, ClipPicker.move(0, 1, 3))
        assertEquals(0, ClipPicker.move(2, 1, 3))
        assertEquals(2, ClipPicker.move(0, -1, 3))
        assertEquals(0, ClipPicker.move(0, 1, 0))
    }

    @Test fun chipsShowThePositionAndTheSelectedEntryFirst() {
        val entries = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("2/5", "b", "c", "d"), ClipPicker.chips(entries, 1))
        assertEquals(listOf("5/5", "e"), ClipPicker.chips(entries, 4))
        assertEquals(listOf("Clipboard empty"), ClipPicker.chips(emptyList(), 0))
        assertNull(ClipPicker.entryForChip(0, 1))
        assertEquals(2, ClipPicker.entryForChip(2, 1))
    }

    @Test fun theOpenPickerShowsEvenWhereTheStripIsOff() {
        val i = StripInputs(
            field = InputClass.Text, softKeyboardShown = false, enabled = false,
            language = Languages.English,
            shift = ModState.Off, alt = ModState.Off, sym = ModState.Off, navOn = false,
            navEffective = false, candidates = emptyList(), variation = null,
            layout = KeyLayout.SableProvisional, nowMs = 0L, clips = listOf("1/1", "a")
        )
        val m = CompactStrip.build(i)
        assertEquals(StripMode.CandidatesOnly, m.mode)
        assertEquals(StripContent.Clips(listOf("1/1", "a")), m.content)
        assertEquals(StripMode.Hidden, CompactStrip.build(i.copy(field = InputClass.None)).mode)
    }
}
