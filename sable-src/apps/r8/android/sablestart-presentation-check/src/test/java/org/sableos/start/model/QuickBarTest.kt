package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The BB10 bottom bar (Phone · Search · Camera) and the Start strip pages. */
class QuickBarTest {
    private val default = QuickBar.DEFAULT

    @Test
    fun neverCustomisedShowsPhoneSearchCamera() {
        assertEquals(listOf("phone", "command", "camera"), QuickBar.normalize(null))
        // The three-page default counts as never customised.
        assertEquals(default, QuickBar.normalize(listOf("phone", "hub", "command", "apps")))
    }

    @Test
    fun normalizeKeepsSearchAndCapsAtThree() {
        assertEquals(listOf("phone", "command"), QuickBar.normalize(listOf("phone", "bogus", "phone")))
        val tooMany = listOf("phone", "hub", "camera", "app:1", "apps")
        assertEquals(listOf("phone", "hub", "command"), QuickBar.normalize(tooMany))
        assertEquals(QuickBar.MAX_SLOTS, QuickBar.normalize(tooMany).size)
    }

    @Test
    fun reorderMovesSlotsAndStopsAtTheEdges() {
        assertEquals(listOf("command", "phone", "camera"), QuickBar.move(default, 0, 1))
        assertEquals(default, QuickBar.move(default, 0, -1))
        assertEquals(default, QuickBar.move(default, 2, 1))
    }

    @Test
    fun phoneAndCameraAreReplaceableButSearchIsNot() {
        assertEquals(listOf("phone", "command", "app:7"), QuickBar.replace(default, 2, "app:7"))
        assertEquals(listOf("apps", "command", "camera"), QuickBar.replace(default, 0, "apps"))
        assertEquals(default, QuickBar.replace(default, 1, "app:7"))
        assertEquals(default, QuickBar.replace(default, 0, "camera"))
        assertEquals(default, QuickBar.remove(default, 1))
        assertEquals(listOf("phone", "command"), QuickBar.remove(default, 2))
    }

    @Test
    fun addGoesBeforeSearchUpToThreeSlots() {
        assertEquals(listOf("phone", "hub", "command"), QuickBar.add(listOf("phone", "command"), "hub"))
        assertEquals(default, QuickBar.add(default, "app:9"))
    }

    @Test
    fun fullBarAddsThroughTheSlotPicker() {
        assertTrue(QuickBar.needsSlotPicker(default))
        assertFalse(QuickBar.needsSlotPicker(listOf("phone", "command")))
        assertEquals("Keep", QuickBar.pickerLine("phone", focused = false, appLabel = "Browser"))
        assertEquals("Replace with Browser", QuickBar.pickerLine("camera", focused = true, appLabel = "Browser"))
        assertEquals("Required path", QuickBar.pickerLine("command", focused = true, appLabel = "Browser"))
    }

    @Test
    fun fnDigitsOpenSlots() {
        val fn = QuickBar.META_FUNCTION_ON
        assertEquals(0, QuickBar.shortcutSlot(QuickBar.KEYCODE_1, fn, 3))
        assertEquals(2, QuickBar.shortcutSlot(QuickBar.KEYCODE_1 + 2, fn, 3))
        assertNull(QuickBar.shortcutSlot(QuickBar.KEYCODE_1 + 3, fn, 3))
        assertNull(QuickBar.shortcutSlot(QuickBar.KEYCODE_1, 0, 3))
    }

    @Test
    fun q25AltLayerDigitsOpenSlots() {
        // The Q25 has no Fn key: Alt+W/E/R type 1..3 through the key map.
        val alt = QuickBar.META_ALT_ON
        val keycodeW = 51
        val keycodeR = 46
        val keycodeS = 47
        assertEquals(0, QuickBar.shortcutSlot(keycodeW, alt, 3, unicode = '1'.code))
        assertEquals(2, QuickBar.shortcutSlot(keycodeR, alt, 3, unicode = '3'.code))
        assertNull(QuickBar.shortcutSlot(keycodeS, alt, 3, unicode = '4'.code))
        assertNull(QuickBar.shortcutSlot(29, alt, 3, unicode = '@'.code))
        assertNull(QuickBar.shortcutSlot(keycodeW, 0, 3, unicode = '1'.code))
        assertEquals("Alt+1", QuickBar.shortcutLabel(0))
    }

    @Test
    fun altArrowsMoveAlongTheStrip() {
        assertEquals(-1, StartPages.keyDelta(21, QuickBar.META_ALT_ON))
        assertEquals(1, StartPages.keyDelta(22, QuickBar.META_FUNCTION_ON))
        assertNull(StartPages.keyDelta(22, 0))
        assertNull(StartPages.keyDelta(29, QuickBar.META_ALT_ON))
    }

    @Test
    fun hubThenActiveFramesThenGridPages() {
        assertEquals(listOf(0, 1, 2), listOf(StartPages.HUB, StartPages.HOME, StartPages.FIRST_GRID))
        assertEquals(3, StartPages.count(0))
        assertEquals(5, StartPages.count(3))
        // Alt+Left from Active Frames opens Hub, Alt+Right the first grid page; the ends hold.
        assertEquals(StartPages.HUB, StartPages.target(StartPages.HOME, -1, 4))
        assertEquals(StartPages.FIRST_GRID, StartPages.target(StartPages.HOME, 1, 4))
        assertEquals(StartPages.HUB, StartPages.target(StartPages.HUB, -1, 4))
        assertEquals(3, StartPages.target(3, 1, 4))
        assertEquals(StartPages.HUB, StartPages.forScreen("Hub"))
        assertEquals(StartPages.FIRST_GRID, StartPages.forScreen("grid"))
        assertEquals(StartPages.HOME, StartPages.forScreen("start"))
    }

    @Test
    fun altUpDownReorderInEditHome() {
        assertEquals(-1, StartPages.reorderDelta(19, QuickBar.META_ALT_ON))
        assertEquals(1, StartPages.reorderDelta(20, QuickBar.META_FUNCTION_ON))
        assertNull(StartPages.reorderDelta(20, 0))
        assertNull(StartPages.reorderDelta(21, QuickBar.META_ALT_ON))
    }

    @Test
    fun digitsOnThePhoneSlotDial() {
        assertEquals('1', QuickBar.dialChar('1'.code))
        assertEquals('#', QuickBar.dialChar('#'.code))
        assertEquals('+', QuickBar.dialChar('+'.code))
        assertNull(QuickBar.dialChar('w'.code))
        assertNull(QuickBar.dialChar(0))
    }

    @Test
    fun printableKeysOnStartSeedSearch() {
        assertEquals('m', StartTypeToSearch.seed('m'.code, 0, repeat = false))
        assertEquals('M', StartTypeToSearch.seed('M'.code, 0x1, repeat = false))
        assertNull(StartTypeToSearch.seed(' '.code, 0, repeat = false))
        assertNull(StartTypeToSearch.seed(0, 0, repeat = false))
        assertNull(StartTypeToSearch.seed('1'.code, QuickBar.META_ALT_ON, repeat = false))
        assertNull(StartTypeToSearch.seed('m'.code, 0, repeat = true))
    }

    @Test
    fun appTokensRoundTrip() {
        assertEquals("0:com.example/.Main", QuickBar.appKey(QuickBar.appToken("0:com.example/.Main")))
        assertNull(QuickBar.appKey("phone"))
        assertNull(QuickBar.appKey("app:"))
    }

    @Test
    fun peekOffersAddOrRemoveOnlyForLauncherUserApps() {
        assertEquals(null, QuickBar.appState(launcherUser = false, inBar = false))
        assertEquals(true, QuickBar.appState(launcherUser = true, inBar = true))
        // A full bar still offers Add: it opens the slot picker.
        assertEquals(false, QuickBar.appState(launcherUser = true, inBar = false))
    }

    @Test
    fun toggleAppAddsBeforeSearchOrRemovesItsSlot() {
        val two = listOf("phone", "command")
        val added = QuickBar.toggleApp(two, null, "0:com.example/.Main")
        assertEquals(listOf("phone", "app:0:com.example/.Main", "command"), added)
        assertEquals(two, QuickBar.toggleApp(added, "app:0:com.example/.Main", "0:com.example/.Main"))
    }
}
