package org.sableos.weather.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityCatalogTest {
    @Test fun everyCatalogCityIsValidAndUnique() {
        CityCatalog.ALL.forEach { assertEquals(it.name, null, CityRules.validate(it)) }
        val state = CityState.of(CityCatalog.ALL, null)
        assertEquals(CityRules.MAX_CITIES, state.cities.size)
        assertEquals(CityCatalog.ALL.size, CityCatalog.ALL.map { it.name.lowercase() }.toSet().size)
    }

    @Test fun theBuiltInCitiesAreInTheCatalogWithTheSameCoordinates() {
        DefaultCities.ALL.forEach { d -> assertTrue(d.name, d in CityCatalog.ALL) }
    }

    @Test fun prefixMatchesComeFirstThenContains() {
        val names = CityCatalog.search("par").map { it.name }
        assertEquals("Paris", names.first())
        val sea = CityCatalog.search("an").map { it.name }
        assertTrue(sea.isNotEmpty())
        assertTrue(sea.size <= CityCatalog.MAX_RESULTS)
    }

    @Test fun aBlankQueryHasNoResults() {
        assertTrue(CityCatalog.search("   ").isEmpty())
    }

    @Test fun citiesAlreadyInTheListAreLeftOut() {
        val names = CityCatalog.search("new", exclude = DefaultCities.ALL).map { it.name }
        assertTrue(names.contains("Newark"))
        assertTrue(!names.contains("New York"))
    }

    @Test fun aManualEntryIsOfferedFirstWhenValid() {
        val r = CityCatalog.search("Hoboken, 40.7440, -74.0324, America/New_York")
        assertEquals(City("Hoboken", 40.7440, -74.0324, "America/New_York"), r.first())
        assertNull(CityCatalog.parseManual("Nowhere, 99, 0, UTC"))
        assertNull(CityCatalog.parseManual("Nowhere, 1, 2, Not/AZone"))
        assertNull(CityCatalog.parseManual("Nowhere, 1, 2"))
    }

    @Test fun searchIsOfflineAndCaseInsensitive() {
        assertEquals(CityCatalog.search("TOKYO"), CityCatalog.search("tokyo"))
    }
}
