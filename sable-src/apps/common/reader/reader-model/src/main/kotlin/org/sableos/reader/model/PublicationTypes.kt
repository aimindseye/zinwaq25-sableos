package org.sableos.reader.model

/** Maps a picked file's media type / name to the publication kind Sable Reader opens it with. */
object PublicationTypes {
    const val EPUB_MIME: String = "application/epub+zip"
    const val PDF_MIME: String = "application/pdf"

    /** Comic book archive (CBZ): registered type plus the unofficial one many file managers report. */
    const val COMIC_MIME: String = "application/vnd.comicbook+zip"
    const val COMIC_MIME_LEGACY: String = "application/x-cbz"

    private const val AUDIO_PREFIX: String = "audio/"

    /** Audio file extensions Sable Reader plays as audiobooks (the Media3 decoders available on every device). */
    val AUDIO_EXTENSIONS: Set<String> = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav")

    /** Generic archive type some providers report for `.cbz`; only trusted together with the `.cbz` extension. */
    private const val ZIP_MIME: String = "application/zip"

    /** Media types Android file pickers send when they cannot tell what a file is. */
    private val genericTypes = setOf("application/octet-stream", "binary/octet-stream", "*/*")

    /**
     * Returns the kind for [mimeType], falling back to the [displayName] extension when the type is missing or
     * generic. Returns null for anything Sable Reader does not own (text, archives it cannot open, …).
     */
    fun detect(mimeType: String?, displayName: String?): PublicationKind? {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        return when {
            mime == EPUB_MIME -> PublicationKind.EPUB
            mime == PDF_MIME -> PublicationKind.PDF
            mime == COMIC_MIME || mime == COMIC_MIME_LEGACY -> PublicationKind.COMIC
            mime == ZIP_MIME -> byExtension(displayName)?.takeIf { it == PublicationKind.COMIC }
            mime != null && mime.startsWith(AUDIO_PREFIX) -> PublicationKind.AUDIOBOOK
            mime.isNullOrEmpty() || mime in genericTypes -> byExtension(displayName)
            else -> null
        }
    }

    private fun byExtension(displayName: String?): PublicationKind? =
        when (displayName?.substringAfterLast('.', "")?.lowercase()) {
            "epub" -> PublicationKind.EPUB
            "pdf" -> PublicationKind.PDF
            "cbz" -> PublicationKind.COMIC
            in AUDIO_EXTENSIONS -> PublicationKind.AUDIOBOOK
            else -> null
        }

    /** The media type to store as [LibraryItem.format] for [kind] when a file does not report a better one. */
    fun defaultFormat(kind: PublicationKind): String? = when (kind) {
        PublicationKind.EPUB -> EPUB_MIME
        PublicationKind.PDF -> PDF_MIME
        PublicationKind.COMIC -> COMIC_MIME
        PublicationKind.AUDIOBOOK -> null
    }
}
