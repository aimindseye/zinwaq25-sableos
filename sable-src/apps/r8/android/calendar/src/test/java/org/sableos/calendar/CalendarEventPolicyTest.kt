package org.sableos.calendar

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarEventPolicyTest {
    private val original =
        EventDraft(
            title = "Standup",
            startMillis = 10_000L,
            endMillis = 20_000L,
            location = "Room 2",
            description = "",
        )

    @Test
    fun draftsNeedATitleAndAnEndAfterTheStart() {
        assertNull(EventEditPolicy.validate(original))
        assertEquals("Add a title.", EventEditPolicy.validate(original.copy(title = "  ")))
        assertEquals(
            "The event must end after it starts.",
            EventEditPolicy.validate(original.copy(endMillis = original.startMillis)),
        )
    }

    @Test
    fun movingTheStartKeepsTheDuration() {
        val moved = original.withStart(50_000L)
        assertEquals(50_000L, moved.startMillis)
        assertEquals(60_000L, moved.endMillis)
        val fresh = EventDraft.newAt(1_000L)
        assertEquals(1_000L + EventDraft.DEFAULT_DURATION_MILLIS, fresh.endMillis)
    }

    @Test
    fun onlyChangedFieldsAreWritten() {
        assertTrue(EventEditPolicy.changedFields(original, original.copy(title = " Standup "), true).isEmpty())
        assertEquals(
            setOf(EventField.Title, EventField.Location),
            EventEditPolicy.changedFields(original, original.copy(title = "Retro", location = ""), true),
        )
        assertEquals(
            setOf(EventField.Start, EventField.End),
            EventEditPolicy.changedFields(original, original.withStart(15_000L), timesEditable = true),
        )
    }

    @Test
    fun repeatingAndAllDayEventsKeepTheirTimes() {
        assertTrue(EventEditPolicy.timesEditable(allDay = false, recurring = false))
        assertFalse(EventEditPolicy.timesEditable(allDay = true, recurring = false))
        assertFalse(EventEditPolicy.timesEditable(allDay = false, recurring = true))
        assertTrue(
            EventEditPolicy.changedFields(original, original.withStart(15_000L), timesEditable = false).isEmpty(),
        )
    }

    @Test
    fun deleteConfirmationNamesTheEventAndWarnsForSeries() {
        assertEquals("Delete “Standup”? This cannot be undone.", EventEditPolicy.deleteConfirmation("Standup", false))
        assertTrue(EventEditPolicy.deleteConfirmation("Standup", true).startsWith("Delete every occurrence"))
    }

    @Test
    fun keysMapToEditDeleteAndBack() {
        assertEquals(CalendarKeyAction.Edit, CalendarKeys.action(KeyEvent.KEYCODE_E, modified = false))
        assertEquals(CalendarKeyAction.Delete, CalendarKeys.action(KeyEvent.KEYCODE_DEL, modified = false))
        assertEquals(CalendarKeyAction.Delete, CalendarKeys.action(KeyEvent.KEYCODE_FORWARD_DEL, modified = false))
        assertEquals(CalendarKeyAction.Back, CalendarKeys.action(KeyEvent.KEYCODE_ESCAPE, modified = false))
        assertNull(CalendarKeys.action(KeyEvent.KEYCODE_E, modified = true))
        assertNull(CalendarKeys.action(KeyEvent.KEYCODE_D, modified = false))
        assertNull(CalendarKeys.action(KeyEvent.KEYCODE_ENTER, modified = false))
    }

    @Test
    fun dayHeadingSaysTodayOnlyForToday() {
        val today = LocalDate.of(2026, 10, 14)
        assertEquals("TODAY · 14", CalendarLabels.dayHeading(today, today))
        assertEquals("TOMORROW · 15", CalendarLabels.dayHeading(today.plusDays(1), today))
        assertEquals("YESTERDAY · 13", CalendarLabels.dayHeading(today.minusDays(1), today))
        assertEquals("SAT · 17", CalendarLabels.dayHeading(today.plusDays(3), today))
    }
}
