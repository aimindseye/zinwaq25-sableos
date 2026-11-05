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
}
