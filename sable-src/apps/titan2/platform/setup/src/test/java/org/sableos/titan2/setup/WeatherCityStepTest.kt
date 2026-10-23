package org.sableos.titan2.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.titan2.setup.core.Action
import org.sableos.titan2.setup.core.Observed
import org.sableos.titan2.setup.core.Readiness
import org.sableos.titan2.setup.core.SetupModel
import org.sableos.titan2.setup.core.SetupRows
import org.sableos.titan2.setup.core.Snapshot
import org.sableos.titan2.setup.core.TargetStatus
import org.sableos.titan2.setup.core.WeatherCityContract
import org.sableos.titan2.setup.core.WeatherCityStep

class WeatherCityStepTest {
    private val v = Observed.Value("pkg.example")
    private val ime = Observed.Value("org.sableos.titan2.keyboard/.android.SableImeService")

    private fun rows(city: Observed, t: TargetStatus = TargetStatus.Resolvable) =
        SetupRows.build(Snapshot(v, ime, v, v, city)) { t }

    private fun weather(city: Observed, t: TargetStatus = TargetStatus.Resolvable) =
        rows(city, t).first { it.id == "weather" }

    // --- filter text sent to Weather ---

    @Test fun queryIsTrimmedCollapsedAndBounded() {
        assertEquals("new york", WeatherCityStep.cleanQuery("  new \t  york "))
        assertEquals("", WeatherCityStep.cleanQuery(null))
        assertEquals("ab", WeatherCityStep.cleanQuery("a\u0000b\n"))
        val long = WeatherCityStep.cleanQuery("x".repeat(500))
        assertEquals(WeatherCityStep.MAX_QUERY, long.length)
    }

    // --- names shown from Weather ---

    @Test fun namesAreCleanedDeduplicatedAndCapped() {
        val raw = listOf("Paris", null, "  ", "Paris", "Pu\u0007ne", " Rome ") +
            List(20) { "City $it" }
        val names = WeatherCityStep.cleanNames(raw)
        assertEquals(listOf("Paris", "Pune", "Rome"), names.take(3))
        assertEquals(WeatherCityStep.MAX_SHOWN, names.size)
        assertTrue(WeatherCityStep.cleanNames(null).isEmpty())
        assertEquals(
            WeatherCityStep.MAX_NAME,
            WeatherCityStep.cleanNames(listOf("y".repeat(999))).single().length
        )
    }

    // --- step text ---

    @Test fun skipKeepsTheCurrentCity() {
        assertEquals("Skip · keep Jersey City", WeatherCityStep.skipLabel("Jersey City"))
        assertEquals("Skip", WeatherCityStep.skipLabel(null))
        assertEquals("Current: Jersey City", WeatherCityStep.currentLine("Jersey City"))
    }

    @Test fun onlySelectedOrUnchangedEndTheStep() {
        assertTrue(WeatherCityStep.succeeded(WeatherCityContract.STATUS_SELECTED))
        assertTrue(WeatherCityStep.succeeded(WeatherCityContract.STATUS_UNCHANGED))
        listOf(WeatherCityContract.STATUS_FULL, WeatherCityContract.STATUS_UNKNOWN, "x", null)
            .forEach { assertFalse(it, WeatherCityStep.succeeded(it)) }
    }

    @Test fun everyOutcomeHasADistinctMessage() {
        val outcomes = listOf(
            WeatherCityContract.STATUS_SELECTED,
            WeatherCityContract.STATUS_UNCHANGED,
            WeatherCityContract.STATUS_FULL,
            WeatherCityContract.STATUS_UNKNOWN,
            "failed",
            null
        )
        val messages = outcomes.map { WeatherCityStep.resultMessage(it, "Tokyo") }
        assertEquals(outcomes.size, messages.toSet().size)
        assertTrue(messages.first().contains("Tokyo"))
        assertEquals(WeatherCityStep.UNREACHABLE, WeatherCityStep.resultMessage(null, "Tokyo"))
    }

    @Test fun noStepTextAsksForLocation() {
        val texts = listOf(WeatherCityStep.HELP, WeatherCityStep.NO_MATCH, WeatherCityStep.TITLE)
        assertTrue(WeatherCityStep.HELP.contains("No location permission"))
        texts.forEach { assertFalse(it, it.lowercase().contains("gps")) }
    }

    // --- checklist row ---

    @Test fun theRowIsInformationalWhateverIsObserved() {
        listOf(Observed.Value("Tokyo"), Observed.Absent, Observed.Failed).forEach {
            val r = weather(it)
            assertEquals(Readiness.Informational, r.state)
            assertEquals(Action.WeatherCity, r.action)
            assertEquals("Choose city", SetupRows.buttonLabel(r))
        }
        assertTrue(weather(Observed.Value("Tokyo")).detail.contains("Tokyo"))
    }

    @Test fun theRowNeverChangesReadinessWhileWeatherIsInstalled() {
        val items = rows(Observed.Failed)
        assertTrue(SetupModel.isReady(items))
        assertEquals("3 of 3 confirmed", SetupModel.summary(items))
    }

    @Test fun withoutWeatherTheRowOffersNoButton() {
        val r = weather(Observed.Failed, TargetStatus.Missing)
        assertEquals(Readiness.Unavailable, r.state)
        assertEquals(Action.None, r.action)
    }

    @Test fun anOlderSnapshotDefaultsToAnUnreportedCity() {
        val r = SetupRows.build(Snapshot(v, ime, v, v)) { TargetStatus.Resolvable }
            .first { it.id == "weather" }
        assertEquals(WeatherCityStep.row(Observed.Absent), r)
    }

    /** Sable Weather's WeatherSetupContract pins the same literals; changing one side breaks the hand-off. */
    @Test fun theContractLiteralsArePinned() {
        assertEquals("org.sableos.weather.snapshot", WeatherCityContract.AUTHORITY)
        assertEquals("setup_search_cities", WeatherCityContract.METHOD_SEARCH)
        assertEquals("setup_select_city", WeatherCityContract.METHOD_SELECT)
        assertEquals("names", WeatherCityContract.KEY_NAMES)
        assertEquals("selected", WeatherCityContract.KEY_SELECTED)
        assertEquals("status", WeatherCityContract.KEY_STATUS)
        assertEquals("selected", WeatherCityContract.STATUS_SELECTED)
        assertEquals("unchanged", WeatherCityContract.STATUS_UNCHANGED)
        assertEquals("unknown_city", WeatherCityContract.STATUS_UNKNOWN)
        assertEquals("list_full", WeatherCityContract.STATUS_FULL)
    }
}
