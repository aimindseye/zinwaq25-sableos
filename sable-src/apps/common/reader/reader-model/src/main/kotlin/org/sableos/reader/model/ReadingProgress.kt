package org.sableos.reader.model

/**
 * Typed reading progress. The persisted truth is [locator]; [fraction] is derived so no format is
 * ever collapsed into a lossy integer.
 */
data class ReadingProgress(
    val locator: Locator,
    val updatedAt: Long,
) {
    val fraction: Double get() = locator.fraction

    val isFinished: Boolean get() = FinishedPolicy.isFinished(locator)

    companion object {
        fun epub(locatorJson: String, progression: Double, updatedAt: Long) =
            ReadingProgress(EpubLocator(locatorJson, progression), updatedAt)

        fun page(pageIndex: Int, pageCount: Int, updatedAt: Long) =
            ReadingProgress(PageLocator(pageIndex, pageCount), updatedAt)

        fun time(positionMs: Long, durationMs: Long, chapterIndex: Int, updatedAt: Long) =
            ReadingProgress(TimeLocator(positionMs, durationMs, chapterIndex), updatedAt)
    }
}

/** Named thresholds that decide when a publication counts as finished. */
object FinishedPolicy {
    /** EPUB total progression at or beyond which a book is finished. */
    const val EPUB_FRACTION: Double = 0.99

    /** An audiobook is finished when no more than this much playback remains. */
    const val AUDIO_REMAINING_MS: Long = 30_000L

    fun isFinished(locator: Locator): Boolean = when (locator) {
        is EpubLocator -> locator.fraction >= EPUB_FRACTION
        is PageLocator -> locator.pageCount > 0 && locator.pageIndex >= locator.pageCount - 1
        is TimeLocator ->
            locator.durationMs > 0L && locator.durationMs - locator.positionMs <= AUDIO_REMAINING_MS
    }
}
