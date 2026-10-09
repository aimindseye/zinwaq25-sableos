package org.sableos.reader.model

/**
 * One entry in the unified Sable Reader library. Format-specific state lives behind [progress]
 * (typed [Locator]) and engine-owned tables, never in this generic type.
 */
data class LibraryItem(
    val id: String,
    val profileId: String,
    val kind: PublicationKind,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val series: String? = null,
    val seriesIndex: Double? = null,
    val cover: String? = null,
    val source: String? = null,
    val format: String? = null,
    val addedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
    val progress: ReadingProgress? = null,
    val finished: Boolean = false,
    val favorite: Boolean = false,
    val collectionIds: Set<String> = emptySet(),
) {
    /** Normalized presentation progress, 0.0 when never opened. */
    val progressFraction: Double get() = progress?.fraction ?: 0.0

    val hasBeenOpened: Boolean get() = lastOpenedAt > 0L

    val authorLine: String get() = AuthorList.join(authors)
}

/** Authors are persisted as one `;`-separated column so the pre-P5 `author` column keeps working. */
object AuthorList {
    private const val SEPARATOR = ";"

    fun split(stored: String?): List<String> =
        stored.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    fun join(authors: List<String>): String = authors.joinToString("$SEPARATOR ")
}
