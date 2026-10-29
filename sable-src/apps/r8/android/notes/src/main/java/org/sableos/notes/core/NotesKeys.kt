package org.sableos.notes.core

import android.view.KeyEvent

/** List screen keys. Single letters only reach the list when no text field has focus. */
enum class ListKey { New, Search, Tasks, Label, Delete, Import }

/**
 * Editor keys. The Q25 has no Ctrl or Esc, so its way in is the Menu key ([EditorMenu]) and Back;
 * the Ctrl chords are for keyboards that have them. Typing never triggers any of them.
 */
enum class EditorKey { Save, Find, FindNext, ToggleItem, MakeItem, Label, Export, Menu, Close }

/** The editor's Menu-key menu: one letter each, as on BlackBerry menus. */
enum class EditorMenu(
    val key: Int,
    val letter: Char,
    val label: String,
) {
    MakeItem(KeyEvent.KEYCODE_L, 'L', "List item on this line"),
    ToggleItem(KeyEvent.KEYCODE_T, 'T', "Tick or untick this item"),
    Find(KeyEvent.KEYCODE_F, 'F', "Find in note"),
    FindNext(KeyEvent.KEYCODE_G, 'G', "Find next"),
    Label(KeyEvent.KEYCODE_C, 'C', "Colour"),
    Share(KeyEvent.KEYCODE_S, 'S', "Share"),
    Export(KeyEvent.KEYCODE_E, 'E', "Export to a file"),
    Delete(KeyEvent.KEYCODE_D, 'D', "Delete note"),
    ;

    companion object {
        fun forKey(keyCode: Int): EditorMenu? = entries.firstOrNull { it.key == keyCode }
    }
}

/** Tasks screen keys. */
enum class TasksKey { OpenNote, History }

/**
 * The Notes keymap (UTILITIES_NOTES_RECORDER_UX: Ctrl+N new, Ctrl+S save, Ctrl+F find, Ctrl+Enter
 * complete checklist item, Esc leave the editor). T, B, N, P and Space stay the shared list moves
 * ([org.sableos.design.SableListKeys]), so C composes a new note, as on BlackBerry.
 */
object NotesKeys {
    const val LIST_HINT = "C new · S find · K tasks · L colour · D delete"
    const val EDITOR_HINT = "Menu: list item, tick, find, colour, share · Back saves"
    const val TASKS_HINT = "Enter tick · O open note · H done items · Back"

    fun list(
        keyCode: Int,
        ctrl: Boolean,
        chord: Boolean,
    ): ListKey? =
        when {
            ctrl && keyCode == KeyEvent.KEYCODE_N -> {
                ListKey.New
            }

            ctrl && keyCode == KeyEvent.KEYCODE_F -> {
                ListKey.Search
            }

            chord -> {
                null
            }

            else -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_C -> ListKey.New
                    KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_SLASH -> ListKey.Search
                    KeyEvent.KEYCODE_K -> ListKey.Tasks
                    KeyEvent.KEYCODE_L -> ListKey.Label
                    KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_FORWARD_DEL -> ListKey.Delete
                    KeyEvent.KEYCODE_I -> ListKey.Import
                    else -> null
                }
            }
        }

    fun editor(
        keyCode: Int,
        ctrl: Boolean,
        alt: Boolean,
    ): EditorKey? {
        val enter = keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        return when {
            keyCode == KeyEvent.KEYCODE_ESCAPE -> EditorKey.Close

            keyCode == KeyEvent.KEYCODE_MENU -> EditorKey.Menu

            // Enter has no Alt character on the Q25, so Alt+Enter ticks there.
            enter && (ctrl || alt) -> EditorKey.ToggleItem

            ctrl -> CTRL[keyCode]

            else -> null
        }
    }

    private val CTRL =
        mapOf(
            KeyEvent.KEYCODE_S to EditorKey.Save,
            KeyEvent.KEYCODE_F to EditorKey.Find,
            KeyEvent.KEYCODE_G to EditorKey.FindNext,
            KeyEvent.KEYCODE_T to EditorKey.MakeItem,
            KeyEvent.KEYCODE_L to EditorKey.Label,
            KeyEvent.KEYCODE_E to EditorKey.Export,
        )

    fun tasks(
        keyCode: Int,
        chord: Boolean,
    ): TasksKey? =
        when {
            chord -> null
            keyCode == KeyEvent.KEYCODE_O -> TasksKey.OpenNote
            keyCode == KeyEvent.KEYCODE_H -> TasksKey.History
            else -> null
        }
}
