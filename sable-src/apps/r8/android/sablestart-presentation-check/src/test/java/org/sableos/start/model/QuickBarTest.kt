package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Base quick bar contract (platform_sable SABLE_START_VISUAL_CONFIRMATION.md). */
class QuickBarTest {
    private val default = QuickBar.DEFAULT

    @Test
    fun neverCustomisedShowsPhoneHubCommandAllApps() {
        assertEquals(listOf("phone", "hub", "command", "apps"), QuickBar.normalize(null))
    }

    @Test
    fun normalizeKeepsCommandAndAllAppsAndCapsAtFive() {
        assertEquals(listOf("phone", "command", "apps"), QuickBar.normalize(listOf("phone", "bogus", "phone")))
        val tooMany = listOf("phone", "hub", "camera", "app:1", "app:2", "app:3")
        val normalized = QuickBar.normalize(tooMany)
        assertEquals(QuickBar.MAX_SLOTS, normalized.size)
        assertEquals(listOf("phone", "hub", "camera", "command", "apps"), normalized)
    }

    @Test
    fun reorderMovesSlotsAndStopsAtTheEdges() {
        assertEquals(listOf("hub", "phone", "command", "apps"), QuickBar.move(default, 0, 1))
        assertEquals(default, QuickBar.move(default, 0, -1))
        assertEquals(default, QuickBar.move(default, 3, 1))
    }

    @Test
    fun hubSlotIsReplaceableButCommandAndAllAppsAreNot() {
        assertEquals(listOf("phone", "app:7", "command", "apps"), QuickBar.replace(default, 1, "app:7"))
        assertEquals(default, QuickBar.replace(default, 2, "app:7"))
        assertEquals(default, QuickBar.replace(default, 3, "app:7"))
        assertEquals(default, QuickBar.replace(default, 1, "phone"))
        assertEquals(default, QuickBar.remove(default, 2))
        assertEquals(listOf("phone", "command", "apps"), QuickBar.remove(default, 1))
    }

    @Test
    fun addGoesBeforeCommandUpToFiveSlots() {
        val five = QuickBar.add(default, "camera")
        assertEquals(listOf("phone", "hub", "camera", "command", "apps"), five)
        assertEquals(five, QuickBar.add(five, "app:9"))
    }

    @Test
    fun fnDigitsOpenSlots() {
        val fn = QuickBar.META_FUNCTION_ON
        assertEquals(0, QuickBar.shortcutSlot(QuickBar.KEYCODE_1, fn, 4))
        assertEquals(3, QuickBar.shortcutSlot(QuickBar.KEYCODE_1 + 3, fn, 4))
        assertNull(QuickBar.shortcutSlot(QuickBar.KEYCODE_1 + 4, fn, 4))
        assertNull(QuickBar.shortcutSlot(QuickBar.KEYCODE_1, 0, 4))
    }

    @Test
    fun appTokensRoundTrip() {
        assertEquals("0:com.example/.Main", QuickBar.appKey(QuickBar.appToken("0:com.example/.Main")))
        assertNull(QuickBar.appKey("phone"))
        assertNull(QuickBar.appKey("app:"))
    }
}
