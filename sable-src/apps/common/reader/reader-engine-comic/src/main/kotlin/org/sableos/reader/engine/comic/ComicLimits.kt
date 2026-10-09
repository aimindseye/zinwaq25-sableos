package org.sableos.reader.engine.comic

/**
 * Hard limits applied to every comic container, however it arrives. Anything beyond them is treated as hostile and
 * refused, never "best effort" decoded. The defaults are far above any real comic and far below what exhausts a phone.
 */
data class ComicLimits(
    /** Entries the container may hold (all kinds), counted while listing; the listing stops at the limit. */
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    /** Image pages a comic may have. */
    val maxPages: Int = DEFAULT_MAX_PAGES,
    /** Bytes a single page may occupy when read, enforced on the bytes actually inflated, not on the header. */
    val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    /** Sum of the declared sizes of all pages. */
    val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    /** Declared size/compressed size ratio above which a page of at least [ratioFloorBytes] is a compression bomb. */
    val maxCompressionRatio: Int = DEFAULT_MAX_RATIO,
    val ratioFloorBytes: Long = DEFAULT_RATIO_FLOOR_BYTES,
    val maxNameLength: Int = DEFAULT_MAX_NAME_LENGTH,
    val maxFolderDepth: Int = DEFAULT_MAX_FOLDER_DEPTH,
    val maxComicInfoBytes: Int = DEFAULT_MAX_COMIC_INFO_BYTES,
    /** Declared width x height above which an image is refused before any pixel is decoded. */
    val maxImagePixels: Long = DEFAULT_MAX_IMAGE_PIXELS,
    /** Largest decoded page held in memory (8 Mpx = 32 MB as ARGB_8888); bigger images are sub-sampled to fit. */
    val maxDecodedPixels: Long = DEFAULT_MAX_DECODED_PIXELS,
) {
    init {
        require(maxEntries > 0 && maxPages > 0 && maxPageBytes > 0 && maxTotalBytes > 0) { "limits must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 20_000
        const val DEFAULT_MAX_PAGES: Int = 5_000
        const val DEFAULT_MAX_PAGE_BYTES: Long = 64L * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_BYTES: Long = 8L * 1024 * 1024 * 1024
        const val DEFAULT_MAX_RATIO: Int = 100
        const val DEFAULT_RATIO_FLOOR_BYTES: Long = 1024L * 1024
        const val DEFAULT_MAX_NAME_LENGTH: Int = 512
        const val DEFAULT_MAX_FOLDER_DEPTH: Int = 8
        const val DEFAULT_MAX_COMIC_INFO_BYTES: Int = 256 * 1024
        const val DEFAULT_MAX_IMAGE_PIXELS: Long = 100L * 1000 * 1000
        const val DEFAULT_MAX_DECODED_PIXELS: Long = 8L * 1000 * 1000
    }
}
