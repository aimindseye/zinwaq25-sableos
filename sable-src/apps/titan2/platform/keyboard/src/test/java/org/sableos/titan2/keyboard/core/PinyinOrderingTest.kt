package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Candidate ordering is deterministic. The weight in pinyin_zh.tsv is a neutral ranking weight, not a word
 * frequency (NUMERIC_WEIGHT_IS_CORPUS_FREQUENCY=NO); ties are broken by word code point.
 */
class PinyinOrderingTest {
    private fun cp(s: String) = s.codePoints().toArray().toList()

    @Test fun shippedAssetIsSortedByKeyThenWeightDescendingThenCodePoint() {
        val rows = PinyinTestAsset.find().readLines(Charsets.UTF_8).map { it.split('\t') }
        assertTrue(rows.isNotEmpty())
        var prev: List<String>? = null
        val seen = HashSet<String>()
        for (r in rows) {
            assertEquals(3, r.size)
            assertTrue("duplicate ${r[0]} ${r[1]}", seen.add(r[0] + "\t" + r[1]))
            val p = prev
            if (p != null) assertTrue("order ${p[0]} ${p[1]} -> ${r[0]} ${r[1]}", inOrder(p, r))
            prev = r
        }
    }

    private fun inOrder(a: List<String>, b: List<String>): Boolean {
        val byKey = a[0].compareTo(b[0])
        val byWeight = b[2].toInt().compareTo(a[2].toInt())
        return when {
            byKey != 0 -> byKey < 0
            byWeight != 0 -> byWeight < 0
            else -> compareCodePoints(cp(a[1]), cp(b[1])) < 0
        }
    }

    private fun compareCodePoints(x: List<Int>, y: List<Int>): Int {
        for (i in 0 until minOf(x.size, y.size)) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return x.size.compareTo(y.size)
    }

    @Test fun exactCandidatesAreStableAcrossRepeatedLoadsAndLookups() {
        val lines = PinyinTestAsset.find().readLines(Charsets.UTF_8)
        val a = PinyinDict.parse(lines.asSequence())
        val b = PinyinDict.parse(lines.asSequence())
        for (k in listOf("ni", "zhong", "xian", "shi", "de", "nihao")) {
            assertEquals(a.exact(k), a.exact(k))
            assertEquals(a.exact(k), b.exact(k))
            assertEquals(a.completions(k), b.completions(k))
        }
    }

    @Test fun equalWeightTiesKeepFileOrderInCompletions() {
        val d = PinyinDict.parse(
            sequenceOf("nia\t甲\t10", "nib\t乙\t10", "nic\t丙\t10", "nid\t丁\t20").sortedBy {
                it.substringBefore('\t')
            }
        )
        // higher weight first; the three equal weights keep their file (key) order
        assertEquals(listOf("丁", "甲", "乙", "丙"), d.completions("ni"))
    }

    @Test fun composerCandidatesDoNotDependOnWallClockOrInstance() {
        val lines = listOf("ni\t你\t30", "ni\t尼\t30", "nihao\t你好\t10").sorted()
        fun run(): List<String> {
            val c = PinyinComposer { PinyinDict.parse(lines.asSequence()) }
            c.type("n")
            c.type("i")
            return c.candidates.map { it.text }
        }
        assertEquals(run(), run())
    }

    @Test fun shippedWeightsAreTheThreeNeutralTiers() {
        val weights = PinyinTestAsset.find().readLines(Charsets.UTF_8).map {
            it.split('\t')[2].toInt()
        }.toSet()
        assertEquals(setOf(10, 20, 30), weights)
    }
}
