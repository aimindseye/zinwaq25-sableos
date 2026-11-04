package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The BB10 bottom bar (Hub · page dots · Camera) and the Start strip pages. */
class QuickBarTest {
    private val default = QuickBar.DEFAULT

    @Test
    fun neverCustomisedShowsHubAndCamera() {
        assertEquals(listOf("hub", "camera"), QuickBar.normalize(null))
        // The three-page default and the Phone · Search · Camera bar count as never customised.
        assertEquals(default, QuickBar.normalize(listOf("phone", "hub", "command", "apps")))
        assertEquals(default, QuickBar.normalize(listOf("phone", "command", "camera")))
    }

    @Test
    fun normalizeDropsJunkAndCapsAtTwo() {
        assertEquals(listOf("phone"), QuickBar.normalize(listOf("phone", "bogus", "phone")))
        val tooMany = listOf("phone", "hub", "camera", "app:1", "apps")
        assertEquals(listOf("phone", "hub"), QuickBar.normalize(tooMany))
        assertEquals(QuickBar.MAX_SLOTS, QuickBar.normalize(tooMany).size)
        // Search is never forced into the bar: typing and Alt+Space open it.
        assertEquals(listOf("hub"), QuickBar.normalize(listOf("hub")))
    }

    @Test
    fun reorderSwapsTheEnds() {
        assertEquals(listOf("camera", "hub"), QuickBar.move(default, 0, 1))
        assertEquals(default, QuickBar.move(default, 0, -1))
        assertEquals(default, QuickBar.move(default, 1, 1))
    }

    @Test
    fun everySlotIsReplaceable() {
        assertEquals(listOf("hub", "app:7"), QuickBar.replace(default, 1, "app:7"))
        assertEquals(listOf("command", "camera"), QuickBar.replace(default, 0, "command"))
        assertEquals(default, QuickBar.replace(default, 0, "camera"))
        assertEquals(listOf("hub"), QuickBar.remove(default, 1))
    }

    @Test
    fun addGoesBeforeSearchUpToTwoSlots() {
        assertEquals(listOf("hub", "command"), QuickBar.add(listOf("command"), "hub"))
        assertEquals(listOf("hub", "camera"), QuickBar.add(listOf("hub"), "camera"))
        assertEquals(default, QuickBar.add(default, "app:9"))
    }

    @Test
    fun fullBarAddsThroughTheSlotPicker() {
        assertTrue(QuickBar.needsSlotPicker(default))
        assertFalse(QuickBar.needsSlotPicker(listOf("hub")))
        assertEquals("Keep", QuickBar.pickerLine("hub", focused = false, appLabel = "Browser"))
        assertEquals("Replace with Browser", QuickBar.pickerLine("camera", focused = true, appLabel = "Browser"))
    }

    @Test
    fun fnDigitsOpenSlots() {
        val fn = QuickBar.META_FUNCTION_ON
        assertEquals(0, QuickBar.shortcutSlot(QuickBar.KEYCODE_1, fn, 2))
        assertEquals(1, QuickBar.shortcutSlot(QuickBar.KEYCODE_1 + 1, fn, 2))
        assertNull(QuickBar.shortcutSlot(QuickBar.KEYCODE_1 + 2, fn, 2))
        assertNull(QuickBar.shortcutSlot(QuickBar.KEYCODE_1, 0, 2))
    }

    @Test
    fun q25AltLayerDigitsOpenSlots() {
        // The Q25 has no Fn key: Alt+W/E type 1 and 2 through the key map.
        val alt = QuickBar.META_ALT_ON
        val keycodeW = 51
        val keycodeE = 33
        val keycodeR = 46
        assertEquals(0, QuickBar.shortcutSlot(keycodeW, alt, 2, unicode = '1'.code))
        assertEquals(1, QuickBar.shortcutSlot(keycodeE, alt, 2, unicode = '2'.code))
        assertNull(QuickBar.shortcutSlot(keycodeR, alt, 2, unicode = '3'.code))
        assertNull(QuickBar.shortcutSlot(29, alt, 2, unicode = '@'.code))
        assertNull(QuickBar.shortcutSlot(keycodeW, 0, 2, unicode = '1'.code))
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
        val one = listOf("command")
        val added = QuickBar.toggleApp(one, null, "0:com.example/.Main")
        assertEquals(listOf("app:0:com.example/.Main", "command"), added)
        assertEquals(one, QuickBar.toggleApp(added, "app:0:com.example/.Main", "0:com.example/.Main"))
    }
}
