package org.sableos.notes.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class TasksAndTimeTest {
    @Test
    fun tasksAreOpenItemsLatestNoteFirst() {
        val old = Note("a", "Old\n- [ ] one\n- [x] two", 1, 1)
        val new = Note("b", "New\n- [ ] three\n- [ ] ", 2, 2)
        assertEquals(listOf("three", "one"), Tasks.collect(listOf(old, new), withDone = false).map { it.text })
        val all = Tasks.collect(listOf(old, new), withDone = true)
        assertEquals(listOf("three", "one", "two"), all.map { it.text })
        assertEquals(2, all.last().line)
        assertEquals("Old", all.last().noteTitle)
    }

    @Test
    fun timeLabelsShortenWithAge() {
        fun at(text: String) = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()
        val now = at("2026-10-29T12:00:00")

        fun label(text: String) = NoteTime.label(at(text), now, ZoneOffset.UTC, Locale.UK)
        assertEquals("09:05", label("2026-10-29T09:05:00"))
        assertEquals("Mon", label("2026-10-26T09:05:00"))
        assertEquals("3 Oct", label("2026-10-03T09:05:00"))
        assertEquals("3 Oct 2025", label("2025-10-03T09:05:00"))
    }
}
