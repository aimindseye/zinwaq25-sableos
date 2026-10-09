package org.sableos.reader.model

/**
 * The publication families Sable Reader v2 owns. Plain text, OCR and share/process-text belong to
 * Sable Text Reader and are deliberately not representable here.
 */
enum class PublicationKind(val stableValue: String) {
    EPUB("EPUB"),
    PDF("PDF"),
    COMIC("COMIC"),
    AUDIOBOOK("AUDIOBOOK"),
    ;

    companion object {
        /** Unknown or legacy values fall back to [EPUB]: every pre-P5 library row is an EPUB. */
        fun fromStableValue(value: String?): PublicationKind =
            entries.firstOrNull { it.stableValue == value } ?: EPUB
    }
}
