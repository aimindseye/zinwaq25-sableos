package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantActionsTest {
    private fun rows(term: String) = InstantActions.forTerm(term)

    private fun actions(term: String) = rows(term).map { it.action }

    @Test
    fun wordsGetRemindNoteSearchMapTextAndEmail() {
        assertEquals(
            listOf(
                InstantAction.RemindMe,
                InstantAction.Note,
                InstantAction.WebSearch,
                InstantAction.Map,
                InstantAction.Text,
                InstantAction.Email,
            ),
            actions("buy milk"),
        )
        assertTrue(InstantActions.forTerm(" buy milk ").all { it.payload == "buy milk" && !it.chosen })
        assertEquals(emptyList<InstantRow>(), InstantActions.forTerm("  "))
    }

    @Test
    fun numbersCallFirstAndAddressesEmailFirst() {
        assertEquals(InstantAction.Call, actions("+1 555 0100").first())
        assertTrue(InstantAction.Map !in actions("5550100"))
        assertEquals(InstantAction.Email, actions("maya@example.org").first())
        assertTrue(InstantAction.Call !in actions("buy milk"))
        assertEquals("maya@example.org", InstantActions.emailAddress(" maya@example.org "))
        assertNull(InstantActions.emailAddress("maya at example"))
    }

    @Test
    fun aKeywordPicksOneActionForEnter() {
        assertEquals(listOf(InstantRow(InstantAction.RemindMe, "call mum", true)), rows("remind me call mum"))
        assertEquals(listOf(InstantRow(InstantAction.Note, "gate code 4512", true)), rows("Note gate code 4512"))
        assertEquals(listOf(InstantRow(InstantAction.WebSearch, "q25 battery", true)), rows("g q25 battery"))
        assertEquals(listOf(InstantRow(InstantAction.Call, "555 0100", true)), rows("call 555 0100"))
        // "call mum" is not a number, and a bare keyword has nothing to act on: every action shows.
        assertTrue(InstantActions.forTerm("call mum").none { it.chosen })
        assertTrue(InstantActions.forTerm("note").none { it.chosen })
        // An app name that starts like a keyword is not one.
        assertTrue(InstantActions.forTerm("notes").none { it.chosen })
    }

    @Test
    fun quickPrefixesMakeATodoANoteOrAnEvent() {
        assertEquals(listOf(InstantRow(InstantAction.RemindMe, "Buy milk", true)), rows("-Buy milk"))
        assertEquals(
            listOf(InstantRow(InstantAction.Note, "Draft the release notes", true)),
            rows("/Draft the release notes"),
        )
        assertEquals(listOf(InstantRow(InstantAction.Event, "Dinner Friday at 7", true)), rows("+Dinner Friday at 7"))
        assertEquals(listOf(InstantRow(InstantAction.RemindMe, "call mum", true)), rows("- call mum"))
        // A prefix with nothing after it does nothing yet.
        assertEquals(emptyList<InstantRow>(), rows("-"))
        assertEquals(emptyList<InstantRow>(), rows("+ "))
        // A + phone number is still a number.
        assertEquals(InstantAction.Call, actions("+44 20 7946 0000").first())
    }

    @Test
    fun aWebAddressOpensInTheBrowser() {
        assertEquals(
            InstantRow(InstantAction.OpenAddress, "https://example.com/page", true),
            rows("example.com/page").first(),
        )
        assertEquals(
            InstantRow(InstantAction.OpenAddress, "https://www.example.org", true),
            rows("www.example.org").first(),
        )
        assertEquals(
            InstantRow(InstantAction.OpenAddress, "http://192.168.1.1.example.net", true),
            rows("http://192.168.1.1.example.net").first(),
        )
        // A bare name with a dot is offered, but Enter does not pick it over the other results.
        assertEquals(InstantRow(InstantAction.OpenAddress, "https://sableos.org", false), rows("sableos.org").first())
        assertNull(InstantActions.webAddress("maya@example.org"))
        assertNull(InstantActions.webAddress("buy milk.com today"))
        assertNull(InstantActions.webAddress("v1.2"))
        assertTrue(InstantAction.OpenAddress !in actions("buy milk"))
    }
}
