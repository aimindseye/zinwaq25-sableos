package org.sableos.reader.input

/** One line of the dismissible key-hint legend. */
data class KeyHint(val keys: String, val label: String)

/** Builds the key-hint legend from what the surface really supports, so a hint never advertises a missing feature. */
object KeyHints {
    fun forContext(context: KeyContext): List<KeyHint> = buildList {
        navigationHints(context)?.let { addAll(it) }
        val caps = context.capabilities
        if (context.surface != ReaderSurface.AUDIO) add(KeyHint("Activate / Menu", "Show or hide controls"))
        if (caps.bookmarks) add(KeyHint("B", "Bookmark"))
        if (caps.contents) {
            add(KeyHint("T", if (context.surface == ReaderSurface.AUDIO) "Chapters" else "Contents"))
        }
        if (caps.textSearch) add(KeyHint("F", "Find"))
        if (caps.appearance) add(KeyHint("A", "Appearance"))
        add(KeyHint("Esc / Back", "Close, then leave"))
    }

    private fun navigationHints(context: KeyContext): List<KeyHint>? = when (context.surface) {
        ReaderSurface.EPUB_PAGED, ReaderSurface.PDF_PAGED, ReaderSurface.COMIC_PAGED -> listOf(
            KeyHint("Left / Right", "Previous / next page"),
            KeyHint("PageUp / PageDown", "Previous / next page"),
            KeyHint("Space / Shift+Space", "Advance / reverse"),
        )
        ReaderSurface.EPUB_SCROLL, ReaderSurface.COMIC_CONTINUOUS -> listOf(
            KeyHint("Up / Down", "Scroll"),
            KeyHint("PageUp / PageDown", "Scroll a page"),
            KeyHint("Space / Shift+Space", "Advance / reverse"),
        )
        ReaderSurface.COMIC_VERTICAL_PAGER -> listOf(
            KeyHint("Up / Down", "Previous / next page"),
            KeyHint("Space / Shift+Space", "Advance / reverse"),
        )
        ReaderSurface.AUDIO -> listOf(
            KeyHint("Space", "Play / pause"),
            KeyHint("Left / Right", "Seek"),
            KeyHint("PageUp / PageDown", "Previous / next chapter"),
        )
    }
}
