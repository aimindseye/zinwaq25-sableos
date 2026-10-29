package org.sableos.notes.core

import org.sableos.design.SableTexts

/**
 * Every line Sable Notes shows, in English, by string resource name. res/values/strings.xml
 * repeats them (NotesTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object NotesText {
    val EN: Map<String, String> =
        mapOf(
            "deleted" to "Deleted “%1\$s”",
            "couldn_t_save" to "Couldn't save “%1\$s”",
            "share_note" to "Share note",
            "exported" to "Exported “%1\$s”",
            "export_failed" to "Export failed",
            "nothing_to_import" to "Nothing to import",
            "note" to "Note",
            "no_colour" to "No colour",
            "yellow" to "Yellow",
            "orange" to "Orange",
            "red" to "Red",
            "pink" to "Pink",
            "purple" to "Purple",
            "blue" to "Blue",
            "green" to "Green",
            "empty_note" to "Empty note",
            "list_item_on_this_line" to "List item on this line",
            "tick_or_untick_this_item" to "Tick or untick this item",
            "find_in_note" to "Find in note",
            "find_next" to "Find next",
            "colour" to "Colour",
            "share" to "Share",
            "export_to_a_file" to "Export to a file",
            "delete_note" to "Delete note",
            "c_new_s_find_k" to "C new · S find · K tasks · L colour · D delete",
            "menu_list_item_tick_find" to "Menu: list item, tick, find, colour, share · Back saves",
            "enter_tick_o_open_note" to "Enter tick · O open note · H done items · Back",
            "type_your_note_the_first" to "Type your note. The first line is its title.",
            "change_colour_now" to "Change colour, now %1\$s",
            "edited" to "Edited %1\$s%2\$s",
            "note_actions_menu_key" to "Note actions (Menu key)",
            "find_in_this_note_enter" to "Find in this note · Enter goes to it",
            "not_in_this_note" to "Not in this note",
            "notes" to "Notes",
            "new_label" to "New",
            "no_notes_yet_press_c" to
                "No notes yet. Press C to write one, or I to import a text file.",
            "no_note_matches" to "No note matches.",
            "c_new_i_import_k" to "C new · I import · K tasks",
            "one_note" to "1 note",
            "note_count" to "%1\$s notes",
            "open_tasks" to "%1\$s · %2\$s open tasks",
            "find_in_notes_enter_opens" to "Find in notes · Enter opens",
            "tasks" to "Tasks",
            "open_showing_done_items" to "%1\$s open · showing done items",
            "open" to "%1\$s open",
            "hide_done" to "Hide done",
            "show_done" to "Show done",
            "no_open_tasks_in_a" to
                "No open tasks. In a note, Menu then L turns a line into a checklist item.",
            "delete_note_question" to "Delete note?",
            "will_be_deleted_from_this" to "“%1\$s” will be deleted from this phone.",
            "delete" to "Delete",
            "keep" to "Keep",
        )

    val t = SableTexts(EN)
}
