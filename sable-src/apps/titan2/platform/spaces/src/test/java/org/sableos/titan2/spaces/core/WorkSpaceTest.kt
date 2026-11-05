package org.sableos.titan2.spaces.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkSpaceTest {
    @Test
    fun stateFollowsTheWorkSpaceThenWhetherAndroidAllowsOne() {
        assertEquals(
            SpaceState.Paused,
            WorkSpace.state(hasWorkSpace = true, paused = true, allowed = false)
        )
        assertEquals(
            SpaceState.On,
            WorkSpace.state(hasWorkSpace = true, paused = false, allowed = false)
        )
        assertEquals(
            SpaceState.None,
            WorkSpace.state(hasWorkSpace = false, paused = false, allowed = true)
        )
        assertEquals(
            SpaceState.NotAllowed,
            WorkSpace.state(hasWorkSpace = false, paused = false, allowed = false)
        )
        SpaceState.entries.forEach { assertTrue(it.name, WorkSpace.detailKey(it) in SpacesText.EN) }
    }

    @Test
    fun onlyPackageNamesAreAcceptedFromAnotherProfile() {
        assertTrue(WorkSpace.isPackageName("com.slack"))
        assertTrue(WorkSpace.isPackageName("org.sableos.titan2.keyboard"))
        assertFalse(WorkSpace.isPackageName(null))
        assertFalse(WorkSpace.isPackageName("slack"))
        assertFalse(WorkSpace.isPackageName("com.slack/.Main"))
        assertFalse(WorkSpace.isPackageName("com..slack"))
        assertFalse(WorkSpace.isPackageName("a." + "b".repeat(300)))
    }

    @Test
    fun theCallersLabelIsShortAndNeverEmpty() {
        assertEquals("com.slack", WorkSpace.shownLabel("  ", "com.slack"))
        assertEquals("com.slack", WorkSpace.shownLabel(null, "com.slack"))
        assertEquals(80, WorkSpace.shownLabel("x".repeat(200), "com.slack").length)
        assertEquals("Slack", WorkSpace.shownLabel(" Slack ", "com.slack"))
    }

    @Test
    fun addableLeavesOutItselfAndAppsAlreadyInWorkSortedByName() {
        val apps = listOf(
            AppChoice("com.zoom", "zoom"),
            AppChoice("org.sableos.titan2.spaces", "Sable Spaces"),
            AppChoice("com.slack", "Slack"),
            AppChoice("com.slack", "Slack"),
            AppChoice("com.mail", "Mail"),
            AppChoice("com.teams", "Teams")
        )
        val shown = WorkSpace.addable(apps, "org.sableos.titan2.spaces", setOf("com.teams"))
        assertEquals(listOf("Mail", "Slack", "zoom"), shown.map { it.label })
    }

    @Test
    fun moveStaysOnTheList() {
        assertEquals(0, WorkSpace.move(0, -1, 3))
        assertEquals(2, WorkSpace.move(2, 1, 3))
        assertEquals(1, WorkSpace.move(0, 1, 3))
        assertEquals(0, WorkSpace.move(5, 1, 0))
    }

    @Test
    fun everyOptionHasItsLineAndStartsAllowed() {
        WorkOption.entries.forEach { assertTrue(it.key, it.key in SpacesText.EN) }
        assertEquals("allowed", WorkSpace.optionValueKey(blocked = false))
        assertEquals("blocked", WorkSpace.optionValueKey(blocked = true))
        assertTrue("allowed" in SpacesText.EN && "blocked" in SpacesText.EN)
        assertEquals(3, WorkOption.entries.size)
    }
}
