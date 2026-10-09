package org.sableos.reader.library

import org.sableos.reader.model.EpubLocator
import org.sableos.reader.model.FinishedPolicy
import org.sableos.reader.model.LibraryItem
import org.sableos.reader.model.PageLocator
import org.sableos.reader.model.TimeLocator

/** The one-line progress text for a library card, from the typed locator, never from a lossy integer. */
object ProgressLabel {
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val PERCENT = 100

    fun of(item: LibraryItem): String {
        val locator = item.progress?.locator
        return when {
            item.finished -> "Finished"
            locator == null || !item.hasBeenOpened -> "Not started"
            locator is PageLocator -> "Page ${locator.pageIndex + 1} of ${locator.pageCount}"
            locator is TimeLocator -> "${clock(locator.positionMs)} of ${clock(locator.durationMs)}"
            locator is EpubLocator -> "${(locator.fraction * PERCENT).toInt()}%"
            else -> ""
        }
    }

    /** Remaining listening time for an audiobook, or null for anything else. */
    fun remaining(item: LibraryItem): String? {
        val locator = item.progress?.locator as? TimeLocator ?: return null
        val left = (locator.durationMs - locator.positionMs).coerceAtLeast(0L)
        return if (item.finished || left <= FinishedPolicy.AUDIO_REMAINING_MS) null else "${clock(left)} left"
    }

    private fun clock(ms: Long): String {
        val total = (ms / MS_PER_SECOND).coerceAtLeast(0L)
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}
