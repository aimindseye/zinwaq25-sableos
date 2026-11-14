package org.sableos.titan2.keyboard.core

/**
 * Emoji search by keyword. Source lines are `emoji<TAB>keyword keyword ...`; the first keyword is the short name
 * (the `:name:` form), the rest are tags and the description's words. Order in the file is the Unicode order, which
 * is the tie-break between emoji that match equally.
 */
class EmojiIndex private constructor(
    private val emoji: List<String>,
    private val keywords: List<List<String>>
) {
    private val exact = HashMap<String, MutableList<Int>>()

    init {
        keywords.forEachIndexed { i, ks ->
            ks.forEach { exact.getOrPut(it) { ArrayList() }.add(i) }
        }
    }

    val size: Int get() = emoji.size

    /** Emoji tagged with exactly [word]; those named [word] first. */
    fun forWord(word: String, limit: Int): List<String> {
        val hits = exact[word.lowercase()] ?: return emptyList()
        return hits.sortedBy { if (keywords[it].first() == word.lowercase()) 0 else 1 }
            .take(limit).map { emoji[it] }
    }

    /** `:name` search: emoji whose short name or tag starts with [prefix], short names first. */
    fun search(prefix: String, limit: Int): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val byName = emoji.indices.filter { keywords[it].first().startsWith(p) }
        val byTag = emoji.indices.filter { i ->
            i !in byName && keywords[i].any { it.startsWith(p) }
        }
        return (
            byName.sortedBy {
                keywords[it].first().length
            } + byTag
            ).take(limit).map { emoji[it] }
    }

    companion object {
        /** Words that tag nearly every emoji description and so identify none. */
        private val STOP = setOf("with", "and", "the", "of", "in", "on", "for")

        fun parse(lines: Sequence<String>): EmojiIndex {
            val entries = lines.mapNotNull { entry(it) }.toList()
            return EmojiIndex(entries.map { it.first }, entries.map { it.second })
        }

        private fun entry(line: String): Pair<String, List<String>>? {
            val tab = line.indexOf('\t')
            val ks = if (tab >
                0
            ) {
                line.substring(tab + 1).split(' ').filter { it.isNotEmpty() && it !in STOP }
            } else {
                null
            }
            return ks?.takeIf { it.isNotEmpty() }?.let { line.substring(0, tab) to it }
        }
    }
}
