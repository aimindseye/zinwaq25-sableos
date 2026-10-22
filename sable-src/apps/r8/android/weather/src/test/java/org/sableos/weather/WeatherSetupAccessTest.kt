package org.sableos.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSetupAccessTest {
    private val own = 10_050
    private val other = 10_099

    private fun allowed(uid: Int, allowed: Set<String>, vararg packages: String) =
        WeatherCallers.allowed(uid, own, allowed) { packages.toList() }

    @Test fun weatherItselfIsAlwaysAllowedWithoutAPackageLookup() {
        assertTrue(WeatherCallers.allowed(own, own, emptySet()) { error("not consulted") })
    }

    @Test fun setupMayUseTheCityHandOff() {
        assertTrue(allowed(other, WeatherSetupContract.SETUP_CALLERS, "org.sableos.titan2.setup"))
    }

    @Test fun otherAppsMayNot() {
        val setup = WeatherSetupContract.SETUP_CALLERS
        assertFalse(allowed(other, setup))
        assertFalse(allowed(other, setup, "com.example.weather"))
        assertFalse(allowed(other, setup, "org.sableos.titan2.setupx"))
        assertFalse(allowed(other, setup, "org.sableos.titan2"))
        assertFalse(allowed(other, setup, "org.sableos.launcher"))
    }

    @Test fun setupCannotReadTheLauncherSnapshotAndTheLauncherCannotSetTheCity() {
        assertFalse(allowed(other, WeatherSetupContract.SNAPSHOT_CALLERS, "org.sableos.titan2.setup"))
        assertTrue(allowed(other, WeatherSetupContract.SNAPSHOT_CALLERS, "org.sableos.launcher"))
    }

    @Test fun aSharedUidIsAllowedWhenAnyOfItsPackagesIs() {
        assertTrue(
            allowed(other, WeatherSetupContract.SETUP_CALLERS, "com.example", "org.sableos.titan2.setup"),
        )
    }

    /** Sable Setup's WeatherCityContract mirrors these literals; changing one side breaks the hand-off. */
    @Test fun theContractLiteralsArePinned() {
        assertEquals("setup_search_cities", WeatherSetupContract.METHOD_SEARCH)
        assertEquals("setup_select_city", WeatherSetupContract.METHOD_SELECT)
        assertEquals(
            listOf("names", "selected", "status"),
            listOf(
                WeatherSetupContract.KEY_NAMES,
                WeatherSetupContract.KEY_SELECTED,
                WeatherSetupContract.KEY_STATUS,
            ),
        )
        assertEquals(
            listOf("selected", "unchanged", "unknown_city", "list_full", "failed"),
            listOf(
                WeatherSetupContract.STATUS_SELECTED,
                WeatherSetupContract.STATUS_UNCHANGED,
                WeatherSetupContract.STATUS_UNKNOWN,
                WeatherSetupContract.STATUS_FULL,
                WeatherSetupContract.STATUS_FAILED,
            ),
        )
        assertEquals("org.sableos.weather.snapshot", WeatherSnapshotContract.AUTHORITY)
    }
}
