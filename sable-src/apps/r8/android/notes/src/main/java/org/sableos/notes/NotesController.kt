package org.sableos.notes

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.sableos.notes.core.Checklist
import org.sableos.notes.core.ListKey
import org.sableos.notes.core.Note
import org.sableos.notes.core.NoteStore
import org.sableos.notes.core.NotesKeys
import org.sableos.notes.core.Tasks
import org.sableos.notes.core.TasksKey
import org.sableos.notes.ui.NotesActions
import org.sableos.notes.ui.NotesUiState
import org.sableos.notes.ui.Screen
import java.io.File
import java.util.concurrent.Executors

/**
 * State and behaviour behind the screens. Notes live in memory once loaded; edits are written to
 * [NoteStore] on one background thread, shortly after typing stops and whenever a note is closed.
 */
class NotesController(
    private val activity: ComponentActivity,
) {
    val state = NotesUiState()
    private val store = NoteStore(File(activity.filesDir, "notes"))
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val pendingSaves = mutableMapOf<String, Runnable>()
    private var exporting: Note? = null

    private val exportLauncher =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val note = exporting
            exporting = null
            if (uri != null && note != null) writeExport(uri, note)
        }

    private val importLauncher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readImport(uri)
        }

    val actions =
        NotesActions(
            newNote = { openNew("") },
            open = { show(Screen.Editor(it)) },
            back = ::back,
            edit = ::edit,
            cycleLabel = ::cycleLabel,
            delete = ::delete,
            askDelete = { state.confirmDelete = it },
            share = ::share,
            export = ::export,
            import = { importLauncher.launch(arrayOf("text/*")) },
            showTasks = { show(Screen.Tasks) },
            toggleTask = ::toggleTask,
            setQuery = { state.query = it },
            setSearching = { state.searching = it },
            setShowDone = { state.showDone = it },
            focusNote = { state.focusedId = it },
            focusTask = { state.focusedTask = it },
            dismissNotice = { state.notice = null },
        )

    fun load() {
        io.execute {
            val loaded = runCatching { store.list() }.getOrDefault(emptyList())
            main.post {
                // Text shared in before the load finished is already in memory; keep it.
                val fresh = state.notes.filter { mine -> loaded.none { it.id == mine.id } }
                state.notes = (fresh + loaded).sortedByDescending { it.updatedAt }
                state.loaded = true
            }
        }
    }

    /** Text shared from another app (ACTION_SEND) becomes a new note. */
    fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val body = listOf(subject, text).filter { it.isNotBlank() }.joinToString("\n")
        if (body.isNotBlank()) openNew(body)
    }

    /** Single keys of the list and Tasks screens; the editor handles its own keys. */
    fun onKey(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val chord = event.isAltPressed || event.isCtrlPressed || event.isMetaPressed
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) return back()
        return when (state.screen) {
            Screen.List -> NotesKeys.list(keyCode, event.isCtrlPressed, chord)?.let(::listKey) ?: false
            Screen.Tasks -> NotesKeys.tasks(keyCode, chord)?.let(::tasksKey) ?: false
            is Screen.Editor -> false
        }
    }

    fun flushAll() {
        pendingSaves.keys.toList().forEach(::flush)
    }

    /** Colour and Delete act on the focused row and do nothing without one. */
    private fun listKey(key: ListKey): Boolean {
        val focused = state.notes.firstOrNull { it.id == state.focusedId }
        when (key) {
            ListKey.New -> openNew("")
            ListKey.Search -> state.searching = true
            ListKey.Tasks -> show(Screen.Tasks)
            ListKey.Import -> importLauncher.launch(arrayOf("text/*"))
            ListKey.Label -> focused?.let { cycleLabel(it.id) }
            ListKey.Delete -> focused?.let { state.confirmDelete = it }
        }
        return focused != null || (key != ListKey.Label && key != ListKey.Delete)
    }

    private fun tasksKey(key: TasksKey): Boolean {
        when (key) {
            TasksKey.History -> state.showDone = !state.showDone
            TasksKey.OpenNote -> show(Screen.Editor(state.focusedTask?.noteId ?: return false))
        }
        return true
    }

    private fun show(screen: Screen) {
        state.searching = false
        state.screen = screen
    }

    /** Back: closes the editor (saving it) or Tasks, or clears a search; false when there is nothing to undo. */
    private fun back(): Boolean {
        when (val screen = state.screen) {
            is Screen.Editor -> {
                close(screen.id)
            }

            Screen.Tasks -> {
                state.screen = Screen.List
            }

            Screen.List -> {
                if (state.searching || state.query.isNotEmpty()) {
                    state.query = ""
                    state.searching = false
                } else {
                    return false
                }
            }
        }
        return true
    }

    private fun openNew(body: String) {
        val now = System.currentTimeMillis()
        val note = Note(NoteStore.newId(), body, now, now)
        state.notes = listOf(note) + state.notes
        if (body.isNotBlank()) scheduleSave(note.id)
        state.screen = Screen.Editor(note.id)
    }

    /** Leaving a note saves it; one left empty is dropped, as Remember did. */
    private fun close(id: String) {
        val note = state.notes.firstOrNull { it.id == id }
        if (note != null && note.body.isBlank()) remove(id) else flush(id)
        state.focusedId = id.takeIf { note?.body?.isNotBlank() == true }
        state.screen = Screen.List
    }

    private fun edit(
        id: String,
        body: String,
    ) {
        update(id) { it.copy(body = body, updatedAt = System.currentTimeMillis()) }
        scheduleSave(id)
    }

    private fun cycleLabel(id: String) {
        update(id) { it.copy(label = it.label.next(), updatedAt = System.currentTimeMillis()) }
        scheduleSave(id)
    }

    private fun toggleTask(task: Tasks.Task) {
        val note = state.notes.firstOrNull { it.id == task.noteId } ?: return
        Checklist.toggleLine(note.body, task.line)?.let { edit(note.id, it) }
    }

    private fun update(
        id: String,
        change: (Note) -> Note,
    ) {
        state.notes = state.notes.map { if (it.id == id) change(it) else it }.sortedByDescending { it.updatedAt }
    }

    private fun delete(note: Note) {
        state.confirmDelete = null
        remove(note.id)
        if ((state.screen as? Screen.Editor)?.id == note.id) state.screen = Screen.List
        state.notice = "Deleted “${note.title}”"
    }

    private fun remove(id: String) {
        pendingSaves.remove(id)?.let(main::removeCallbacks)
        state.notes = state.notes.filterNot { it.id == id }
        io.execute { runCatching { store.delete(id) } }
    }

    private fun scheduleSave(id: String) {
        pendingSaves.remove(id)?.let(main::removeCallbacks)
        val run = Runnable { flush(id) }
        pendingSaves[id] = run
        main.postDelayed(run, SAVE_DELAY_MS)
    }

    private fun flush(id: String) {
        pendingSaves.remove(id)?.let(main::removeCallbacks)
        val note = state.notes.firstOrNull { it.id == id } ?: return
        if (note.body.isBlank()) return
        io.execute {
            if (runCatching { store.save(note) }.isFailure) main.post { state.notice = "Couldn't save “${note.title}”" }
        }
    }

    private fun share(note: Note) {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, note.title)
                .putExtra(Intent.EXTRA_TEXT, note.body)
        activity.startActivity(Intent.createChooser(send, "Share note"))
    }

    private fun export(note: Note) {
        flush(note.id)
        exporting = note
        exportLauncher.launch(fileName(note.title))
    }

    private fun writeExport(
        uri: Uri,
        note: Note,
    ) {
        io.execute {
            val ok =
                runCatching {
                    activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(note.body.toByteArray()) }
                        ?: error("no stream")
                }.isSuccess
            main.post { state.notice = if (ok) "Exported “${note.title}”" else "Export failed" }
        }
    }

    private fun readImport(uri: Uri) {
        io.execute {
            val text =
                runCatching {
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = ByteArray(MAX_IMPORT_BYTES)
                        var read = 0
                        while (read < bytes.size) {
                            val n = input.read(bytes, read, bytes.size - read)
                            if (n < 0) break
                            read += n
                        }
                        String(bytes, 0, read)
                    }
                }.getOrNull()
            main.post {
                if (text.isNullOrBlank()) state.notice = "Nothing to import" else openNew(text)
            }
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 800L
        const val MAX_IMPORT_BYTES = 1 shl 20
        const val MAX_FILE_NAME_CHARS = 60
        val UNSAFE_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        fun fileName(title: String): String =
            title
                .replace(UNSAFE_FILE_CHARS, " ")
                .trim()
                .take(MAX_FILE_NAME_CHARS)
                .ifEmpty { "Note" } + ".txt"
    }
}
