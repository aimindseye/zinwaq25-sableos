package org.sableos.weather.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastCacheTest {
    private val ny = DefaultCities.ALL[1]
    private val mumbai = DefaultCities.ALL[3]
    private val paris = City("Paris", 48.8566, 2.3522, "Europe/Paris")
    private val protocol = "OK\nCURRENT|70|71|40|0|5|180|CLEAR\n"

    @Test fun eachCityHasItsOwnEntry() {
        val cache = ForecastCache(MemoryStore())
        cache.write(ny, protocol, 100L)
        cache.write(mumbai, protocol.replace("70", "90"), 200L)
        assertEquals(100L, cache.read(ny)?.observedEpochSeconds)
        assertEquals(200L, cache.read(mumbai)?.observedEpochSeconds)
        assertTrue(cache.read(mumbai)!!.protocol.contains("90"))
    }

    @Test fun aCityWithoutItsOwnForecastShowsNothingNotAnotherCitysForecast() {
        val cache = ForecastCache(MemoryStore())
        cache.write(ny, protocol, 100L)
        assertNull(cache.read(mumbai))
        assertNull(cache.read(paris))
    }

    @Test fun theKeyIsTheLocaleIndependentCacheKey() {
        assertEquals("forecast_v1:40.7128,-74.006,America/New_York", ForecastCache.entryKey(ny))
    }

    @Test fun renamingACityKeepsItsCoordinatesForecast() {
        val cache = ForecastCache(MemoryStore())
        cache.write(ny, protocol, 100L)
        assertEquals(100L, cache.read(ny.copy(name = "NYC"))?.observedEpochSeconds)
    }

    @Test fun anEntryWhoseStoredKeyDoesNotMatchIsRejected() {
        val store = MemoryStore()
        val forged = CachedForecast(StartLiveRefresh.cacheKey(mumbai), protocol, 100L)
        store.put(ForecastCache.entryKey(ny), ForecastCache.encode(forged))
        assertNull(ForecastCache(store).read(ny))
    }

    @Test fun corruptOrEmptyEntriesAreIgnoredNotFatal() {
        val store = MemoryStore()
        val cache = ForecastCache(store)
        listOf("", "garbage", "SABLE_FORECAST_V1\nkey\nnot-a-number\nOK").forEach {
            store.put(ForecastCache.entryKey(ny), it)
            assertEquals(it, null, cache.read(ny))
        }
        cache.write(ny, protocol, 0L)
        assertNull(cache.read(ny))
        cache.write(ny, "  ", 5L)
        assertNull(cache.read(ny))
    }

    @Test fun aProtocolWithManyLinesRoundTrips() {
        val long = protocol + "HOURLY|2026-10-09T10:00|70|10|0|CLEAR\nDAILY|2026-10-09|0|75|60|10|CLEAR\n"
        val cache = ForecastCache(MemoryStore())
        cache.write(ny, long, 42L)
        assertEquals(long, cache.read(ny)?.protocol)
    }

    @Test fun retainOnlyDropsRemovedCitiesAndKeepsOtherKeys() {
        val store = MemoryStore(mapOf(CityRepository.KEY to "x"))
        val cache = ForecastCache(store)
        cache.write(ny, protocol, 1L)
        cache.write(mumbai, protocol, 2L)
        cache.write(paris, protocol, 3L)
        val dropped = cache.retainOnly(listOf(ny, mumbai))
        assertEquals(setOf(ForecastCache.entryKey(paris)), dropped)
        assertNull(cache.read(paris))
        assertEquals(1L, cache.read(ny)?.observedEpochSeconds)
        assertEquals("x", store.get(CityRepository.KEY))
    }

    @Test fun selectingACityNeverReturnsThePreviousCitysForecast() {
        val store = MemoryStore()
        val repo = CityRepository(store)
        val cache = ForecastCache(store)
        cache.write(repo.load().selected, protocol, 10L)
        val edit = repo.select("Mumbai")
        assertTrue(edit.refresh)
        assertNull(cache.read(edit.state.selected))
        assertFalse(StartLiveRefresh.cacheValidFor(StartLiveRefresh.cacheKey(DefaultCities.ALL[0]), edit.state.selected))
    }
}
