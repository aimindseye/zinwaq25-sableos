package org.sableos.titan2.keyboard.core

/**
 * What the user has typed that the shipped word list does not know, and which word follows which, kept in memory and
 * saved by the Android layer in the keyboard's device-protected preferences. It never leaves the device. A word the
 * list lacks counts as the user's own after [PROMOTE] sightings (so one typo is not remembered); a word pair is
 * offered as a next word after [PROMOTE] sightings too. Both tables are capped, dropping the least used first.
 */
class UserWords(private val maxWords: Int = MAX_WORDS, private val maxPairs: Int = MAX_PAIRS) {
    private class Entry(var form: String, var count: Int)

    private val words = HashMap<String, Entry>()
    private val pairs = HashMap<String, Entry>()

    var dirty = false
        private set

    val size: Int get() = words.size + pairs.size

    /** Whether [word] is one the user has typed often enough to count as theirs. */
    fun isKnown(word: String): Boolean = (words[key(word)]?.count ?: 0) >= PROMOTE

    /** Counts a sighting of [word] the shipped list does not have. */
    fun learnWord(word: String) = bump(words, key(word), word, maxWords)

    /** Counts a sighting of [next] straight after [previous]. */
    fun learnPair(previous: String, next: String) =
        bump(pairs, "${key(previous)} ${key(next)}", next, maxPairs)

    /** The user's own words that start with [prefix] and are longer, most used first. */
    fun complete(prefix: String, limit: Int): List<String> {
        val p = key(prefix)
        return words.entries.filter {
            it.value.count >= PROMOTE && it.key.length > p.length &&
                it.key.startsWith(p)
        }
            .sortedByDescending { it.value.count }.take(limit).map { it.value.form }
    }

    /** Words the user has typed after [previous] at least [PROMOTE] times, most often first. */
    fun next(previous: String, limit: Int): List<String> {
        val head = key(previous) + " "
        return pairs.entries.filter { it.value.count >= PROMOTE && it.key.startsWith(head) }
            .sortedByDescending { it.value.count }.take(limit).map { it.value.form }
    }

    fun clear() {
        words.clear()
        pairs.clear()
        dirty = true
    }

    fun markSaved() {
        dirty = false
    }

    private fun key(w: String) = w.replace('’', '\'').lowercase()

    private fun bump(table: HashMap<String, Entry>, key: String, form: String, cap: Int) {
        val e = table.getOrPut(key) { Entry(form, 0) }
        e.count++
        e.form = form
        dirty = true
        if (table.size > cap) trim(table, cap)
    }

    private fun trim(table: HashMap<String, Entry>, cap: Int) {
        val keep = cap - cap / TRIM_DIVISOR
        val drop = table.entries.sortedBy { it.value.count }.take(table.size - keep).map { it.key }
        drop.forEach { table.remove(it) }
    }

    /** `W<TAB>word<TAB>count` and `P<TAB>previous<TAB>next<TAB>count` lines. */
    fun serialize(): String = buildString {
        words.forEach { (_, e) ->
            append("W\t").append(e.form).append('\t').append(e.count).append('\n')
        }
        pairs.forEach { (k, e) ->
            append("P\t").append(k.substringBefore(' ')).append('\t').append(e.form).append('\t')
                .append(e.count).append('\n')
        }
    }

    fun load(text: String) {
        words.clear()
        pairs.clear()
        text.lineSequence().forEach { read(it.split('\t')) }
        dirty = false
    }

    private fun read(f: List<String>) {
        val count = f.lastOrNull()?.toIntOrNull()
        when {
            count == null -> Unit

            f[0] == "W" && f.size == WORD_FIELDS -> words[key(f[1])] = Entry(f[1], count)

            f[0] == "P" && f.size == PAIR_FIELDS -> pairs["${key(f[1])} ${key(f[2])}"] =
                Entry(f[2], count)
        }
    }

    companion object {
        const val PROMOTE = 2
        const val MAX_WORDS = 1500
        const val MAX_PAIRS = 3000
        private const val TRIM_DIVISOR = 10
        private const val WORD_FIELDS = 3
        private const val PAIR_FIELDS = 4
    }
}
