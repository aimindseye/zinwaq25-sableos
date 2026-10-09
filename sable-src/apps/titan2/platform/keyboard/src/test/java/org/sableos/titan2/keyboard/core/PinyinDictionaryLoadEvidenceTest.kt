package org.sableos.titan2.keyboard.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Captures JVM load evidence for the shipped pinyin dictionary: byte size, entry count, parse time, memory delta and
 * host/JVM identity. This is EVIDENCE, not a latency claim for the device and not a timing threshold: the only
 * assertions are structural (entry count and size match the provenance pins, ordering holds, lookups hit).
 *
 * Output: `<dir>/pinyin_dictionary_jvm_load_benchmark.env`, where `<dir>` is the system property
 * `pinyin.evidence.dir` (default `build/pinyin-evidence`). Prints the marker
 * `PINYIN_DICTIONARY_JVM_LOAD_BENCHMARK=PASS_EVIDENCE_CAPTURED`.
 */
class PinyinDictionaryLoadEvidenceTest {
    private companion object {
        const val RUNS = 5
        const val NANOS_PER_MS = 1_000_000.0
        const val MICRO_DIVISOR = 1000.0
        const val LOOKUPS_PER_RUN = 1000
        const val BYTES_PER_KIB = 1024L
    }

    private fun maxHeapMib() = Runtime.getRuntime().maxMemory() / BYTES_PER_KIB / BYTES_PER_KIB

    private fun usedBytes(): Long {
        val rt = Runtime.getRuntime()
        repeat(3) { System.gc() }
        return rt.totalMemory() - rt.freeMemory()
    }

    private fun parse(asset: File): PinyinDict =
        asset.bufferedReader(Charsets.UTF_8).use { PinyinDict.parse(it.lineSequence()) }

    private fun prop(k: String) = System.getProperty(k)

    private fun checkStructure(asset: File, d: PinyinDict) {
        val p = PinyinTestAsset.pins(asset)
        if (p.isNotEmpty()) {
            assertEquals(p.getValue("PINYIN_OUTPUT_ENTRIES").toInt(), d.size)
            assertEquals(p.getValue("PINYIN_OUTPUT_BYTES").toLong(), asset.length())
            assertTrue(d.size <= p.getValue("PINYIN_SIZE_MAX_ENTRIES").toInt())
            assertTrue(asset.length() <= p.getValue("PINYIN_SIZE_MAX_BYTES").toLong())
        }
        assertTrue(d.exact("ni").isNotEmpty())
        assertTrue(d.exact("zhongguo").contains("中国"))
    }

    /** Average microseconds for an exact lookup on the already-loaded structure (no re-parse per keystroke). */
    private fun lookupMicros(d: PinyinDict): Double {
        val keys =
            listOf("n", "ni", "nih", "nihao", "zhong", "zhongg", "zhongguo", "xian", "shi", "de")
        val t0 = System.nanoTime()
        repeat(RUNS * LOOKUPS_PER_RUN) { i -> d.exact(keys[i % keys.size]) }
        return (System.nanoTime() - t0) / MICRO_DIVISOR / (RUNS * LOOKUPS_PER_RUN)
    }

    private fun identityLines() = listOf(
        "EVIDENCE_HOST_OS=${prop("os.name")} ${prop("os.version")} ${prop("os.arch")}",
        "EVIDENCE_HOST_CPUS=${Runtime.getRuntime().availableProcessors()}",
        "EVIDENCE_JVM=${prop(
            "java.vm.name"
        )} ${prop("java.runtime.version")} (${prop("java.vendor")})",
        "EVIDENCE_JVM_MAX_HEAP_MIB=${maxHeapMib()}"
    )

    @Test fun capturesLoadEvidenceAndChecksStructure() {
        val asset = PinyinTestAsset.find()
        val times = ArrayList<Double>()
        var dict: PinyinDict? = null
        val before = usedBytes()
        repeat(RUNS) {
            val t0 = System.nanoTime()
            dict = parse(asset)
            times.add((System.nanoTime() - t0) / NANOS_PER_MS)
        }
        val after = usedBytes()
        val d = dict!!
        val sorted = times.sorted()
        checkStructure(asset, d)

        val lines = listOf(
            "# JVM load evidence for pinyin_zh.tsv.",
            "# NOT an on-device latency claim and NOT a threshold test.",
            "PINYIN_DICTIONARY_ASSET_BYTES=${asset.length()}",
            "PINYIN_DICTIONARY_ENTRY_COUNT=${d.size}",
            "PINYIN_DICTIONARY_PARSE_RUNS=$RUNS",
            "PINYIN_DICTIONARY_PARSE_MS_MIN=${"%.1f".format(sorted.first())}",
            "PINYIN_DICTIONARY_PARSE_MS_MEDIAN=${"%.1f".format(sorted[sorted.size / 2])}",
            "PINYIN_DICTIONARY_PARSE_MS_MAX=${"%.1f".format(sorted.last())}",
            "PINYIN_DICTIONARY_HEAP_DELTA_KIB=${(after - before) / BYTES_PER_KIB}",
            "PINYIN_DICTIONARY_LOOKUP_US_AVG=${"%.2f".format(lookupMicros(d))}"
        ) + identityLines() + listOf(
            "ON_DEVICE_LATENCY_CLAIM=NO",
            "THRESHOLD_ASSERTED=NO",
            "PINYIN_DICTIONARY_JVM_LOAD_BENCHMARK=PASS_EVIDENCE_CAPTURED"
        )
        val out = File(prop("pinyin.evidence.dir") ?: "build/pinyin-evidence")
        out.mkdirs()
        File(
            out,
            "pinyin_dictionary_jvm_load_benchmark.env"
        ).writeText(lines.joinToString("\n") + "\n")
        println("PINYIN_DICTIONARY_JVM_LOAD_BENCHMARK=PASS_EVIDENCE_CAPTURED")
    }
}
