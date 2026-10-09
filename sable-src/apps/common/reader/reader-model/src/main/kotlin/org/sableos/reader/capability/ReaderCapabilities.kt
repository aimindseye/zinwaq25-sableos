package org.sableos.reader.capability

import org.sableos.reader.model.PublicationKind

/**
 * What a reading engine genuinely supports. Unsupported capabilities must be absent or disabled in the UI and in
 * keyboard handling, never faked. This is the single source of truth every engine and screen consults.
 */
data class ReaderCapabilities(
    val textSearch: Boolean = false,
    val textHighlight: Boolean = false,
    val textToSpeech: Boolean = false,
    val bookmarks: Boolean = false,
    /** Table of contents, chapter list or page navigator. */
    val contents: Boolean = false,
    /** Appearance / view settings the user can change for this engine. */
    val appearance: Boolean = false,
    val pageNavigation: Boolean = false,
    val progress: Boolean = false,
    val dictionaryLookup: Boolean = false,
) {
    companion object {
        /** EPUB (Readium): full text features. */
        val EPUB = ReaderCapabilities(
            textSearch = true,
            textHighlight = true,
            textToSpeech = true,
            bookmarks = true,
            contents = true,
            appearance = true,
            pageNavigation = true,
            progress = true,
            dictionaryLookup = true,
        )

        /** PDF v2.0 (Android PdfRenderer): page rendering, progress, page bookmarks and page navigation only. */
        val PDF = ReaderCapabilities(
            bookmarks = true,
            contents = true,
            pageNavigation = true,
            progress = true,
        )

        /** Comics: no text features; chapters/volumes and viewer settings are supported. */
        val COMIC = ReaderCapabilities(
            bookmarks = true,
            contents = true,
            appearance = true,
            pageNavigation = true,
            progress = true,
        )

        /** Audiobooks: no visual text features; chapters, bookmarks and playback preferences. */
        val AUDIOBOOK = ReaderCapabilities(
            bookmarks = true,
            contents = true,
            appearance = true,
            progress = true,
        )

        fun of(kind: PublicationKind): ReaderCapabilities = when (kind) {
            PublicationKind.EPUB -> EPUB
            PublicationKind.PDF -> PDF
            PublicationKind.COMIC -> COMIC
            PublicationKind.AUDIOBOOK -> AUDIOBOOK
        }
    }
}
