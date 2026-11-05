package org.sableos.tasks.core

import org.sableos.design.SableTexts

/**
 * Every line Sable Tasks shows, in English, by string resource name. res/values/strings.xml
 * repeats them (TasksTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 */
object TasksText {
    val EN: Map<String, String> =
        mapOf(
            "tasks" to "tasks",
            "tasks_hint" to "C new · Enter edit · Space done · Z tomorrow · D delete · H done list",
            "no_tasks" to "No tasks. C adds one.",
            "group_overdue" to "Overdue",
            "group_today" to "Today",
            "group_tomorrow" to "Tomorrow",
            "group_this_week" to "This week",
            "group_later" to "Later",
            "group_no_date" to "No date",
            "group_done" to "Done",
            "due_at" to "Due %1\$s",
            "remind_at" to "Remind %1\$s",
            "from_app" to "From %1\$s",
            "every_day" to "Every day",
            "every_week" to "Every week",
            "every_month" to "Every month",
            "new_task" to "New task",
            "edit_task" to "Edit task",
            "title" to "Title",
            "details" to "Details",
            "due" to "Due",
            "remind" to "Remind",
            "none" to "None",
            "pick_time" to "Pick a time",
            "later_today" to "Later today",
            "this_evening" to "This evening",
            "tomorrow_morning" to "Tomorrow morning",
            "repeat" to "Repeat: %1\$s",
            "never" to "Never",
            "save" to "Save",
            "cancel" to "Cancel",
            "delete" to "Delete",
            "delete_task" to "Delete “%1\$s”?",
            "deleted" to "Deleted “%1\$s”",
            "add_a_title" to "Add a title.",
            "reminders" to "Reminders",
            "task_reminder" to "Task reminder",
            "done" to "Done",
            "snooze_1_hour" to "Snooze 1 hour",
            "reminders_off" to "Reminder alerts are off. Enter allows them; reminders still show here.",
            "follow_up_in" to "Follow up in %1\$s",
            "show_done" to "Show done (H)",
            "hide_done" to "Hide done (H)",
        )

    val t = SableTexts(EN)
}
