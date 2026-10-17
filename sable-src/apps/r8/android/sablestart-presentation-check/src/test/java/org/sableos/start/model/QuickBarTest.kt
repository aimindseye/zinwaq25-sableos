package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Base quick bar contract (platform_sable SABLE_START_VISUAL_CONFIRMATION.md). */
class QuickBarTest {
    private val default = QuickBar.DEFAULT

    @Test
    fun neverCustomisedShowsPhoneAndHub() {
        assertEquals(listOf("phone", "hub"), QuickBar.normalize(null))
    }

    @Test
    fun normalizeDropsUnknownDuplicateAndOldSearchAndAppsSlotsAndCapsAtFive() {
        assertEquals(listOf("phone"), QuickBar.normalize(listOf("phone", "bogus", "phone")))
        // Bars saved before All Apps and Search became pages lose those slots.
        assertEquals(listOf("phone", "hub"), QuickBar.normalize(listOf("phone", "hub", "command", "apps")))
        assertEquals(emptyList<String>(), QuickBar.normalize(listOf("command", "apps")))
        assertEquals(false, QuickBar.isValid("command"))
        assertEquals(false, QuickBar.isValid("apps"))
        val tooMany = listOf("phone", "hub", "camera", "app:1", "app:2", "app:3")
        val normalized = QuickBar.normalize(tooMany)
        assertEquals(QuickBar.MAX_SLOTS, normalized.size)
        assertEquals(listOf("phone", "hub", "camera", "app:1", "app:2"), normalized)
    }

    @Test
    fun anEmptyBarIsKept() {
        assertEquals(emptyList<String>(), QuickBar.normalize(emptyList()))
        assertEquals(emptyList<String>(), QuickBar.remove(listOf("phone"), 0))
    }

    @Test
    fun reorderMovesSlotsAndStopsAtTheEdges() {
        assertEquals(listOf("hub", "phone"), QuickBar.move(default, 0, 1))
        assertEquals(default, QuickBar.move(default, 0, -1))
        assertEquals(default, QuickBar.move(default, 1, 1))
    }

    @Test
    fun everySlotCanBeReplacedOrRemoved() {
        assertEquals(listOf("phone", "app:7"), QuickBar.replace(default, 1, "app:7"))
        assertEquals(listOf("camera", "hub"), QuickBar.replace(default, 0, "camera"))
        assertEquals(default, QuickBar.replace(default, 1, "phone"))
        assertEquals(default, QuickBar.replace(default, 1, "command"))
        assertEquals(default, QuickBar.replace(default, 2, "app:7"))
        assertEquals(listOf("phone"), QuickBar.remove(default, 1))
        assertEquals(listOf("hub"), QuickBar.remove(default, 0))
        assertEquals(default, QuickBar.remove(default, 2))
    }

    @Test
    fun addAppendsUpToFiveSlots() {
        val three = QuickBar.add(default, "camera")
        assertEquals(listOf("phone", "hub", "camera"), three)
        assertEquals(three, QuickBar.add(three, "apps"))
        val five = QuickBar.add(QuickBar.add(three, "app:8"), "app:9")
        assertEquals(QuickBar.MAX_SLOTS, five.size)
        assertEquals(five, QuickBar.add(five, "app:10"))
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
    fun q25AltLayerDigitsOpenSlots() {
        // The Q25 has no Fn key: Alt+W/E/R/S/D type 1..5 through the key map.
        val alt = QuickBar.META_ALT_ON
        val keycodeW = 51
        val keycodeD = 32
        assertEquals(0, QuickBar.shortcutSlot(keycodeW, alt, 4, unicode = '1'.code))
        assertEquals(3, QuickBar.shortcutSlot(keycodeD, alt, 4, unicode = '4'.code))
        assertNull(QuickBar.shortcutSlot(keycodeD, alt, 3, unicode = '4'.code))
        // Alt on a key with no digit on its Alt layer is not a shortcut.
        assertNull(QuickBar.shortcutSlot(29, alt, 4, unicode = '@'.code))
        // A plain digit (no modifier) types into Search instead.
        assertNull(QuickBar.shortcutSlot(keycodeW, 0, 4, unicode = '1'.code))
        assertEquals("Alt+1", QuickBar.shortcutLabel(0))
    }

    @Test
    fun altArrowsMoveBetweenStartPages() {
        assertEquals(-1, StartPages.keyDelta(21, QuickBar.META_ALT_ON))
        assertEquals(1, StartPages.keyDelta(22, QuickBar.META_FUNCTION_ON))
        // Plain arrows stay with focus traversal.
        assertNull(StartPages.keyDelta(22, 0))
        assertNull(StartPages.keyDelta(29, QuickBar.META_ALT_ON))
    }

    @Test
    fun pagesRightOfHomeAreAllAppsSearchLiveInThatOrder() {
        assertEquals(0, StartPages.HOME)
        assertEquals(listOf(StartPages.APPS, StartPages.SEARCH, StartPages.LIVE), listOf(1, 2, 3))
        assertEquals(4, StartPages.COUNT)
        // Moving right from Home walks All Apps, Search, Live and stops there.
        var page = StartPages.HOME
        val visited = mutableListOf<Int>()
        repeat(4) {
            page = StartPages.target(page, 1)
            visited += page
        }
        assertEquals(listOf(StartPages.APPS, StartPages.SEARCH, StartPages.LIVE, StartPages.LIVE), visited)
        // Home has nothing to its left.
        assertEquals(StartPages.HOME, StartPages.target(StartPages.HOME, -1))
        assertEquals(StartPages.SEARCH, StartPages.target(StartPages.LIVE, -1))
    }

    @Test
    fun screenNamesOpenTheirPage() {
        assertEquals(StartPages.HOME, StartPages.forScreen("start"))
        assertEquals(StartPages.APPS, StartPages.forScreen("apps"))
        assertEquals(StartPages.SEARCH, StartPages.forScreen("Search"))
        assertEquals(StartPages.LIVE, StartPages.forScreen("live"))
        assertEquals(StartPages.LIVE, StartPages.forScreen("glance"))
        assertNull(StartPages.forScreen("quickbar"))
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
        assertEquals(null, QuickBar.appState(launcherUser = false, inBar = false, tokens = default))
        assertEquals(true, QuickBar.appState(launcherUser = true, inBar = true, tokens = default))
        assertEquals(false, QuickBar.appState(launcherUser = true, inBar = false, tokens = default))
        val full = listOf("phone", "hub", "camera", "app:1", "app:2")
        assertNull(QuickBar.appState(launcherUser = true, inBar = false, tokens = full))
    }

    @Test
    fun toggleAppAddsAtTheEndOrRemovesItsSlot() {
        val added = QuickBar.toggleApp(default, null, "0:com.example/.Main")
        assertEquals(listOf("phone", "hub", "app:0:com.example/.Main"), added)
        assertEquals(default, QuickBar.toggleApp(added, "app:0:com.example/.Main", "0:com.example/.Main"))
    }
}
