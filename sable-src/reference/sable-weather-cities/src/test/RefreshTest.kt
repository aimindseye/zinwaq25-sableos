package org.sableos.reference.weathercities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshTest {
    private val ny = DefaultCities.ALL[1]
    private val mumbai = DefaultCities.ALL[3]

    @Test fun manualCitiesNeverNeedALocationPermission() {
        assertFalse(StartLiveRefresh.MANUAL_CITY_PRECISE_LOCATION_REQUIRED)
    }

    @Test fun aChangedActiveCityNeedsARefreshAndANotification() {
        assertTrue(StartLiveRefresh.needsRefresh(ny, mumbai))
        assertTrue(StartLiveRefresh.NOTIFY_SNAPSHOT_ON_ACTIVE_CITY_CHANGE)
    }

    @Test fun theSameCityNeedsNoRefresh() {
        assertFalse(StartLiveRefresh.needsRefresh(ny, ny.copy()))
    }

    @Test fun theFirstCityNeedsARefreshAndNoCityNeedsNone() {
        assertTrue(StartLiveRefresh.needsRefresh(null, ny))
        assertFalse(StartLiveRefresh.needsRefresh(ny, null))
        assertFalse(StartLiveRefresh.needsRefresh(null, null))
    }

    @Test fun aCachedForecastIsOnlyValidForTheCityItWasFetchedFor() {
        val key = StartLiveRefresh.cacheKey(ny)
        assertTrue(StartLiveRefresh.cacheValidFor(key, ny))
        assertFalse(StartLiveRefresh.cacheValidFor(key, mumbai))
        assertFalse(StartLiveRefresh.cacheValidFor(null, ny))
    }

    @Test fun theCacheKeyIsLocaleIndependent() {
        assertEquals("40.7128,-74.006,America/New_York", StartLiveRefresh.cacheKey(ny))
    }

    @Test fun everyEditThatChangesTheActiveCityRequestsARefresh() {
        val s = CityState.defaults()
        assertTrue(s.select("Mumbai").refresh)
        assertTrue(s.add(City("Paris", 48.8566, 2.3522, "Europe/Paris")).refresh)
        assertTrue(s.remove("Jersey City").refresh)
        assertFalse(s.remove("Mumbai").refresh)
    }
}
