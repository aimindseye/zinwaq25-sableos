package org.sableos.tasks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.sableos.tasks.core.ScreenKey
import org.sableos.tasks.core.TaskRecord

/** Screen state; every field is Compose state, so readers recompose on change. */
class TasksState {
    var tasks by mutableStateOf<List<TaskRecord>>(emptyList())
    var showDone by mutableStateOf(false)
    var notificationsAllowed by mutableStateOf(true)
    var status by mutableStateOf<String?>(null)

    /** The task being edited (a new one is not saved until Save), or null on the list. */
    var editing by mutableStateOf<TaskRecord?>(null)
    var editingIsNew by mutableStateOf(false)
    var pendingDelete by mutableStateOf<TaskRecord?>(null)

    fun openEditor(
        task: TaskRecord,
        isNew: Boolean,
    ) {
        status = null
        editing = task
        editingIsNew = isNew
    }

    fun closeEditor() {
        editing = null
        editingIsNew = false
    }

    /** C new, H done list; not while editing or confirming. */
    fun onScreenKey(key: ScreenKey): Boolean {
        if (editing != null || pendingDelete != null) return false
        when (key) {
            ScreenKey.New -> openEditor(TaskRecord(title = NEW_PLACEHOLDER), isNew = true)
            ScreenKey.ShowDone -> showDone = !showDone
        }
        return true
    }

    companion object {
        /** A new task starts with an empty title field; the record needs some title until Save. */
        const val NEW_PLACEHOLDER = "…"
    }
}
