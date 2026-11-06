package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.phone.core.PhoneSearch.Person

class PhoneSearchTest {
    private val ann = Person(1, "Ann Smith", listOf("+1 555 0101"))
    private val bob =
        Person(2, "Bob Annand", listOf("0207 555 9000", "07700 900123"), starred = true)
    private val carla = Person(3, "Carla Jo", listOf("555 1234"))

    @Test
    fun lettersFindNamesByAnyWordPrefixFirst() {
        assertEquals(listOf(ann, bob), PhoneSearch.find(listOf(carla, bob, ann), "ann"))
        assertEquals(listOf(carla), PhoneSearch.find(listOf(ann, bob, carla), "jo"))
        assertEquals(listOf(ann), PhoneSearch.find(listOf(ann, bob, carla), "as"))
    }

    @Test
    fun digitsFindAnyPartOfAnyNumber() {
        assertEquals(listOf(carla, bob, ann), PhoneSearch.find(listOf(ann, bob, carla), "555"))
        assertEquals(listOf(bob), PhoneSearch.find(listOf(ann, bob, carla), "900123"))
        assertTrue(PhoneSearch.numberMatches("+1 (555) 010-1", "5550"))
        assertFalse(PhoneSearch.numberMatches("123", "9"))
    }

    @Test
    fun emptyQueryKeepsEveryoneStarredFirst() {
        assertEquals(listOf(bob, ann, carla), PhoneSearch.find(listOf(carla, ann, bob), " "))
        assertNull(PhoneSearch.rank(ann, "zzz"))
    }
}
