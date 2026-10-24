package org.sableos.weather.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pieces Sable Setup's city hand-off relies on: catalog-only search, lookup by name, and [CityState.use]. */
class SetupCityChoiceTest {
    @Test fun listedSearchNeverOffersAManualEntry() {
        assertTrue(CityCatalog.search("Home, 10, 20, Europe/Paris").isNotEmpty())
        assertTrue(CityCatalog.searchListed("Home, 10, 20, Europe/Paris").isEmpty())
    }

    @Test fun listedSearchKeepsPrefixMatchesFirstAndIsBounded() {
        assertEquals("San Francisco", CityCatalog.searchListed("san").first().name)
        assertTrue(CityCatalog.searchListed("a").size <= CityCatalog.MAX_RESULTS)
        assertTrue(CityCatalog.searchListed("   ").isEmpty())
    }

    @Test fun listedSearchStillOffersCitiesAlreadyInTheList() {
        assertEquals("Jersey City", CityCatalog.searchListed("jersey").single().name)
    }

    @Test fun namedFindsOnlyCatalogCities() {
        assertEquals("Tokyo", CityCatalog.named("  tokyo ")?.name)
        assertNull(CityCatalog.named("Atlantis"))
        assertNull(CityCatalog.named(""))
    }

    @Test fun usingAListedCitySelectsItWithoutAddingAnother() {
        val edit = CityState.defaults().use(CityCatalog.named("Mumbai")!!)
        assertTrue(edit.changed)
        assertTrue(edit.refresh)
        assertEquals("Mumbai", edit.state.selected.name)
        assertEquals(DefaultCities.ALL, edit.state.cities)
    }

    @Test fun usingANewCityAddsAndSelectsIt() {
        val edit = CityState.defaults().use(CityCatalog.named("London")!!)
        assertTrue(edit.refresh)
        assertEquals("London", edit.state.selected.name)
        assertEquals(DefaultCities.ALL.size + 1, edit.state.cities.size)
    }

    @Test fun usingTheActiveCityChangesNothingThatNeedsARefresh() {
        val edit = CityState.defaults().use(CityCatalog.named("Jersey City")!!)
        assertTrue(edit.changed)
        assertFalse(edit.refresh)
    }

    @Test fun aFullListIsReportedAndKeepsTheSelection() {
        val full = CityState.of(CityCatalog.ALL.drop(30).take(CityRules.MAX_CITIES), null)
        val edit = full.use(CityCatalog.named("Amsterdam")!!)
        assertEquals(Reason.TooManyCities, edit.reason)
        assertEquals(full, edit.state)
    }

    @Test fun theRepositoryPersistsTheChoice() {
        val store = MemoryStore()
        CityRepository(store).use(CityCatalog.named("Paris")!!)
        assertEquals("Paris", CityRepository(store).load().selected.name)
    }
}
