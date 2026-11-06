package org.sableos.phone.core

import org.sableos.phone.core.PhoneSearch.Person

/**
 * The Favorites tab, BlackBerry 10 style: starred contacts by name, then the people called most often who are not
 * starred. Speed dial letters show beside the favorite they call. Nothing is stored: the stars live in Contacts and
 * the counts come from the call log each time the tab loads.
 */
object Favorites {
    const val FREQUENT_MAX = 8
    const val FREQUENT_MIN_CALLS = 2

    data class Lists(val starred: List<Person>, val frequent: List<Person>) {
        val isEmpty: Boolean get() = starred.isEmpty() && frequent.isEmpty()
    }

    fun build(people: List<Person>, calls: List<CallHistory.Call>): Lists {
        val starred = people.filter { it.starred }.sortedBy { it.name.lowercase() }
        val talked = calls.filter {
            !it.hidden && it.number.any(Char::isDigit) &&
                (it.kind == CallHistory.Kind.INCOMING || it.kind == CallHistory.Kind.OUTGOING)
        }
        val frequent = talked
            .groupBy { CallFormat.matchKey(it.number) }
            .filterValues { it.size >= FREQUENT_MIN_CALLS }
            .values
            .filter { group ->
                starred.none { s ->
                    s.numbers.any { CallFormat.sameNumber(it, group.first().number) }
                }
            }
            .sortedWith(
                compareByDescending<List<CallHistory.Call>> {
                    it.size
                }.thenByDescending { g -> g.maxOf { it.timeMillis } }
            )
            .map { group -> personFor(group.first().number, people) }
            .distinctBy { if (it.id >= 0) "c${it.id}" else CallFormat.matchKey(it.numbers.first()) }
            .take(FREQUENT_MAX)
        return Lists(starred, frequent)
    }

    /** The speed dial letter (upper case) that calls one of [person]'s numbers, or null. */
    fun speedLetter(person: Person, speedDial: Map<Char, SpeedDial.Entry>): Char? =
        speedDial.entries
            .sortedBy { it.key }
            .firstOrNull { (_, entry) ->
                person.numbers.any { CallFormat.sameNumber(it, entry.number) }
            }
            ?.key
            ?.uppercaseChar()

    /** The contact for [number], or a stand-in named by the number itself (id -1). */
    private fun personFor(number: String, people: List<Person>): Person =
        people.firstOrNull { p -> p.numbers.any { CallFormat.sameNumber(it, number) } }
            ?: Person(-1, number, listOf(number))
}
