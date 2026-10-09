package org.sableos.reader.ui.pdf

/** Every user intent the PDF reader understands; one entry point keeps the ViewModel surface small and testable. */
sealed interface PdfUiAction {
    data object NextPage : PdfUiAction

    data object PreviousPage : PdfUiAction

    /** [pageNumber] is one-based, as typed by the user. */
    data class GoToPage(val pageNumber: Int) : PdfUiAction

    data object ToggleBookmark : PdfUiAction

    data class DeleteBookmark(val id: String) : PdfUiAction

    data class Show(val overlay: PdfOverlay) : PdfUiAction

    data object ToggleChrome : PdfUiAction
}
