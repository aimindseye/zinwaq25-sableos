package org.sableos.phone.core

/**
 * BlackBerry speed dial on letter keys: holding a letter calls the number put on it. The contract Sable Start
 * reads (authority, permission, action, extra, column) is the one the Sable Lineage Dialer patch 0710 defined, so
 * Start keeps working when Sable Phone replaces that Dialer.
 */
object SpeedDial {
    const val AUTHORITY = "org.sableos.dialer.speeddial"
    const val PERMISSION = "org.sableos.permission.SPEED_DIAL"
    const val ACTION_SPEED_DIAL = "org.sableos.dialer.action.SPEED_DIAL"
    const val EXTRA_LETTER = "org.sableos.dialer.extra.LETTER"
    const val COLUMN_LETTER = "letter"

    /** One assignment: the number to call and the name shown beside the letter. */
    data class Entry(val number: String, val label: String)

    /** The letter as stored, a-z, or null for anything else. */
    fun normalize(c: Char): Char? = c.lowercaseChar().takeIf { it in 'a'..'z' }

    /** Reads the stored list: one `letter TAB number TAB label` per line; bad lines are skipped. */
    fun parse(stored: String?): Map<Char, Entry> {
        if (stored.isNullOrEmpty()) return emptyMap()
        val out = sortedMapOf<Char, Entry>()
        for (line in stored.split('\n')) {
            val f = line.split('\t')
            val letter = f.takeIf {
                it.size == FIELDS && it[0].length == 1
            }?.let { normalize(it[0][0]) }
            val number = f.getOrNull(1)?.trim().orEmpty()
            if (letter != null && number.isNotEmpty()) {
                out[letter] = Entry(number, clean(f[2]).ifEmpty { number })
            }
        }
        return out
    }

    /** Writes the list in [parse]'s form, A to Z. */
    fun format(entries: Map<Char, Entry>): String =
        entries.toSortedMap().entries.joinToString("\n") { (letter, e) ->
            "$letter\t${clean(e.number)}\t${clean(e.label)}"
        }

    /** Names and numbers never carry the separators of the stored form. */
    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').trim()

    private const val FIELDS = 3
}
