package org.sableos.reference.weathercities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityStoreTest {
    private class MemoryStore : KeyValueStore {
        val data = mutableMapOf<String, String>()
        var writes = 0

        override fun get(key: String): String? = data[key]

        override fun put(key: String, value: String) {
            writes += 1
            data[key] = value
        }
    }

    private val paris = City("Paris", 48.8566, 2.3522, "Europe/Paris")

    @Test fun anEmptyStoreLoadsTheDefaults() {
        assertEquals(CityState.defaults(), CityRepository(MemoryStore()).load())
    }

    @Test fun theCityListSurvivesARestart() {
        val store = MemoryStore()
        CityRepository(store).add(paris)
        val reopened = CityRepository(store).load()
        assertEquals(5, reopened.cities.size)
        assertEquals(paris, reopened.cities.last())
    }

    @Test fun theSelectedCitySurvivesARestart() {
        val store = MemoryStore()
        CityRepository(store).select("Mumbai")
        assertEquals("Mumbai", CityRepository(store).load().selected.name)
    }

    @Test fun anAddedAndSelectedCityIsTheSelectionAfterARestart() {
        val store = MemoryStore()
        CityRepository(store).add(paris)
        assertEquals(paris, CityRepository(store).load().selected)
    }

    @Test fun aRemovalSurvivesARestart() {
        val store = MemoryStore()
        CityRepository(store).remove("Edison")
        assertFalse(CityRepository(store).load().cities.any { it.name == "Edison" })
    }

    @Test fun resetSurvivesARestartAndTheDefaultsRemain() {
        val store = MemoryStore()
        val repo = CityRepository(store)
        repo.add(paris)
        repo.remove("Mumbai")
        repo.reset()
        val back = CityRepository(store).load()
        assertEquals(DefaultCities.ALL, back.cities)
    }

    @Test fun aRejectedEditWritesNothing() {
        val store = MemoryStore()
        val repo = CityRepository(store)
        repo.add(paris.copy(latitude = 123.0))
        repo.remove("Atlantis")
        repo.select("Atlantis")
        assertEquals(0, store.writes)
        assertNull(store.get(CityRepository.KEY))
    }

    @Test fun theTimezoneIsStoredWithTheCity() {
        val store = MemoryStore()
        CityRepository(store).add(paris)
        assertTrue(store.get(CityRepository.KEY)!!.contains("Europe/Paris"))
        assertEquals("Europe/Paris", CityRepository(store).load().selected.timezone)
    }

    @Test fun codecRoundTripsAwkwardNames() {
        val odd = City("A|B\\C\nD", -33.8688, 151.2093, "Australia/Sydney")
        val s = CityState(listOf(odd), odd.name)
        assertEquals(s, CityCodec.decode(CityCodec.encode(s)))
        val piped = CityState.defaults().add(
            City("A|B\\C", -33.8688, 151.2093, "Australia/Sydney")
        ).state
        assertEquals(piped, CityCodec.decode(CityCodec.encode(piped)))
    }

    @Test fun coordinatesRoundTripExactly() {
        val c = City("Exact", 12.345678901234, -98.765432109876, "UTC")
        assertEquals(
            c,
            CityCodec.decode(CityCodec.encode(CityState.of(listOf(c), "Exact"))).selected
        )
    }

    @Test fun garbageAndUnknownVersionsFallBackToTheDefaults() {
        assertEquals(CityState.defaults(), CityCodec.decode(null))
        assertEquals(CityState.defaults(), CityCodec.decode(""))
        assertEquals(CityState.defaults(), CityCodec.decode("not cities"))
        assertEquals(CityState.defaults(), CityCodec.decode("SABLE_CITIES_V9\nCITY|X|1|2|UTC"))
    }

    @Test fun badLinesAreSkippedAndGoodOnesKept() {
        val text =
            listOf(
                "SABLE_CITIES_V1",
                "SELECTED|Paris",
                "CITY|Paris|48.8|2.3|Europe/Paris",
                "CITY|Bad|abc|2|UTC",
                "CITY|Far|100|2|UTC",
                "CITY|x"
            ).joinToString("\n")
        val s = CityCodec.decode(text)
        assertEquals(listOf("Paris"), s.cities.map { it.name })
        assertEquals("Paris", s.selected.name)
    }

    @Test fun aSelectionThatNoLongerExistsFallsBackToTheFirstCity() {
        val text =
            listOf(
                "SABLE_CITIES_V1",
                "SELECTED|Gone",
                "CITY|Paris|48.8|2.3|Europe/Paris",
                "CITY|Rome|41.9|12.5|Europe/Rome"
            ).joinToString("\n")
        assertEquals("Paris", CityCodec.decode(text).selected.name)
    }

    @Test fun theStoredTextHasNoPersonalOrDeviceData() {
        val store = MemoryStore()
        CityRepository(store).add(paris)
        val text = store.get(CityRepository.KEY)!!
        assertTrue(
            text.lines().all {
                it.startsWith("SABLE_CITIES_V1") || it.startsWith("SELECTED|") ||
                    it.startsWith("CITY|")
            }
        )
    }
}
