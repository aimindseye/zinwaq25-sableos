package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** BB10 layout: Active Frames, the app grid and Dual apps. */
class Bb10HomeTest {
    private fun frame(
        id: Int,
        pkg: String,
    ) = ActiveFrame(taskId = id, packageName = pkg, className = null, userId = 0, running = true)

    @Test
    fun aPlayingAppGetsControlsOnItsOwnFrame() {
        val frame = frame(1, "com.pod")
        val pod = FrameMedia("com.pod", "Ep 1", playing = true, canPrevious = true, canNext = false)
        val other = FrameMedia("com.music", "Song", playing = false, canPrevious = true, canNext = true)
        assertEquals(pod, ActiveFrames.mediaFor(frame, listOf(other, pod, pod.copy(title = "older")), 0))
        assertNull(ActiveFrames.mediaFor(frame, listOf(other), 0))
        // A work-profile frame: Android lists only the current user's sessions.
        assertNull(ActiveFrames.mediaFor(frame.copy(userId = 10), listOf(pod), 0))
        assertTrue(ActiveFrames.isPlayPauseKey(62, 0))
        assertTrue(ActiveFrames.isPlayPauseKey(85, 0))
        assertFalse(ActiveFrames.isPlayPauseKey(62, 0x2))
    }

    @Test
    fun framesAreTheEightMostRecentTasksWithoutStartOrRecents() {
        val tasks =
            listOf(frame(1, "org.sableos.launcher"), frame(2, "a"), frame(2, "a"), frame(3, "com.android.launcher3")) +
                (4..12).map { frame(it, "app$it") }
        val frames = ActiveFrames.select(tasks, setOf("org.sableos.launcher", "com.android.launcher3"))
        assertEquals(ActiveFrames.MAX, frames.size)
        assertEquals(listOf(2, 4, 5, 6, 7, 8, 9, 10), frames.map { it.taskId })
    }

    @Test
    fun privateSpaceTasksNeverBecomeFrames() {
        val tasks = listOf(frame(1, "a"), frame(2, "b").copy(userId = 11), frame(3, "c"))
        assertEquals(listOf(1, 3), ActiveFrames.select(tasks, emptySet(), setOf(11)).map { it.taskId })
        assertEquals(listOf(1, 2, 3), ActiveFrames.select(tasks, emptySet()).map { it.taskId })
    }

    @Test
    fun xOrBackspaceCloseAFrame() {
        assertTrue(ActiveFrames.isCloseKey(52, 0))
        assertTrue(ActiveFrames.isCloseKey(67, 0))
        assertTrue(ActiveFrames.isCloseKey(52, 0x1))
        assertFalse(ActiveFrames.isCloseKey(52, 0x2))
        assertFalse(ActiveFrames.isCloseKey(66, 0))
    }

    @Test
    fun focusMovesToTheNextFrameAfterClose() {
        assertEquals(0, ActiveFrames.focusAfterClose(0, 3))
        assertEquals(1, ActiveFrames.focusAfterClose(2, 3))
        assertNull(ActiveFrames.focusAfterClose(0, 1))
    }

    @Test
    fun gridKeepsTheSavedOrderAndAppendsNewApps() {
        val installed = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), AppGrid.resolve(null, listOf("c", "x"), installed))
        assertEquals(listOf("d", "b", "a", "c"), AppGrid.resolve(listOf("d", "gone", "b"), listOf("c"), installed))
    }

    @Test
    fun gridPagesHoldFiveColumns() {
        assertEquals(5, AppGrid.COLUMNS)
        // Q25 at density 280: about 387dp under the status bar, less dots, bar and padding.
        assertEquals(4, AppGrid.rowsFor(387 - 72, 74))
        assertEquals(AppGrid.MIN_ROWS, AppGrid.rowsFor(100, 74))
        assertEquals(AppGrid.MAX_ROWS, AppGrid.rowsFor(2000, 74))
        assertEquals(20, AppGrid.perPage(4))
        assertEquals(1, AppGrid.pageCount(0, 20))
        assertEquals(2, AppGrid.pageCount(21, 20))
        assertEquals(listOf(20, 21), AppGrid.page((0..21).toList(), 1, 20))
    }

    @Test
    fun gridIconsFollowTheCell() {
        // 411dp less 8dp padding over five columns, four rows of ~79dp, a 31dp label block.
        assertEquals(44, AppGrid.iconSizeFor(80, 78, 31))
        assertEquals(40, AppGrid.iconSizeFor(80, 71, 31))
        assertEquals(AppGrid.MIN_ICON_DP, AppGrid.iconSizeFor(40, 40, 31))
        assertEquals(AppGrid.MAX_ICON_DP, AppGrid.iconSizeFor(200, 200, 31))
    }

    @Test
    fun gridLabelsDropTheSablePrefixUnlessTheNameIsTaken() {
        assertEquals("Calculator", AppGrid.gridLabel("Sable Calculator"))
        assertEquals("2048", AppGrid.gridLabel(" Sable 2048 "))
        assertEquals("Files", AppGrid.gridLabel("Files"))
        assertEquals("Sable", AppGrid.gridLabel("Sable"))
        assertEquals("Sablefish", AppGrid.gridLabel("Sablefish"))
        assertEquals("Sable Camera", AppGrid.gridLabel("Sable Camera", setOf("Sable Camera", "camera")))
        assertEquals("Hub", AppGrid.gridLabel("Sable Hub", setOf("Sable Hub", "Camera")))
    }

    @Test
    fun gridMovesShiftLikeADraggedIcon() {
        val keys = listOf("a", "b", "c", "d", "e", "f")
        assertEquals(listOf("b", "a", "c", "d", "e", "f"), AppGrid.move(keys, 0, 1))
        assertEquals(listOf("b", "c", "d", "e", "f", "a"), AppGrid.move(keys, 0, AppGrid.COLUMNS))
        assertEquals(listOf("f", "a", "b", "c", "d", "e"), AppGrid.move(keys, 5, -10))
        assertEquals(keys, AppGrid.move(keys, 0, -1))
        assertEquals(-5, AppGrid.arrowDelta(19))
        assertEquals(1, AppGrid.arrowDelta(22))
        assertNull(AppGrid.arrowDelta(66))
    }

    @Test
    fun shiftEnterFindsTheDualCopy() {
        data class App(
            val component: String,
            val profile: String?,
        )
        val chat = App("chat/.Main", null)
        val chatDual = App("chat/.Main", AppProfiles.DUAL)
        val work = App("chat/.Main", AppProfiles.WORK)
        val apps = listOf(chat, work, chatDual, App("mail/.Main", null))
        assertEquals(chatDual, AppProfiles.secondInstance(chat, apps, { it.component }, { it.profile }))
        assertEquals(chat, AppProfiles.secondInstance(chatDual, apps, { it.component }, { it.profile }))
        assertNull(AppProfiles.secondInstance(work, apps, { it.component }, { it.profile }))
        assertNull(AppProfiles.secondInstance(apps[3], apps, { it.component }, { it.profile }))
        assertEquals("Dual", AppProfiles.badge(AppProfiles.DUAL))
        assertEquals("Work", AppProfiles.badge(AppProfiles.WORK))
    }

    @Test
    fun balanceSplitsWorkAppsFromPersonalOnes() {
        assertEquals(emptyList<BalanceSpace>(), Balance.spaces(hasWorkProfile = false))
        assertEquals(listOf(BalanceSpace.Personal, BalanceSpace.Work), Balance.spaces(hasWorkProfile = true))
        assertTrue(Balance.inSpace(null, BalanceSpace.Personal))
        assertTrue(Balance.inSpace(AppProfiles.DUAL, BalanceSpace.Personal))
        assertTrue(Balance.inSpace(AppProfiles.PRIVATE, BalanceSpace.Personal))
        assertFalse(Balance.inSpace(AppProfiles.WORK, BalanceSpace.Personal))
        assertTrue(Balance.inSpace(AppProfiles.WORK, BalanceSpace.Work))
        assertFalse(Balance.inSpace(null, BalanceSpace.Work))
        assertEquals(BalanceSpace.Work, Balance.next(BalanceSpace.Personal))
        assertEquals(BalanceSpace.Personal, Balance.next(BalanceSpace.Work))
    }

    @Test
    fun aMostlySidewaysSwipeRightOnHubOpensHubSettings() {
        assertTrue(HubSwipe.opensSettings(dragX = 200f, dragY = 30f, thresholdPx = 180f))
        assertFalse(HubSwipe.opensSettings(dragX = 120f, dragY = 0f, thresholdPx = 180f))
        assertFalse(HubSwipe.opensSettings(dragX = -400f, dragY = 0f, thresholdPx = 180f))
        assertFalse(HubSwipe.opensSettings(dragX = 200f, dragY = 150f, thresholdPx = 180f))
    }

    @Test
    fun theSettingsFrameShowsDeviceMonitorRows() {
        assertTrue(DeviceMonitor.isMonitorFrame("com.android.settings"))
        assertFalse(DeviceMonitor.isMonitorFrame("org.sableos.weather"))
        assertEquals("3.1 GB", DeviceMonitor.gb(3_140_000_000L))
        assertEquals("6 GB", DeviceMonitor.gb(6_000_000_000L))
        assertEquals("41 GB", DeviceMonitor.gb(41_400_000_000L))
        val text = { key: String, args: Array<out Any> ->
            when (key) {
                "dm_charging" -> "${args[0]}, charging"
                "dm_used_of" -> "${args[0]} of ${args[1]}"
                "dm_free" -> "${args[0]} free"
                else -> key
            }
        }
        val stats = MonitorStats(82, 0.25f, true, 3_100_000_000L, 6_000_000_000L, 40_000_000_000L, 128_000_000_000L)
        val rows = DeviceMonitor.rows(stats, text)
        assertEquals(listOf("dm_battery", "dm_cpu", "dm_memory", "dm_storage"), rows.map { it.labelKey })
        assertEquals(listOf("82%, charging", "25%", "3.1 GB of 6 GB", "40 GB free"), rows.map { it.value })
        assertEquals(0.82f, rows[0].fill, 0.001f)
        assertEquals(0.6875f, rows[3].fill, 0.001f)
        val unknown = DeviceMonitor.rows(MonitorStats(null, null, false, 0, 0, 0, 0), text)
        assertEquals(listOf("?", "0 GB of 0 GB", "0 GB free"), unknown.map { it.value })
        assertEquals(0f, unknown[1].fill, 0f)
    }
}
