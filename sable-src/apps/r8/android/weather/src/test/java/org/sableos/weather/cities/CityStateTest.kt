package org.sableos.weather.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityStateTest {
    private val paris = City("Paris", 48.8566, 2.3522, "Europe/Paris")
    private val tokyo = City("Tokyo", 35.6762, 139.6503, "Asia/Tokyo")

    private fun names(s: CityState) = s.cities.map { it.name }

    @Test fun defaultsAreTheFourCanonicalCitiesAndTheFirstIsSelected() {
        val s = CityState.defaults()
        assertEquals(listOf("Jersey City", "New York", "Edison", "Mumbai"), names(s))
        assertEquals("Jersey City", s.selected.name)
        assertEquals("Asia/Kolkata", s.cities.last().timezone)
    }

    @Test fun addingACityAppendsAndSelectsItAndAsksForARefresh() {
        val e = CityState.defaults().add(paris)
        assertNull(e.reason)
        assertTrue(e.changed)
        assertEquals("Paris", e.state.selected.name)
        assertEquals(5, e.state.cities.size)
        assertTrue(e.refresh)
    }

    @Test fun anAddedCityKeepsNameCoordinatesAndTimezone() {
        val c = CityState.defaults().add(tokyo).state.selected
        assertEquals(tokyo, c)
    }

    @Test fun addWithoutSelectKeepsTheActiveCityAndNeedsNoRefresh() {
        val e = CityState.defaults().add(paris, select = false)
        assertEquals("Jersey City", e.state.selected.name)
        assertFalse(e.refresh)
    }

    @Test fun theNameIsTrimmedAndWhitespaceCollapsed() {
        val e = CityState.defaults().add(paris.copy(name = "  Port   Louis "))
        assertEquals("Port Louis", e.state.selected.name)
    }

    @Test fun aDuplicateNameIgnoringCaseIsRejected() {
        val e = CityState.defaults().add(paris.copy(name = "  new york "))
        assertEquals(Reason.Duplicate, e.reason)
        assertFalse(e.changed)
        assertEquals(4, e.state.cities.size)
    }

    @Test fun theSamePlaceUnderAnotherNameIsADuplicate() {
        val e = CityState.defaults().add(City("NYC", 40.7129, -74.0061, "America/New_York"))
        assertEquals(Reason.Duplicate, e.reason)
    }

    @Test fun invalidInputIsRejectedWithAReasonAndChangesNothing() {
        val s = CityState.defaults()
        val cases =
            mapOf(
                paris.copy(name = "   ") to Reason.BlankName,
                paris.copy(name = "x".repeat(61)) to Reason.NameTooLong,
                paris.copy(latitude = 90.5) to Reason.LatitudeOutOfRange,
                paris.copy(latitude = -91.0) to Reason.LatitudeOutOfRange,
                paris.copy(longitude = 180.1) to Reason.LongitudeOutOfRange,
                paris.copy(longitude = -181.0) to Reason.LongitudeOutOfRange,
                paris.copy(latitude = Double.NaN) to Reason.NotFinite,
                paris.copy(longitude = Double.POSITIVE_INFINITY) to Reason.NotFinite,
                paris.copy(timezone = "") to Reason.BadTimezone,
                paris.copy(timezone = "Mars/Olympus") to Reason.BadTimezone
            )
        cases.forEach { (city, reason) ->
            val e = s.add(city)
            assertEquals(city.toString(), reason, e.reason)
            assertEquals(s, e.state)
        }
    }

    @Test fun boundaryCoordinatesAreValid() {
        assertNull(CityRules.validate(City("Pole", 90.0, 180.0, "UTC")))
        assertNull(CityRules.validate(City("Pole", -90.0, -180.0, "UTC")))
    }

    @Test fun theTimezoneMustBeAKnownRegionId() {
        assertTrue(CityRules.validTimezone("America/New_York"))
        assertFalse(CityRules.validTimezone("new york"))
    }

    @Test fun theListIsCappedAtTheMaximum() {
        var s = CityState.defaults()
        var i = 0
        while (s.cities.size < CityRules.MAX_CITIES) {
            i += 1
            s = s.add(City("Town$i", 10.0 + i, 20.0 + i, "UTC")).state
        }
        assertEquals(Reason.TooManyCities, s.add(City("Extra", -50.0, -50.0, "UTC")).reason)
    }

    @Test fun removingACityDropsItAndKeepsTheOthers() {
        val e = CityState.defaults().remove("Edison")
        assertEquals(listOf("Jersey City", "New York", "Mumbai"), names(e.state))
        assertEquals("Jersey City", e.state.selected.name)
        assertFalse(e.refresh)
    }

    @Test fun removingTheActiveCitySelectsItsNeighbourAndAsksForARefresh() {
        val s = CityState.defaults().select("New York").state
        val e = s.remove("New York")
        assertEquals("Edison", e.state.selected.name)
        assertTrue(e.refresh)
        val last = CityState.defaults().select("Mumbai").state.remove("Mumbai")
        assertEquals("Edison", last.state.selected.name)
    }

    @Test fun removingAnUnknownCityIsNotFound() {
        assertEquals(Reason.NotFound, CityState.defaults().remove("Atlantis").reason)
    }

    @Test fun theLastCityCannotBeRemoved() {
        var s = CityState.defaults()
        listOf("Jersey City", "New York", "Edison").forEach { s = s.remove(it).state }
        assertEquals(listOf("Mumbai"), names(s))
        val e = s.remove("Mumbai")
        assertEquals(Reason.LastCity, e.reason)
        assertEquals(1, e.state.cities.size)
    }

    @Test fun selectingChangesTheActiveCityAndAsksForARefresh() {
        val e = CityState.defaults().select("mumbai")
        assertEquals("Mumbai", e.state.selected.name)
        assertTrue(e.refresh)
    }

    @Test fun selectingTheActiveCityAgainNeedsNoRefresh() {
        assertFalse(CityState.defaults().select("Jersey City").refresh)
    }

    @Test fun selectingAnUnknownCityIsNotFound() {
        assertEquals(Reason.NotFound, CityState.defaults().select("Atlantis").reason)
    }

    @Test fun resetRestoresTheDefaultsAndDropsUserCities() {
        val s = CityState.defaults().add(paris).state.remove("Edison").state
        val e = s.reset()
        assertEquals(DefaultCities.ALL, e.state.cities)
        assertTrue(DefaultCities.ALL.all { d -> e.state.cities.any { it == d } })
    }

    @Test fun resetKeepsABuiltInSelectionAndFallsBackToTheFirstOtherwise() {
        val onMumbai = CityState.defaults().select(
            "Mumbai"
        ).state.add(paris, select = false).state.reset()
        assertEquals("Mumbai", onMumbai.state.selected.name)
        assertFalse(onMumbai.refresh)
        val onParis = CityState.defaults().add(paris).state.reset()
        assertEquals("Jersey City", onParis.state.selected.name)
        assertTrue(onParis.refresh)
    }

    @Test fun aRemovedDefaultComesBackAfterReset() {
        val s = CityState.defaults().remove("Mumbai").state
        assertFalse(names(s).contains("Mumbai"))
        assertTrue(names(s.reset().state).contains("Mumbai"))
    }

    @Test fun repairingBadInputNeverProducesAnEmptyOrDuplicateList() {
        assertEquals(CityState.defaults(), CityState.of(emptyList(), "x"))
        assertEquals(CityState.defaults(), CityState.of(listOf(paris.copy(latitude = 999.0)), null))
        val s = CityState.of(listOf(paris, paris.copy(name = "paris"), tokyo), "Nowhere")
        assertEquals(listOf("Paris", "Tokyo"), names(s))
        assertEquals("Paris", s.selected.name)
    }
}
