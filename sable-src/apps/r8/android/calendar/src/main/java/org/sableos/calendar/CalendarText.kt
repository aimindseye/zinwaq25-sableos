package org.sableos.calendar

import org.sableos.design.SableTexts

/**
 * Every line Sable Calendar shows, in English, by string resource name. res/values/strings.xml
 * repeats them (CalendarTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object CalendarText {
    val EN: Map<String, String> =
        mapOf(
            "add_a_title" to "Add a title.",
            "the_event_must_end_after" to "The event must end after it starts.",
            "delete_every_occurrence_of_this" to
                "Delete every occurrence of “%1\$s”? This cannot be undone.",
            "delete_this_cannot_be_undone" to "Delete “%1\$s”? This cannot be undone.",
            "calendar_event" to "Calendar event",
            "calendar" to "Calendar",
            "permission_required" to "permission required",
            "nothing_else_today" to "nothing else today",
            "all_day" to "all day",
            "c_create_t_today_v" to "C create · T today · V view · J/K event · R refresh",
            "wk" to "%1\$s %2\$s · Wk %3\$s",
            "this_event_is_no_longer" to "This event is no longer in the calendar.",
            "is_in_a_read_only" to "“%1\$s” is in a read-only calendar.",
            "refreshed" to "Refreshed.",
            "nothing_scheduled" to "Nothing scheduled.",
            "deleted" to "Deleted “%1\$s”.",
            "unable_to_delete" to "Unable to delete “%1\$s”.",
            "saved" to "Saved.",
            "local_calendarprovider_ui_account_sync" to
                "Local CalendarProvider UI · account sync remains separate.",
            "save_event" to "Save event",
            "event" to "‹  event",
            "save_changes" to "Save changes",
            "agenda" to "‹  agenda",
            "loading" to "Loading…",
            "nothing_scheduled_2" to "nothing scheduled",
            "new_event_c" to "+  new event  (C)",
            "repeats" to " · repeats",
            "where" to "Where",
            "organizer" to "Organizer",
            "guests" to "Guests",
            "notes" to "Notes",
            "edit_e" to "Edit  (E)",
            "delete_del" to "Delete  (Del)",
            "this_calendar_is_read_only" to "This calendar is read-only here.",
            "back" to "Back",
            "all_day_2" to "%1\$s · all day",
            "delete_event" to "Delete event?",
            "delete" to "Delete",
            "cancel" to "Cancel",
            "event_title" to "Event title",
            "starts" to "Starts ",
            "ends" to "Ends ",
            "times_of_repeating_and_all" to
                "Times of repeating and all-day events are kept; change them in the account's " +
                "calendar.",
            "location" to "Location",
            "unable_to_save_event" to "Unable to save event.",
            "calendar_access" to "Calendar access",
            "sable_calendar_uses_android_calendarprovider" to
                "Sable Calendar uses Android CalendarProvider. Grant calendar access to read and " +
                "create local events.",
            "review_access" to "Review access",
            "no_event_selected_j_k" to "No event selected. J/K move through the day.",
            "guests_accepted" to "%1\$s guests · %2\$s accepted",
            "enter_open_e_edit_del" to "Enter open · E edit · Del delete",
            "read_only_calendar" to "Read-only calendar",
            "all_day_3" to "All day · ",
            "no_event_this_week_c" to "No event this week. C creates one.",
            "selected" to "Selected: %1\$s",
            "enter_open_e_edit_del_2" to "[Enter] open  [E] edit  [Del] delete  [T] today",
        )

    val t = SableTexts(EN)
}
