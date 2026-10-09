package org.sableos.reader.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.reader.library.LibraryFixtures.item
import org.sableos.reader.model.LibraryItem
import org.sableos.reader.model.PublicationKind
import org.sableos.reader.model.ReadingProgress

class SeriesAndLabelTest {
    private fun volume(
        id: String,
        series: String?,
        index: Double? = null,
        done: Boolean = false,
        title: String = "T$id",
    ) = item(id, title) { it.copy(series = series, seriesIndex = index, finished = done) }

    @Test
    fun seriesGroupVolumesInReadingOrderAndSkipSingles() {
        val items = listOf(
            volume("3", "Saga", 3.0),
            volume("1", "saga", 1.0, done = true),
            volume("x", "Saga", null, title = "Extra"),
            volume("2", "SAGA", 2.0),
            volume("solo", "Alone", 1.0),
            volume("none", null),
            volume("accented", "Épée", 1.0),
            volume("plain", "Epee", 2.0),
        )
        val groups = SeriesGroups.group(items)
        assertEquals(2, groups.size)
        val saga = groups.first { it.items.size == 4 }
        assertEquals(listOf("1", "2", "3", "x"), saga.items.map { it.id })
        assertEquals("2", saga.next?.id)
        val epee = groups.first { it !== saga }
        assertEquals(setOf("accented", "plain"), epee.items.map { it.id }.toSet())
    }

    @Test
    fun aCompletedSeriesHasNoNextVolumeAndMinSizeIsHonoured() {
        val done = listOf(volume("1", "S", 1.0, true), volume("2", "S", 2.0, true))
        assertNull(SeriesGroups.group(done).single().next)
        assertEquals(1, SeriesGroups.group(listOf(volume("1", "S")), minSize = 1).size)
        assertEquals(0, SeriesGroups.group(listOf(volume("1", "S"))).size)
    }

    private fun opened(change: (LibraryItem) -> LibraryItem) = item("a") { change(it.copy(lastOpenedAt = 5L)) }

    @Test
    fun progressLabelsComeFromTheTypedLocator() {
        assertEquals("Not started", ProgressLabel.of(item("a")))
        assertEquals("Finished", ProgressLabel.of(opened { it.copy(finished = true) }))
        val page = opened { it.copy(kind = PublicationKind.COMIC, progress = ReadingProgress.page(3, 10, 1L)) }
        assertEquals("Page 4 of 10", ProgressLabel.of(page))
        val time = opened {
            it.copy(kind = PublicationKind.AUDIOBOOK, progress = ReadingProgress.time(65_000, 3_600_000, 0, 1L))
        }
        assertEquals("1:05 of 1:00:00", ProgressLabel.of(time))
        assertEquals("40%", ProgressLabel.of(opened { it.copy(progress = ReadingProgress.epub("{}", 0.4, 1L)) }))
    }

    @Test
    fun remainingTimeOnlyForUnfinishedAudiobooks() {
        val book = opened {
            it.copy(kind = PublicationKind.AUDIOBOOK, progress = ReadingProgress.time(60_000, 3_660_000, 0, 1L))
        }
        assertEquals("1:00:00 left", ProgressLabel.remaining(book))
        assertNull(ProgressLabel.remaining(book.copy(finished = true)))
        assertNull(ProgressLabel.remaining(opened { it.copy(progress = ReadingProgress.epub("{}", 0.5, 1L)) }))
        val nearEnd = book.copy(progress = ReadingProgress.time(3_650_000, 3_660_000, 0, 1L))
        assertNull(ProgressLabel.remaining(nearEnd))
    }
}
