package org.sableos.reader.engine.audio

/** Hard limits for audiobook containers and metadata. Anything beyond them is refused, never "best effort" read. */
data class AudioLimits(
    val maxTracks: Int = DEFAULT_MAX_TRACKS,
    val maxChapters: Int = DEFAULT_MAX_CHAPTERS,
    val maxTitleLength: Int = DEFAULT_MAX_TITLE_LENGTH,
    /** Top-level and nested MP4 boxes examined while looking for chapters. */
    val maxBoxes: Int = DEFAULT_MAX_BOXES,
    val maxBoxDepth: Int = DEFAULT_MAX_BOX_DEPTH,
    /** Largest `chpl` payload that is read into memory. */
    val maxChapterAtomBytes: Int = DEFAULT_MAX_CHAPTER_ATOM_BYTES,
    val maxFolderDepth: Int = DEFAULT_MAX_FOLDER_DEPTH,
) {
    init {
        require(maxTracks > 0 && maxChapters > 0 && maxBoxes > 0 && maxBoxDepth > 0) { "limits must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_TRACKS: Int = 2_000
        const val DEFAULT_MAX_CHAPTERS: Int = 10_000
        const val DEFAULT_MAX_TITLE_LENGTH: Int = 200
        const val DEFAULT_MAX_BOXES: Int = 20_000
        const val DEFAULT_MAX_BOX_DEPTH: Int = 8
        const val DEFAULT_MAX_CHAPTER_ATOM_BYTES: Int = 1024 * 1024
        const val DEFAULT_MAX_FOLDER_DEPTH: Int = 4
    }
}
