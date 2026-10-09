package org.sableos.reader.ui.comic

import org.sableos.reader.comic.ComicViewSettings
import org.sableos.reader.engine.comic.ComicChapter
import org.sableos.reader.engine.comic.ComicOpenFailure

/** Transient surfaces the comic reader can show above the pages. At most one is open at a time. */
enum class ComicOverlay { NONE, CONTENTS, BOOKMARKS, MODE, HINTS }

data class ComicBookmarkUi(val id: String, val pageIndex: Int, val label: String)

data class ComicReaderUiState(
    val title: String = "",
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val settings: ComicViewSettings = ComicViewSettings(),
    val chapters: List<ComicChapter> = emptyList(),
    val bookmarks: List<ComicBookmarkUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: ComicReaderError? = null,
    val chromeVisible: Boolean = true,
    val transientChrome: Boolean = false,
    val overlay: ComicOverlay = ComicOverlay.NONE,
    /** Bumped whenever cached renders became invalid (window resize, mode or fit change). */
    val layoutToken: Int = 0,
) {
    val isBookmarked: Boolean get() = bookmarks.any { it.pageIndex == pageIndex }

    /** One-based number shown to the user. */
    val pageNumber: Int get() = pageIndex + 1

    val hasTransientSurface: Boolean
        get() = overlay != ComicOverlay.NONE || (transientChrome && chromeVisible)

    /** Title of the chapter the current page is in, or null when the comic has no chapters. */
    val chapterTitle: String? get() = chapters.lastOrNull { it.firstPage <= pageIndex }?.title
}

sealed interface ComicReaderError {
    data object NotFound : ComicReaderError

    data class CannotOpen(val failure: ComicOpenFailure) : ComicReaderError
}

/** Why one page shows a placeholder instead of a picture. The other pages stay readable. */
enum class ComicPageProblem {
    TOO_LARGE,
    COMPRESSION_BOMB,
    UNREADABLE,
    ENCRYPTED,
    UNDECODABLE,
    TOO_MANY_PIXELS,
    OUT_OF_MEMORY,
}
