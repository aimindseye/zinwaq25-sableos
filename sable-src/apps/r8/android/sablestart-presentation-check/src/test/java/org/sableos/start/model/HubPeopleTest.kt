package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Hub page's Contacts tab: favourites first, A to Z, letter keys jump. */
class HubPeopleTest {
    private var nextId = 1L

    private fun person(
        name: String,
        starred: Boolean = false,
    ) = HubPerson(id = nextId++, lookupKey = "k$name", name = name, starred = starred, number = null)

    private val people =
        HubPeople.sort(
            listOf(
                person("zoe"),
                person("Bob"),
                person("Émile"),
                person("+44 line"),
                person("Carol", starred = true),
                person("anna"),
                person("Dave"),
                person("Alex", starred = true),
            ),
        )

    @Test
    fun favouritesFirstThenAToZIgnoringCaseAndAccentsOthersLast() {
        assertEquals(
            listOf("Alex", "Carol", "anna", "Bob", "Dave", "Émile", "zoe", "+44 line"),
            people.map { it.name },
        )
        assertEquals(
            listOf("★", "★", "A", "B", "D", "E", "Z", "#"),
            people.map(HubPeople::sectionOf),
        )
    }

    @Test
    fun sectionsStartWhereTheLetterChanges() {
        assertTrue(HubPeople.startsSection(people, 0))
        assertFalse(HubPeople.startsSection(people, 1))
        assertTrue(HubPeople.startsSection(people, 2))
        assertFalse(HubPeople.startsSection(people, 99))
    }

    @Test
    fun aLetterJumpsToItsFirstPersonSkippingFavourites() {
        assertEquals(2, HubPeople.jump(people, 'a'))
        assertEquals(3, HubPeople.jump(people, 'B'))
        assertEquals(5, HubPeople.jump(people, 'e'))
    }

    @Test
    fun aLetterWithNobodyJumpsToTheNextLetter() {
        assertEquals(4, HubPeople.jump(people, 'c'))
        assertEquals(6, HubPeople.jump(people, 'q'))
    }

    @Test
    fun digitsJumpToOthersAndOtherKeysDoNothing() {
        assertEquals(7, HubPeople.jump(people, '4'))
        assertNull(HubPeople.jump(people, ' '))
        assertNull(HubPeople.jump(emptyList(), 'a'))
        assertNull(HubPeople.jump(HubPeople.sort(listOf(person("Al", starred = true))), 'a'))
    }

    @Test
    fun pastTheLastLetterLandsOnOthersOrTheEnd() {
        val noZoe = people.filterNot { it.name == "zoe" }
        assertEquals(noZoe.indexOfFirst { it.name == "+44 line" }, HubPeople.jump(noZoe, 'y'))
        val lettersOnly = noZoe.filterNot { it.name.startsWith("+") }
        assertEquals(lettersOnly.lastIndex, HubPeople.jump(lettersOnly, 'y'))
    }
}
