package org.sableos.reader.engine.pdf

import org.sableos.reader.model.PageLocator
import org.sableos.reader.model.ReadingProgress

/** Pure page-position state for a PDF. Pages are zero-based; navigation always clamps to the document. */
class PdfNavigator(val pageCount: Int, startPage: Int = 0) {
    init {
        require(pageCount >= 1) { "a PDF has at least one page" }
    }

    var currentPage: Int = startPage.coerceIn(0, pageCount - 1)
        private set

    val isFirstPage: Boolean get() = currentPage == 0
    val isLastPage: Boolean get() = currentPage == pageCount - 1

    /** Moves to [page] (clamped). Returns true when the position changed. */
    fun goTo(page: Int): Boolean {
        val target = page.coerceIn(0, pageCount - 1)
        val changed = target != currentPage
        currentPage = target
        return changed
    }

    fun next(): Boolean = goTo(currentPage + 1)

    fun previous(): Boolean = goTo(currentPage - 1)

    /** Pages that should be warm in the cache: the current page and its direct neighbours. */
    fun adjacentPages(radius: Int = PREFETCH_RADIUS): List<Int> =
        ((currentPage - radius)..(currentPage + radius)).filter { it in 0 until pageCount }

    fun toProgress(updatedAt: Long): ReadingProgress = ReadingProgress.page(currentPage, pageCount, updatedAt)

    fun locator(): PageLocator = PageLocator(currentPage, pageCount)

    companion object {
        /** Only the immediately adjacent pages are prefetched; a whole document is never preloaded. */
        const val PREFETCH_RADIUS: Int = 1

        /** User-facing page numbers are one-based. */
        fun displayNumber(pageIndex: Int): Int = pageIndex + 1

        fun fromProgress(progress: ReadingProgress?, pageCount: Int): PdfNavigator {
            val locator = progress?.locator as? PageLocator
            return PdfNavigator(pageCount, locator?.pageIndex ?: 0)
        }
    }
}
