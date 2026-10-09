package org.sableos.reader.engine.comic

import org.sableos.reader.comic.ComicReadingMode
import org.sableos.reader.comic.ComicViewSettings

/** The `<Manga>` flag of ComicInfo.xml. */
enum class MangaFlag { UNKNOWN, NO, YES, YES_RIGHT_TO_LEFT }

/** The subset of ComicInfo.xml (Anansi schema) Sable Reader uses. Every field is optional. */
data class ComicInfo(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: Int? = null,
    val writer: String? = null,
    val publisher: String? = null,
    val summary: String? = null,
    val year: Int? = null,
    val pageCount: Int? = null,
    val format: String? = null,
    val manga: MangaFlag = MangaFlag.UNKNOWN,
    /** Zero-based index of the page marked `FrontCover`, if any. */
    val coverPage: Int? = null,
) {
    /** Series order for sorting: the issue number as a decimal when it is one (`12`, `3.5`). */
    val seriesIndex: Double? get() = number?.trim()?.toDoubleOrNull()
}

/** First-open viewer choice derived from the comic's own metadata; the user's per-title choice always wins later. */
object ComicViewDefaults {
    fun initial(info: ComicInfo?): ComicViewSettings {
        val mode = when {
            info?.format?.contains("webtoon", ignoreCase = true) == true -> ComicReadingMode.WEBTOON
            info?.manga == MangaFlag.YES_RIGHT_TO_LEFT -> ComicReadingMode.PAGED_RTL
            else -> ComicReadingMode.PAGED_LTR
        }
        return ComicViewSettings(mode)
    }
}
