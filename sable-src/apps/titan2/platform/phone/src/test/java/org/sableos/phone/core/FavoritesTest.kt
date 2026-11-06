package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.phone.core.CallHistory.Call
import org.sableos.phone.core.CallHistory.Kind
import org.sableos.phone.core.PhoneSearch.Person

class FavoritesTest {
    private val ann = Person(1, "Ann Smith", listOf("+44 7700 900101"), starred = true)
    private val bob = Person(2, "Bob Jones", listOf("07700 900202"))
    private val cat = Person(3, "Cat Lee", listOf("555 0303"), starred = true)

    private fun call(
        number: String,
        kind: Kind = Kind.OUTGOING,
        time: Long = 0,
        hidden: Boolean = false
    ) = Call(time, number, null, kind, time, 30, 0, hidden)

    @Test
    fun starredComeFirstByName() {
        val lists = Favorites.build(listOf(cat, bob, ann), emptyList())
        assertEquals(listOf(ann, cat), lists.starred)
        assertTrue(lists.frequent.isEmpty())
    }

    @Test
    fun frequentAreTalkedToTwiceAndNotStarred() {
        val calls = listOf(
            call("07700 900202", time = 5),
            call("+447700900202", Kind.INCOMING, time = 4),
            // Ann is starred already, so her calls do not make her frequent too.
            call("07700 900101", time = 3),
            call("+44 7700 900101", time = 2),
            // Missed and hidden calls do not count.
            call("0123 456789", Kind.MISSED, time = 1),
            call("0123 456789", Kind.MISSED, time = 1),
            call("", hidden = true),
            call("", hidden = true),
            // Once is not often.
            call("0999 888777")
        )
        val lists = Favorites.build(listOf(ann, bob, cat), calls)
        assertEquals(listOf(bob), lists.frequent)
    }

    @Test
    fun frequentStrangersAreNamedByNumberAndMostCalledFirst() {
        val calls = listOf(
            call("0111 222333", time = 9),
            call("0111 222333", time = 8),
            call("0444 555666", time = 7),
            call("0444 555666", time = 6),
            call("0444 555666", time = 5)
        )
        val frequent = Favorites.build(emptyList(), calls).frequent
        assertEquals(listOf("0444 555666", "0111 222333"), frequent.map { it.name })
        assertEquals(-1L, frequent.first().id)
    }

    @Test
    fun frequentIsCapped() {
        val calls = (0 until 20).flatMap { i ->
            List(2) { call("0700 0000%02d".format(i), time = i.toLong()) }
        }
        assertEquals(Favorites.FREQUENT_MAX, Favorites.build(emptyList(), calls).frequent.size)
    }

    @Test
    fun speedDialLetterMatchesAnyNumber() {
        val speed = mapOf(
            'w' to SpeedDial.Entry("07700 900101", "Ann"),
            'e' to SpeedDial.Entry("555 1234", "x")
        )
        assertEquals('W', Favorites.speedLetter(ann, speed))
        assertNull(Favorites.speedLetter(bob, speed))
    }
}
