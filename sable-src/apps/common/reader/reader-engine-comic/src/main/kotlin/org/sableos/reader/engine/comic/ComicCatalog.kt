package org.sableos.reader.engine.comic

/** One raw container entry before classification. [handle] lets the source open it later (zip name, file path, URI). */
data class RawEntry(val name: String, val sizeBytes: Long, val compressedBytes: Long, val handle: String = name)

/** A readable page, in reading order. [index] is zero-based and stable for the life of the catalog. */
data class ComicPage(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val compressedBytes: Long,
    val handle: String,
)

/** A named run of pages, derived from the folder structure inside the comic. */
data class ComicChapter(val title: String, val firstPage: Int)

enum class ComicOpenFailure {
    NOT_FOUND,
    UNREADABLE,
    CORRUPT,
    ENCRYPTED,
    TOO_MANY_ENTRIES,
    TOO_MANY_PAGES,
    ARCHIVE_TOO_LARGE,
    NO_PAGES,
}

class ComicOpenException(val failure: ComicOpenFailure, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: failure.name, cause)

/** Why a single page could not be read; the viewer shows a placeholder and the other pages stay usable. */
enum class PageFailure { TOO_LARGE, COMPRESSION_BOMB, UNREADABLE, ENCRYPTED }

class ComicPageException(val failure: PageFailure, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: failure.name, cause)

/** Everything the viewer needs to know about a container, with hostile content already filtered out. */
class ComicCatalog(
    val pages: List<ComicPage>,
    val chapters: List<ComicChapter>,
    /** Handle of a `ComicInfo.xml` entry if one exists. */
    val comicInfoHandle: String?,
    val skippedUnsafe: Int,
    val skippedDuplicates: Int,
)

/** Builds a [ComicCatalog] from raw entries: classify, bound, de-duplicate, order naturally, derive chapters. */
object ComicCatalogBuilder {
    private const val COMIC_INFO_NAME = "comicinfo.xml"
    private const val ROOT_TITLE = "Start"

    fun build(entries: Sequence<RawEntry>, limits: ComicLimits = ComicLimits()): ComicCatalog {
        val accepted = LinkedHashMap<String, RawEntry>()
        var comicInfo: String? = null
        var unsafe = 0
        var duplicates = 0
        var seen = 0
        var declaredTotal = 0L
        for (entry in entries) {
            if (++seen > limits.maxEntries) throw ComicOpenException(ComicOpenFailure.TOO_MANY_ENTRIES)
            when (ComicEntryPolicy.classify(entry.name, limits)) {
                EntryVerdict.UNSAFE -> unsafe++
                EntryVerdict.IGNORED ->
                    if (comicInfo == null && isComicInfo(entry.name)) comicInfo = entry.handle
                EntryVerdict.IMAGE -> {
                    val key = ComicEntryPolicy.normalize(entry.name).lowercase()
                    if (accepted.containsKey(key)) {
                        duplicates++
                    } else {
                        accepted[key] = entry
                        declaredTotal += entry.sizeBytes.coerceAtLeast(0)
                        check(accepted.size, declaredTotal, limits)
                    }
                }
            }
        }
        if (accepted.isEmpty()) throw ComicOpenException(ComicOpenFailure.NO_PAGES)
        val ordered = accepted.values.sortedWith { a, b ->
            NaturalOrder.compare(ComicEntryPolicy.normalize(a.name), ComicEntryPolicy.normalize(b.name))
        }
        val pages = ordered.mapIndexed { index, e ->
            ComicPage(index, ComicEntryPolicy.normalize(e.name), e.sizeBytes, e.compressedBytes, e.handle)
        }
        return ComicCatalog(pages, chaptersOf(pages), comicInfo, unsafe, duplicates)
    }

    private fun check(pageCount: Int, declaredTotal: Long, limits: ComicLimits) {
        if (pageCount > limits.maxPages) throw ComicOpenException(ComicOpenFailure.TOO_MANY_PAGES)
        if (declaredTotal > limits.maxTotalBytes) throw ComicOpenException(ComicOpenFailure.ARCHIVE_TOO_LARGE)
    }

    private fun isComicInfo(name: String): Boolean =
        ComicEntryPolicy.baseName(ComicEntryPolicy.normalize(name)).lowercase() == COMIC_INFO_NAME

    /** Chapters exist only when pages live in two or more distinct folders; each folder starts a chapter. */
    fun chaptersOf(pages: List<ComicPage>): List<ComicChapter> {
        val chapters = mutableListOf<ComicChapter>()
        var lastFolder: String? = null
        pages.forEach { page ->
            val folder = ComicEntryPolicy.parentPath(page.path)
            if (folder != lastFolder) {
                chapters += ComicChapter(ComicEntryPolicy.baseName(folder).ifEmpty { ROOT_TITLE }, page.index)
                lastFolder = folder
            }
        }
        return if (chapters.size >= 2) chapters else emptyList()
    }

    /** Per-page guard applied when a page is about to be read. Throws [ComicPageException]. */
    fun checkReadable(page: ComicPage, limits: ComicLimits) {
        val ratioApplies = page.sizeBytes >= limits.ratioFloorBytes && page.compressedBytes > 0
        if (ratioApplies && page.sizeBytes / page.compressedBytes > limits.maxCompressionRatio) {
            throw ComicPageException(PageFailure.COMPRESSION_BOMB)
        }
        if (page.sizeBytes > limits.maxPageBytes) throw ComicPageException(PageFailure.TOO_LARGE)
    }
}
