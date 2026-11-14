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
        alphabet =
            lowers.flatMap {
                it.asIterable()
            }.filter { Tokens.isWordChar(it) || it == '\'' }.toSortedSet().toCharArray()
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
        return best.sortedByDescending {
            freq[it]
        }.filter { byLower[lowers[it]] == it }.take(limit).map { words[it] }
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
        val ranked = if (w.length in MIN_CHECK..MAX_CHECK && !byLower.containsKey(w)) {
            edits1(w).mapNotNull { byLower[it] }.distinct().sortedByDescending { freq[it] }
                .ifEmpty { if (w.length >= MIN_TWO_EDITS) twoEdits(w) else emptyList() }
        } else {
            emptyList()
        }
        return ranked.take(limit).map { words[it] }
    }

    private fun twoEdits(w: String): List<Int> =
        words.indices.filter { near(w, it) }.sortedByDescending { freq[it] }

    private fun near(w: String, i: Int): Boolean {
        val l = lowers[i]
        if (byLower[l] != i || !sharesStart(l, w)) return false
        return editDistance(w, l, 2) <= 2
    }

    // a cheap filter: a typo rarely changes both of the first two letters
    private fun sharesStart(a: String, b: String): Boolean =
        a[0] == b[0] || a.getOrNull(1) == b.getOrNull(1)

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
            val entries = lines.mapNotNull { entry(it) }.toList()
            return WordList(
                entries.map { it.first }.toTypedArray(),
                entries.map { it.second }.toIntArray(),
                locale
            )
        }

        private fun entry(line: String): Pair<String, Int>? {
            val tab = line.indexOf('\t')
            val fr = if (tab > 0) line.substring(tab + 1).trim().toIntOrNull() else null
            return fr?.let { line.substring(0, tab) to it }
        }

        /** Damerau-Levenshtein distance, or [max] + 1 as soon as it is known to exceed [max]. */
        fun editDistance(a: String, b: String, max: Int): Int {
            if (kotlin.math.abs(a.length - b.length) > max) return max + 1
            var prev2 = IntArray(b.length + 1)
            var prev = IntArray(b.length + 1) { it }
            var i = 1
            var rowMin = 0
            while (i <= a.length && rowMin <= max) {
                val cur = row(a, b, i, prev, prev2)
                rowMin = cur.min()
                prev2 = prev
                prev = cur
                i++
            }
            // a row whose smallest entry is over [max] has nothing at or under it in the last cell either
            return minOf(prev[b.length], max + 1)
        }

        private fun row(a: String, b: String, i: Int, prev: IntArray, prev2: IntArray): IntArray {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (swapped(a, b, i, j)) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
            }
            return cur
        }

        private fun swapped(a: String, b: String, i: Int, j: Int): Boolean =
            i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
    }
}
