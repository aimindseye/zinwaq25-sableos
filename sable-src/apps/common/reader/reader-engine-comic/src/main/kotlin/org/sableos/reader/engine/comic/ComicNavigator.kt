package org.sableos.reader.engine.comic

import org.sableos.reader.model.PageLocator
import org.sableos.reader.model.ReadingProgress

/** Zero-based page position inside a comic; navigation always clamps to the pages that exist. */
class ComicNavigator(val pageCount: Int, startPage: Int = 0) {
    init {
        require(pageCount >= 1) { "a comic has at least one page" }
    }

    var currentPage: Int = startPage.coerceIn(0, pageCount - 1)
        private set

    fun goTo(page: Int): Boolean {
        val target = page.coerceIn(0, pageCount - 1)
        val changed = target != currentPage
        currentPage = target
        return changed
    }

    fun next(): Boolean = goTo(currentPage + 1)

    fun previous(): Boolean = goTo(currentPage - 1)

    /** Index of the chapter the current page belongs to, or null when the comic has no chapters. */
    fun chapterIndex(chapters: List<ComicChapter>): Int? =
        chapters.indexOfLast { it.firstPage <= currentPage }.takeIf { it >= 0 && chapters.isNotEmpty() }

    /** First page of the chapter after/before the current one, or null at the ends. */
    fun nextChapterStart(chapters: List<ComicChapter>): Int? =
        chapters.firstOrNull { it.firstPage > currentPage }?.firstPage

    fun previousChapterStart(chapters: List<ComicChapter>): Int? {
        val current = chapterIndex(chapters) ?: return null
        val start = chapters[current].firstPage
        return if (currentPage > start) start else chapters.getOrNull(current - 1)?.firstPage
    }

    /** Pages worth keeping decoded: the current page and its [radius] neighbours on each side. */
    fun window(radius: Int = PREFETCH_RADIUS): IntRange =
        (currentPage - radius).coerceAtLeast(0)..(currentPage + radius).coerceAtMost(pageCount - 1)

    fun toProgress(updatedAt: Long): ReadingProgress = ReadingProgress.page(currentPage, pageCount, updatedAt)

    fun locator(): PageLocator = PageLocator(currentPage, pageCount)

    companion object {
        const val PREFETCH_RADIUS: Int = 1

        fun displayNumber(pageIndex: Int): Int = pageIndex + 1

        fun fromProgress(progress: ReadingProgress?, pageCount: Int): ComicNavigator =
            ComicNavigator(pageCount, (progress?.locator as? PageLocator)?.pageIndex ?: 0)
    }
}
