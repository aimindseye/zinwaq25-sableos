package org.sableos.titan2.displaycompat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStyleTest {
    private class FakeStore(var value: AppCornerStyle?, var accept: Boolean = true) :
        AppStyleStore {
        val writes = mutableListOf<AppCornerStyle>()

        override fun read(): AppCornerStyle? = value

        override fun write(style: AppCornerStyle): Boolean {
            writes += style
            if (!accept || value == null) return false
            value = style
            return true
        }
    }

    @Test fun stableValuesMatchTheAuthority() {
        assertEquals(listOf("compact", "rounded"), AppCornerStyle.entries.map { it.stableValue })
        AppCornerStyle.entries.forEach {
            assertEquals(it, AppCornerStyle.fromStableValue(it.stableValue))
        }
    }

    @Test fun compactIsTheDefaultAndUnknownValuesFallBack() {
        assertEquals(AppCornerStyle.Compact, AppCornerStyle.DEFAULT)
        assertEquals(AppCornerStyle.Compact, AppCornerStyle.fromStableValue(null))
        assertEquals(AppCornerStyle.Compact, AppCornerStyle.fromStableValue("Rounded"))
        assertEquals(AppCornerStyle.Compact, AppCornerStyle.fromStableValue("pill"))
    }

    @Test fun leftAndRightMoveAcrossTheOptionsAndStopAtTheEnds() {
        assertEquals(
            AppCornerStyle.Rounded,
            AppStylePolicy.styleForKey(AppCornerStyle.Compact, AppStyleKey.RIGHT)
        )
        assertNull(AppStylePolicy.styleForKey(AppCornerStyle.Rounded, AppStyleKey.RIGHT))
        assertEquals(
            AppCornerStyle.Compact,
            AppStylePolicy.styleForKey(AppCornerStyle.Rounded, AppStyleKey.LEFT)
        )
        assertNull(AppStylePolicy.styleForKey(AppCornerStyle.Compact, AppStyleKey.LEFT))
    }

    @Test fun enterSwitchesToTheOtherStyle() {
        assertEquals(
            AppCornerStyle.Rounded,
            AppStylePolicy.styleForKey(AppCornerStyle.Compact, AppStyleKey.ENTER)
        )
        assertEquals(
            AppCornerStyle.Compact,
            AppStylePolicy.styleForKey(AppCornerStyle.Rounded, AppStyleKey.ENTER)
        )
    }

    @Test fun missingAuthorityMakesTheSectionReadOnly() {
        val store = FakeStore(null)
        val c = AppStyleController(store)
        val state = c.load()
        assertEquals(AppStyleAvailability.UNAVAILABLE, state.availability)
        assertEquals(AppCornerStyle.Compact, state.style)
        assertFalse(state.enabled)
        assertNull(c.onKey(state, AppStyleKey.ENTER))
        assertNull(c.choose(state, AppCornerStyle.Rounded))
        assertTrue(store.writes.isEmpty())
    }

    @Test fun choosingWritesReReadsAndReports() {
        val store = FakeStore(AppCornerStyle.Compact)
        val c = AppStyleController(store)
        val (state, msg) = c.onKey(c.load(), AppStyleKey.RIGHT)!!
        assertEquals(AppCornerStyle.Rounded, state.style)
        assertEquals(listOf(AppCornerStyle.Rounded), store.writes)
        assertEquals("Sable apps now use Rounded corners.", msg)
        assertFalse(msg.contains("could not"))
    }

    @Test fun unchangedKeysAndSameChoiceDoNotWrite() {
        val store = FakeStore(AppCornerStyle.Rounded)
        val c = AppStyleController(store)
        val state = c.load()
        assertNull(c.onKey(state, AppStyleKey.RIGHT))
        assertNull(c.choose(state, AppCornerStyle.Rounded))
        assertTrue(store.writes.isEmpty())
    }

    @Test fun refusedWriteKeepsTheOldStyleAndSaysSo() {
        val store = FakeStore(AppCornerStyle.Compact, accept = false)
        val c = AppStyleController(store)
        val (state, msg) = c.choose(c.load(), AppCornerStyle.Rounded)!!
        assertEquals(AppCornerStyle.Compact, state.style)
        assertTrue(msg.contains("could not"))
    }
}
