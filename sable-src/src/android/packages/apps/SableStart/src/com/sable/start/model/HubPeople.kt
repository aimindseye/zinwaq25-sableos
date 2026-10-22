package org.sableos.start.model

import java.text.Normalizer
import java.util.Locale

/** One person on the Hub page's Contacts tab. Names and one number only; nothing is stored. */
data class HubPerson(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    /** The number Call and Message use (the default, else the first); null when none. */
    val number: String?,
)

/**
 * The Hub page's Contacts tab, as the Contacts app orders it: favourites first, then A to Z by
 * name, names that do not start with a letter last ("#"). A letter key jumps to the first person
 * of that letter (or of the next letter that has someone). Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check.
 */
object HubPeople {
    const val FAVOURITES = "★"
    const val OTHER = "#"

    fun sort(people: List<HubPerson>): List<HubPerson> =
        people.sortedWith(
            compareByDescending<HubPerson> { it.starred }
                .thenBy { sectionOf(it) == OTHER }
                .thenBy { sortKey(it.name) }
                .thenBy { it.name }
                .thenBy { it.id },
        )

    /** "★" for a favourite, else the name's first letter A-Z (accents dropped), else "#". */
    fun sectionOf(person: HubPerson): String = if (person.starred) FAVOURITES else letterOf(person.name)

    /** Whether the row at [index] of [sorted] starts a section (and so shows its header). */
    fun startsSection(
        sorted: List<HubPerson>,
        index: Int,
    ): Boolean =
        index in sorted.indices && (index == 0 || sectionOf(sorted[index - 1]) != sectionOf(sorted[index]))

    /**
     * The index a letter or digit key jumps to in [sorted] (favourites are skipped: they are
     * already at the top), or null for any other key or an empty list.
     */
    fun jump(
        sorted: List<HubPerson>,
        char: Char,
    ): Int? {
        if (!char.isLetterOrDigit()) return null
        val target = letterOf(char.toString())
        val listed = sorted.indices.filter { !sorted[it].starred }
        if (listed.isEmpty()) return null
        return listed.firstOrNull { sectionOf(sorted[it]) == target }
            ?: if (target == OTHER) {
                null
            } else {
                listed.firstOrNull { sectionOf(sorted[it]).let { s -> s != OTHER && s > target } }
                    ?: listed.firstOrNull { sectionOf(sorted[it]) == OTHER }
                    ?: listed.last()
            }
    }

    private fun letterOf(name: String): String {
        val first = sortKey(name).firstOrNull() ?: return OTHER
        return if (first in 'A'..'Z') first.toString() else OTHER
    }

    private fun sortKey(name: String): String =
        Normalizer
            .normalize(name.trim(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .uppercase(Locale.ROOT)

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
