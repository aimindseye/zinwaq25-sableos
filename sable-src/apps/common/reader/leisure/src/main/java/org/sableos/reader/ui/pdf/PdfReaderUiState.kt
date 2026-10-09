package org.sableos.reader.ui.pdf

import android.graphics.Bitmap
import org.sableos.reader.core.pdf.PdfOpenFailure

/** Transient surfaces the PDF reader can show above the page. At most one is open at a time. */
enum class PdfOverlay { NONE, PAGES, BOOKMARKS, HINTS }

data class PdfBookmarkUi(val id: String, val pageIndex: Int, val label: String)

data class PdfReaderUiState(
    val title: String = "",
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val page: Bitmap? = null,
    val isLoading: Boolean = true,
    val error: PdfReaderError? = null,
    val bookmarks: List<PdfBookmarkUi> = emptyList(),
    val chromeVisible: Boolean = true,
    val transientChrome: Boolean = false,
    val overlay: PdfOverlay = PdfOverlay.NONE,
) {
    val isBookmarked: Boolean get() = bookmarks.any { it.pageIndex == pageIndex }

    /** One-based number shown to the user. */
    val pageNumber: Int get() = pageIndex + 1

    /** True while anything dismissible sits above the page (Escape/Back closes it before leaving). */
    val hasTransientSurface: Boolean
        get() = overlay != PdfOverlay.NONE || (transientChrome && chromeVisible)
}

sealed interface PdfReaderError {
    data object NotFound : PdfReaderError

    data class CannotOpen(val reason: PdfOpenFailure) : PdfReaderError
}
