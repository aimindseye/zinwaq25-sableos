package org.sableos.tasks.core

import android.view.KeyEvent
import org.sableos.tasks.core.TasksText.t

/** Keys for the whole list; they reach it only when no text field has focus. */
enum class ScreenKey { New, ShowDone }

/** Keys for the focused task row. Enter (or a tap) opens it. */
enum class RowKey { Done, Tomorrow, Delete }

/**
 * The Tasks keymap, BlackBerry style single letters: C new, H done list; on a row Space done,
 * Z remind tomorrow morning, D or Del delete. Anything with Alt or Ctrl held (a digit or symbol on the
 * Q25) is left to the app.
 */
object TasksKeys {
    val HINT: String get() = t("tasks_hint")

    fun screen(
        keyCode: Int,
        modified: Boolean,
    ): ScreenKey? =
        if (modified) {
            null
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_C -> ScreenKey.New
                KeyEvent.KEYCODE_H -> ScreenKey.ShowDone
                else -> null
            }
        }

    fun row(
        keyCode: Int,
        modified: Boolean,
    ): RowKey? =
        if (modified) {
            null
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_SPACE -> RowKey.Done
                KeyEvent.KEYCODE_Z -> RowKey.Tomorrow
                KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> RowKey.Delete
                else -> null
            }
        }
}
