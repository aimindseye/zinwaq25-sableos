package org.sableos.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.weather.cities.City

/** "Use current location": a fix becomes a rounded place named after a listed city nearby. */
class CurrentPlaceTest {
    private val catalog =
        listOf(
            City("Edison", 40.5187, -74.4121, "America/New_York"),
            City("Boston", 42.3601, -71.0589, "America/New_York"),
        )

    @Test
    fun aFixNearAListedCityTakesItsNameAndIsRoundedToAboutAKilometre() {
        val place = CurrentPlace.place(40.53456, -74.39871, "America/New_York", catalog)
        assertEquals("Edison", place.name)
        assertEquals(40.53, place.latitude, 0.0)
        assertEquals(-74.4, place.longitude, 0.0)
        assertEquals("America/New_York", place.timezone)
    }

    @Test
    fun aFixFarFromEveryListedCityIsCurrentLocation() {
        assertEquals(CurrentPlace.UNNAMED, CurrentPlace.place(0.0, 0.0, "UTC", catalog).name)
    }

    @Test
    fun theStoredPlaceRoundTripsAndBadTextIsIgnored() {
        val place = WeatherLocation("Edison", 40.53, -74.4, "America/New_York")
        assertEquals(place, CurrentPlace.decode(CurrentPlace.encode(place)))
        assertNull(CurrentPlace.decode(null))
        assertNull(CurrentPlace.decode("Edison|95|0|UTC"))
        assertNull(CurrentPlace.decode("Edison|x|0|UTC"))
    }

    @Test
    fun distanceIsGreatCircleKilometres() {
        // Edison to Boston is about 346 km in a straight line.
        val km = CurrentPlace.distanceKm(40.5187, -74.4121, 42.3601, -71.0589)
        assertEquals(346.0, km, 5.0)
    }
}
