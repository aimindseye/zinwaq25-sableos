package org.sableos.reader.ui.comic

import org.sableos.reader.comic.ComicFit
import org.sableos.reader.comic.ComicReadingMode

/** Every user intent the comic reader understands; one entry point keeps the ViewModel surface small and testable. */
sealed interface ComicUiAction {
    data object NextPage : ComicUiAction

    data object PreviousPage : ComicUiAction

    /** [pageNumber] is one-based, as typed by the user. */
    data class GoToPage(val pageNumber: Int) : ComicUiAction

    data object NextChapter : ComicUiAction

    data object PreviousChapter : ComicUiAction

    /** The viewer settled on [pageIndex] by swiping or scrolling; the position is recorded, not re-applied. */
    data class PageSettled(val pageIndex: Int) : ComicUiAction

    data object ToggleBookmark : ComicUiAction

    data class DeleteBookmark(val id: String) : ComicUiAction

    data class SetMode(val mode: ComicReadingMode) : ComicUiAction

    data class SetFit(val fit: ComicFit) : ComicUiAction

    data class Show(val overlay: ComicOverlay) : ComicUiAction

    data object ToggleChrome : ComicUiAction
}
