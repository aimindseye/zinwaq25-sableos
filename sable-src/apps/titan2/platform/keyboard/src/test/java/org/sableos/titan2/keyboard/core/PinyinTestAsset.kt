package org.sableos.titan2.keyboard.core

import java.io.File

/** Locates the shipped pinyin asset and its provenance pins from a Gradle module dir or the repository root. */
object PinyinTestAsset {
    private const val ASSET = "pinyin_zh.tsv"

    fun find(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            for (rel in listOf(
                "src/main/assets/$ASSET",
                "apps/titan2/platform/keyboard/src/main/assets/$ASSET"
            )) {
                val f = File(d, rel)
                if (f.isFile) return f
            }
            d = d.parentFile
        }
        error("cannot locate $ASSET from ${System.getProperty("user.dir")}")
    }

    fun pins(asset: File): Map<String, String> {
        var d: File? = asset.parentFile
        while (d != null) {
            val f = File(d, "third_party/pinyin_zh/PROVENANCE.env")
            if (f.isFile) {
                return f.readLines().filter { '=' in it && !it.startsWith("#") }
                    .associate { it.substringBefore('=') to it.substringAfter('=') }
            }
            d = d.parentFile
        }
        return emptyMap()
    }
}
