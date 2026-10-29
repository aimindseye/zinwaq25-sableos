package org.sableos.calendar

import android.view.KeyEvent
import org.sableos.calendar.CalendarText.t
import java.time.LocalDate

/** The editable fields of one event, as the editor holds them. */
data class EventDraft(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String = "",
    val description: String = "",
) {
    /** Moving the start keeps the event's length, so a new start never ends before it begins. */
    fun withStart(newStart: Long): EventDraft =
        copy(
            startMillis = newStart,
            endMillis =
                newStart + (endMillis - startMillis),
        )

    companion object {
        const val DEFAULT_DURATION_MILLIS = 60 * 60 * 1000L

        fun newAt(startMillis: Long): EventDraft =
            EventDraft(
                title = "",
                startMillis = startMillis,
                endMillis =
                    startMillis + DEFAULT_DURATION_MILLIS,
            )
    }
}

enum class EventField {
    Title,
    Location,
    Description,
    Start,
    End,
}

object EventEditPolicy {
    /** Why [draft] cannot be saved, or null when it can. */
    fun validate(draft: EventDraft): String? =
        when {
            draft.title.isBlank() -> t("add_a_title")
            draft.endMillis <= draft.startMillis -> t("the_event_must_end_after")
            else -> null
        }

    /**
     * Times are edited only for a single, timed event: changing DTSTART of a repeating series moves
     * every occurrence, and all-day events are stored at UTC midnight. Those keep their times and
     * can still be renamed or relocated here.
     */
    fun timesEditable(
        allDay: Boolean,
        recurring: Boolean,
    ): Boolean = !allDay && !recurring

    /** Fields that differ between [original] and [draft]; only these are written to the provider. */
    fun changedFields(
        original: EventDraft,
        draft: EventDraft,
        timesEditable: Boolean,
    ): Set<EventField> =
        buildSet {
            if (draft.title.trim() != original.title.trim()) add(EventField.Title)
            if (draft.location.trim() != original.location.trim()) add(EventField.Location)
            if (draft.description.trim() != original.description.trim()) add(EventField.Description)
            if (timesEditable && draft.startMillis != original.startMillis) add(EventField.Start)
            if (timesEditable && draft.endMillis != original.endMillis) add(EventField.End)
        }

    /** The explicit question asked before a delete; a repeating event deletes every occurrence. */
    fun deleteConfirmation(
        title: String,
        recurring: Boolean,
    ): String =
        if (recurring) {
            t("delete_every_occurrence_of_this", title)
        } else {
            t("delete_this_cannot_be_undone", title)
        }
}

/** Calendar keyboard actions (Q25: letters, Del, Esc; no Fn key). */
enum class CalendarKeyAction {
    Edit,
    Delete,
    Back,
}

object CalendarKeys {
    /**
     * E edits, Del (Backspace on the Q25) or Forward-Delete asks to delete, Esc goes back. Enter
     * opens a focused row through its click handler. Keys with Alt/Ctrl held are left alone.
     */
    fun action(
        keyCode: Int,
        modified: Boolean,
    ): CalendarKeyAction? =
        when {
            modified -> null
            keyCode == KeyEvent.KEYCODE_E -> CalendarKeyAction.Edit
            keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL -> CalendarKeyAction.Delete
            keyCode == KeyEvent.KEYCODE_ESCAPE -> CalendarKeyAction.Back
            else -> null
        }
}

object CalendarLabels {
    /** "TODAY · 14" only for today; other days name themselves ("TOMORROW · 15", "FRI · 17"). */
    fun dayHeading(
        selected: LocalDate,
        today: LocalDate,
    ): String {
        val name =
            when (selected) {
                today -> "TODAY"
                today.plusDays(1) -> "TOMORROW"
                today.minusDays(1) -> "YESTERDAY"
                else -> selected.dayOfWeek.name.take(DAY_ABBREVIATION_LENGTH)
            }
        return name + " · " + selected.dayOfMonth
    }

    private const val DAY_ABBREVIATION_LENGTH = 3
}
