package org.sableos.reader.model

/** A saved position (page or time) in a non-EPUB publication. EPUB bookmarks stay Readium highlights. */
data class Bookmark(
    val id: String,
    val itemId: String,
    val profileId: String,
    val locator: Locator,
    val label: String,
    val createdAt: Long,
)

/** Audiobook bookmark: a time position inside a chapter. */
data class PlaybackBookmark(
    val id: String,
    val itemId: String,
    val profileId: String,
    val positionMs: Long,
    val chapterIndex: Int,
    val label: String,
    val createdAt: Long,
) {
    fun toBookmark(durationMs: Long = 0L): Bookmark =
        Bookmark(id, itemId, profileId, TimeLocator(positionMs, durationMs, chapterIndex), label, createdAt)

    companion object {
        fun from(bookmark: Bookmark): PlaybackBookmark? {
            val locator = bookmark.locator as? TimeLocator ?: return null
            return PlaybackBookmark(
                bookmark.id,
                bookmark.itemId,
                bookmark.profileId,
                locator.positionMs,
                locator.chapterIndex,
                bookmark.label,
                bookmark.createdAt,
            )
        }
    }
}

/** A text highlight (EPUB only in v2.0; PDF/comic/audio engines do not claim highlights). */
data class Highlight(
    val id: String,
    val itemId: String,
    val profileId: String,
    val locatorJson: String,
    val text: String,
    val color: Int,
    val tag: String?,
    val createdAt: Long,
)
