package org.sableos.weather.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWeatherCacheTest {
    private fun legacy(name: String?) =
        MemoryStore(
            buildMap {
                put(LegacyWeatherCache.KEY_PROTOCOL, "OK\nCURRENT|1|1|1|1|1|1|CLEAR")
                put(LegacyWeatherCache.KEY_OBSERVED_EPOCH_SECONDS, "123")
                if (name != null) put(LegacyWeatherCache.KEY_LOCATION_NAME, name)
            }
        )

    @Test fun aFreshInstallHasNothingToMigrate() {
        val store = MemoryStore()
        assertFalse(LegacyWeatherCache.migrate(store))
        assertTrue(store.data.isEmpty())
    }

    @Test fun theOldSingleForecastIsDroppedBecauseItsCityIsUnknown() {
        val store = legacy("Mumbai")
        assertTrue(LegacyWeatherCache.migrate(store))
        assertNull(store.get(LegacyWeatherCache.KEY_PROTOCOL))
        assertNull(store.get(LegacyWeatherCache.KEY_OBSERVED_EPOCH_SECONDS))
        assertNull(store.get(LegacyWeatherCache.KEY_LOCATION_NAME))
        DefaultCities.ALL.forEach { assertNull(ForecastCache(store).read(it)) }
    }

    @Test fun theOldSelectionIsKept() {
        val store = legacy("Mumbai")
        LegacyWeatherCache.migrate(store)
        assertEquals("Mumbai", CityRepository(store).load().selected.name)
        assertEquals(DefaultCities.ALL, CityRepository(store).load().cities)
    }

    @Test fun anUnknownOldSelectionFallsBackToTheFirstCity() {
        val store = legacy("Atlantis")
        LegacyWeatherCache.migrate(store)
        assertEquals("Jersey City", CityRepository(store).load().selected.name)
    }

    @Test fun anExistingCityListIsNeverOverwritten() {
        val store = legacy("Mumbai")
        CityRepository(store).add(City("Paris", 48.8566, 2.3522, "Europe/Paris"))
        LegacyWeatherCache.migrate(store)
        assertEquals("Paris", CityRepository(store).load().selected.name)
    }

    @Test fun migrationRunsOnce() {
        val store = legacy("Edison")
        assertTrue(LegacyWeatherCache.migrate(store))
        assertFalse(LegacyWeatherCache.migrate(store))
    }
}
