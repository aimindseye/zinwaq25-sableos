package org.sableos.phone.core

/**
 * Type-to-find on the Phone screens, BlackBerry style: letters find contacts by any word of the name, digits find
 * contacts and calls by any part of the number. Results that start with what was typed come first.
 */
object PhoneSearch {
    data class Person(
        val id: Long,
        val name: String,
        val numbers: List<String>,
        val starred: Boolean = false
    )

    /** How well [person] matches [query]; null when it does not. Lower is better. */
    fun rank(person: Person, query: String): Int? {
        val q = query.trim()
        if (q.isEmpty()) return RANK_ALL
        return if (isNumberQuery(q)) numberRank(person.numbers, q) else nameRank(person.name, q)
    }

    /** The people matching [query], best first, then starred, then by name. */
    fun find(people: List<Person>, query: String): List<Person> = people
        .mapNotNull { p -> rank(p, query)?.let { p to it } }
        .sortedWith(
            compareBy<Pair<Person, Int>> {
                it.second
            }.thenBy { !it.first.starred }.thenBy { it.first.name.lowercase() }
        )
        .map { it.first }

    /** True when [number] contains the digits of [query] (separators ignored). */
    fun numberMatches(number: String, query: String): Boolean =
        numberRank(listOf(number), query) != null

    fun isNumberQuery(q: String): Boolean =
        q.isNotEmpty() && q.all { it.isDigit() || it in "+*# ()-." }

    private fun numberRank(numbers: List<String>, q: String): Int? {
        val digits = q.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return numbers.mapNotNull { n ->
            val nd = n.filter { it.isDigit() }
            when {
                nd.startsWith(digits) -> RANK_PREFIX
                nd.contains(digits) -> RANK_INSIDE
                else -> null
            }
        }.minOrNull()
    }

    private fun nameRank(name: String, q: String): Int? {
        val n = name.lowercase()
        val query = q.lowercase()
        val words = n.split(' ', '-', '.', ',').filter { it.isNotEmpty() }
        return when {
            n.startsWith(query) -> RANK_PREFIX
            words.any { it.startsWith(query) } -> RANK_WORD
            initials(words).startsWith(query) && query.length > 1 -> RANK_WORD
            n.contains(query) -> RANK_INSIDE
            else -> null
        }
    }

    private fun initials(words: List<String>): String = words.joinToString("") { it.take(1) }

    private const val RANK_PREFIX = 0
    private const val RANK_WORD = 1
    private const val RANK_INSIDE = 2
    private const val RANK_ALL = 3
}
