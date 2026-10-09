package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelTest {
    private val allPresent = Hardware.entries.associateWith { Probe.PRESENT }

    private fun tools(groups: List<HomeGroup>, s: Section) = groups.firstOrNull {
        it.section == s
    }?.items?.map { it.tool }.orEmpty()

    @Test fun threeSectionsInOrder() {
        val g = HomeModel.build(
            HomeInputs(ToolsDeviceProfile.Titan2, allPresent, developerMode = false)
        )
        assertEquals(listOf("Utilities", "Diagnostics", "Reports"), g.map { it.title })
    }

    @Test fun q25NormalModeShowsNoUnprovenUtilities() {
        val g = HomeModel.build(
            HomeInputs(ToolsDeviceProfile.ZinwaQ25, allPresent, developerMode = false)
        )
        assertTrue(tools(g, Section.UTILITIES).isEmpty())
        assertEquals(listOf("Diagnostics", "Reports"), g.map { it.title })
        assertFalse(Tool.CAPABILITIES in tools(g, Section.DIAGNOSTICS))
        assertFalse(Tool.BUGREPORT in tools(g, Section.REPORTS))
    }

    @Test fun q25DeveloperModeShowsQualificationToolsButNeverIr() {
        val g = HomeModel.build(
            HomeInputs(ToolsDeviceProfile.ZinwaQ25, allPresent, developerMode = true)
        )
        val u = tools(g, Section.UTILITIES)
        assertTrue(Tool.COMPASS in u)
        assertTrue(Tool.FLASHLIGHT in u)
        assertFalse(Tool.IR_REMOTE in u)
        assertTrue(Tool.CAPABILITIES in tools(g, Section.DIAGNOSTICS))
        // Factory bridge is UNKNOWN on Q25: hidden even in developer mode.
        assertFalse(Tool.FACTORY_BRIDGE in tools(g, Section.DIAGNOSTICS))
        g.first {
            it.section == Section.UTILITIES
        }.items.forEach { assertEquals(Badge.DEVELOPER_ONLY, it.badge) }
    }

    @Test fun titan2ListsIrRemoteWithoutPermissionPrompt() {
        val g = HomeModel.build(
            HomeInputs(ToolsDeviceProfile.Titan2, allPresent, developerMode = false)
        )
        val ir = g.first {
            it.section == Section.UTILITIES
        }.items.first { it.tool == Tool.IR_REMOTE }
        assertEquals(Badge.AVAILABLE, ir.badge)
        val noise = g.first { it.section == Section.UTILITIES }.items.first {
            it.tool ==
                Tool.NOISE_METER
        }
        assertEquals(Badge.NEEDS_PERMISSION, noise.badge)
    }

    @Test fun noUnavailableBadgeOnHome() {
        listOf(
            ToolsDeviceProfile.Titan2,
            ToolsDeviceProfile.ZinwaQ25,
            ToolsDeviceProfile.Unknown
        ).forEach { p ->
            listOf(true, false).forEach { dev ->
                HomeModel.build(HomeInputs(p, allPresent, dev)).flatMap { it.items }.forEach {
                    assertTrue("${p.id} ${it.tool}", it.badge != Badge.UNAVAILABLE)
                }
            }
        }
    }

    @Test fun recentsBoundedAndOnlyListedTools() {
        val recents =
            listOf(
                Tool.IR_REMOTE,
                Tool.RADIO,
                Tool.KEY_VIEWER,
                Tool.STORAGE,
                Tool.NETWORK,
                Tool.APPS
            )
        val g = HomeModel.build(
            HomeInputs(ToolsDeviceProfile.ZinwaQ25, allPresent, false, recents = recents)
        )
        val r = g.first().items.map { it.tool }
        assertEquals("Recent", g.first().title)
        assertEquals(listOf(Tool.RADIO, Tool.KEY_VIEWER, Tool.STORAGE, Tool.NETWORK), r)
    }

    @Test fun typeToFilter() {
        val g = HomeModel.build(
            HomeInputs(ToolsDeviceProfile.Titan2, allPresent, false, filter = "key")
        )
        val all = g.flatMap { it.items }.map { it.tool }
        assertTrue(Tool.KEY_VIEWER in all)
        assertTrue(Tool.KEYBOARD_PROFILE in all)
        assertFalse(Tool.COMPASS in all)
        assertTrue(g.none { it.title == "Recent" })
        assertTrue(HomeModel.matches(Tool.NOISE_METER, "db"))
        assertTrue(HomeModel.matches(Tool.FLASHLIGHT, "torch"))
        assertTrue(HomeModel.matches(Tool.HARDWARE_TESTS, "factory test"))
        assertFalse(HomeModel.matches(Tool.FLASHLIGHT, "zz"))
    }

    @Test fun recentsCodecRoundTrip() {
        val r = HomeModel.pushRecent(listOf(Tool.RADIO, Tool.COMPASS), Tool.COMPASS)
        assertEquals(listOf(Tool.COMPASS, Tool.RADIO), r)
        assertEquals(r, HomeModel.decodeRecents(HomeModel.encodeRecents(r)))
        assertEquals(listOf(Tool.RADIO), HomeModel.decodeRecents("RADIO,NOT_A_TOOL,RADIO"))
    }

    @Test fun profileSummaryNamesTheProfile() {
        val i = HomeInputs(ToolsDeviceProfile.ZinwaQ25, allPresent, developerMode = false)
        assertEquals(
            "Zinwa Q25 · zinwa-q25 · 0 utilities",
            HomeModel.profileSummary(i, HomeModel.build(i))
        )
    }

    @Test fun permissionsAreJustInTime() {
        assertTrue(PermissionPlan.atLaunch.isEmpty())
        assertEquals(
            listOf(Perm.RECORD_AUDIO),
            PermissionPlan.missingFor(Tool.NOISE_METER, emptySet())
        )
        assertTrue(PermissionPlan.missingFor(Tool.NOISE_METER, setOf(Perm.RECORD_AUDIO)).isEmpty())
        assertTrue(PermissionPlan.missingFor(Tool.COMPASS, emptySet()).isEmpty())
        assertTrue(PermissionPlan.usableWithout(Tool.PEDOMETER, listOf(Perm.ACTIVITY_RECOGNITION)))
        assertFalse(PermissionPlan.usableWithout(Tool.MAGNIFIER, listOf(Perm.CAMERA)))
        // Diagnostics and reports never need a runtime permission.
        Tool.entries.filter {
            it.section != Section.UTILITIES
        }.forEach { assertTrue(it.name, it.permissions.isEmpty()) }
    }

    @Test fun catalogHasNoDeviceModelNames() {
        val models = listOf("titan", "q25", "q27", "zinwa", "unihertz")
        Tool.entries.forEach { t ->
            models.forEach { assertFalse("${t.name} mentions $it", t.searchText.contains(it)) }
        }
    }
}
