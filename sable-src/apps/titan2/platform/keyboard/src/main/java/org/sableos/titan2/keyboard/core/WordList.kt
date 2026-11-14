package org.sableos.titan2.keyboard.core

import java.util.Locale

/**
 * One language's frequency-ranked word list: prefix completion, known-word lookup and spelling corrections. Pure
 * Kotlin and read-only after [parse], so the keyboard and the system spell checker can share one instance across
 * threads.
 *
 * Source lines are `word<TAB>frequency` (higher is more common), already sorted by frequency (see
 * assets/words.NOTICE.txt). Lookups ignore case; a word keeps the case the list gives it (proper nouns).
 */
class WordList private constructor(
    private val words: Array<String>,
    private val freq: IntArray,
    private val locale: Locale
) {
    private val byLower = HashMap<String, Int>(words.size * 2)

    // indices into [words] ordered by lowercase text, for prefix ranges
    private val sorted: IntArray
    private val lowers: Array<String>

    // letters seen in the list, the alphabet for spelling edits
    private val alphabet: CharArray

    init {
        lowers = Array(words.size) { words[it].lowercase(locale) }
        for (i in words.indices) {
            val l = lowers[i]
            val have = byLower[l]
            if (have == null || freq[i] > freq[have]) byLower[l] = i
        }
        sorted = words.indices.sortedBy { lowers[it] }.toIntArray()
        alphabet = lowers.flatMap { it.asIterable() }.filter { it.isLetter() || it == '\'' }.toSortedSet().toCharArray()
    }

    val size: Int get() = words.size

    fun contains(word: String): Boolean = byLower.containsKey(norm(word))

    /** Frequency of [word], 0 when unknown. */
    fun frequency(word: String): Int = byLower[norm(word)]?.let { freq[it] } ?: 0

    private fun norm(w: String) = w.replace('’', '\'').lowercase(locale)

    /** The most common words that start with [prefix] and are longer than it, best first. */
    fun complete(prefix: String, limit: Int): List<String> {
        val p = norm(prefix)
        if (p.isEmpty() || limit <= 0) return emptyList()
        var lo = lowerBound(p)
        val best = ArrayList<Int>()
        while (lo < sorted.size && lowers[sorted[lo]].startsWith(p)) {
            val i = sorted[lo++]
            if (lowers[i].length > p.length) best.add(i)
        }
        // a word listed in two casings: the more common one only
        return best.sortedByDescending { freq[it] }.filter { byLower[lowers[it]] == it }.take(limit).map { words[it] }
    }

    private fun lowerBound(p: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (lowers[sorted[mid]] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Likely intended words for a misspelt [word]: those one edit away (typo, missing, extra or swapped letter) by
     * frequency, else, for words of four or more letters, those two edits away. Empty when [word] is known.
     */
    fun corrections(word: String, limit: Int): List<String> {
        val w = norm(word)
        if (w.length < MIN_CHECK || w.length > MAX_CHECK || byLower.containsKey(w)) return emptyList()
        val one = edits1(w).mapNotNull { byLower[it] }.distinct().sortedByDescending { freq[it] }
        if (one.isNotEmpty() || w.length < MIN_TWO_EDITS) return one.take(limit).map { words[it] }
        val two = ArrayList<Int>()
        for (i in words.indices) {
            val l = lowers[i]
            if (l.length < w.length - 2 || l.length > w.length + 2 || byLower[l] != i) continue
            if (l[0] != w[0] && l.getOrNull(1) != w.getOrNull(1)) continue
            if (editDistance(w, l, 2) <= 2) two.add(i)
        }
        return two.sortedByDescending { freq[it] }.take(limit).map { words[it] }
    }

    private fun edits1(w: String): Set<String> {
        val out = HashSet<String>()
        for (i in 0..w.length) {
            if (i < w.length) out.add(w.removeRange(i, i + 1))
            if (i < w.length - 1) out.add(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
            for (c in alphabet) {
                if (i < w.length) out.add(w.substring(0, i) + c + w.substring(i + 1))
                out.add(w.substring(0, i) + c + w.substring(i))
            }
        }
        out.remove(w)
        return out
    }

    companion object {
        const val MIN_CHECK = 2
        const val MAX_CHECK = 24
        const val MIN_TWO_EDITS = 4

        /** Parses `word<TAB>frequency` lines; malformed lines are skipped. */
        fun parse(lines: Sequence<String>, locale: Locale = Locale.ROOT): WordList {
            val w = ArrayList<String>()
            val f = ArrayList<Int>()
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val fr = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                w.add(line.substring(0, tab))
                f.add(fr)
            }
            return WordList(w.toTypedArray(), f.toIntArray(), locale)
        }

        /** Damerau-Levenshtein distance, or [max] + 1 as soon as it is known to exceed [max]. */
        fun editDistance(a: String, b: String, max: Int): Int {
            if (kotlin.math.abs(a.length - b.length) > max) return max + 1
            var prev2 = IntArray(b.length + 1)
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > max) return max + 1
                prev2 = prev
                prev = cur
            }
            return prev[b.length]
        }
    }
}
