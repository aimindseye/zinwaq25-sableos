@file:Suppress("FunctionNaming")

package org.sableos.notes.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.sableos.design.SableActionButton
import org.sableos.design.SableListFocus
import org.sableos.design.SableListKeys
import org.sableos.design.SableScreen
import org.sableos.design.SableSize
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import org.sableos.design.sableListKeys
import org.sableos.notes.core.Checklist
import org.sableos.notes.core.Note
import org.sableos.notes.core.NoteLabel
import org.sableos.notes.core.NoteSearch
import org.sableos.notes.core.NoteText
import org.sableos.notes.core.NoteTime
import org.sableos.notes.core.NotesKeys
import org.sableos.notes.core.Tasks

private const val NOTICE_MS = 3_000L

@Composable
fun NotesApp(
    state: NotesUiState,
    actions: NotesActions,
) {
    BackHandler(enabled = state.screen != Screen.List || state.searching || state.query.isNotEmpty()) {
        actions.back()
    }
    when (val screen = state.screen) {
        Screen.List -> {
            ListScreen(state, actions)
        }

        Screen.Tasks -> {
            TasksScreen(state, actions)
        }

        is Screen.Editor -> {
            val note = state.notes.firstOrNull { it.id == screen.id }
            if (note == null) {
                LaunchedEffect(screen.id) { actions.back() }
            } else {
                EditorScreen(note, actions)
            }
        }
    }
    state.confirmDelete?.let { note -> DeleteDialog(note, actions) }
    state.notice?.let { notice ->
        LaunchedEffect(notice) {
            delay(NOTICE_MS)
            actions.dismissNotice()
        }
    }
}

@Composable
private fun ListScreen(
    state: NotesUiState,
    actions: NotesActions,
) {
    val hits = remember(state.notes, state.query) { NoteSearch.search(state.notes, state.query) }
    SableScreen {
        Header(
            title = "Notes",
            subtitle = state.notice ?: listSubtitle(state.notes),
            action = "New",
            onAction = actions.newNote,
        )
        if (state.searching || state.query.isNotEmpty()) SearchField(state, hits, actions)
        if (state.loaded && hits.isEmpty()) {
            Text(
                text =
                    if (state.query.isBlank()) {
                        "No notes yet. Press C to write one, or I to import a text file."
                    } else {
                        "No note matches."
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val listState = rememberLazyListState()
        val focus = remember(listState) { SableListFocus(listState) }
        val scope = rememberCoroutineScope()
        val now = System.currentTimeMillis()
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .sableListKeys(focus, hits.map { it.note.id }, scope),
            state = focus.state,
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
        ) {
            itemsIndexed(hits, key = { _, hit -> hit.note.id }) { index, hit ->
                NoteRow(
                    hit = hit,
                    now = now,
                    modifier =
                        focus
                            .row(index, hit.note.id)
                            .onFocusChanged { if (it.hasFocus) actions.focusNote(hit.note.id) },
                    onClick = { actions.open(hit.note.id) },
                )
            }
        }
        Hint(if (hits.isEmpty()) "C new · I import · K tasks" else "${NotesKeys.LIST_HINT} · ${SableListKeys.HINT}")
    }
}

private fun listSubtitle(notes: List<Note>): String {
    val open = Tasks.collect(notes, withDone = false).size
    val count = if (notes.size == 1) "1 note" else "${notes.size} notes"
    return if (open > 0) "$count · $open open tasks" else count
}

@Composable
private fun SearchField(
    state: NotesUiState,
    hits: List<NoteSearch.Hit>,
    actions: NotesActions,
) {
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.searching) { if (state.searching) runCatching { requester.requestFocus() } }
    OutlinedTextField(
        value = state.query,
        onValueChange = actions.setQuery,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            hits.firstOrNull()?.let { actions.open(it.note.id) }
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            actions.setSearching(false)
                            focusManager.moveFocus(FocusDirection.Down)
                        }

                        KeyEvent.KEYCODE_ESCAPE -> {
                            actions.back()
                        }

                        else -> {
                            false
                        }
                    }
                },
        singleLine = true,
        placeholder = { Text("Find in notes · Enter opens") },
    )
}

@Composable
private fun NoteRow(
    hit: NoteSearch.Hit,
    now: Long,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val note = hit.note
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .heightIn(min = SableSize.ListRowHeight)
                .padding(end = SableSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelBar(note.label)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = SableSpacing.Md),
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = hit.context ?: NoteText.preview(note.body)
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NoteRowMeta(note.updatedAt, Checklist.progress(note.body), now)
    }
}

/** When the note was edited and, for a checklist, how much of it is done. */
@Composable
private fun NoteRowMeta(
    updatedAt: Long,
    progress: Checklist.Progress,
    now: Long,
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = NoteTime.label(updatedAt, now),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progress.total > 0) {
            Text(
                text = "☑ ${progress.done}/${progress.total}",
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (progress.done == progress.total) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}

@Composable
internal fun LabelBar(label: NoteLabel) {
    Box(
        modifier =
            Modifier
                .width(SableSpacing.Xs)
                .height(SableSpacing.Xxl)
                .background(
                    if (label == NoteLabel.NONE) MaterialTheme.colorScheme.outlineVariant else Color(label.argb),
                ),
    )
}

@Composable
private fun TasksScreen(
    state: NotesUiState,
    actions: NotesActions,
) {
    val tasks = remember(state.notes, state.showDone) { Tasks.collect(state.notes, state.showDone) }
    val open = tasks.count { !it.done }
    SableScreen {
        Header(
            title = "Tasks",
            subtitle = if (state.showDone) "$open open · showing done items" else "$open open",
            action = if (state.showDone) "Hide done" else "Show done",
            onAction = { actions.setShowDone(!state.showDone) },
        )
        if (tasks.isEmpty()) {
            Text(
                text = "No open tasks. In a note, Menu then L turns a line into a checklist item.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val listState = rememberLazyListState()
        val focus = remember(listState) { SableListFocus(listState) }
        val scope = rememberCoroutineScope()
        val keys = tasks.map { "${it.noteId}:${it.line}" }
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .sableListKeys(focus, keys, scope),
            state = focus.state,
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
        ) {
            itemsIndexed(tasks, key = { index, _ -> keys[index] }) { index, task ->
                TaskRow(
                    task = task,
                    modifier =
                        focus
                            .row(index, keys[index])
                            .onFocusChanged { if (it.hasFocus) actions.focusTask(task) },
                    onClick = { actions.toggleTask(task) },
                )
            }
        }
        Hint(NotesKeys.TASKS_HINT)
    }
}

@Composable
private fun TaskRow(
    task: Tasks.Task,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .heightIn(min = SableSize.TouchTarget)
                .padding(horizontal = SableSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
    ) {
        Text(
            text = if (task.done) "☑" else "☐",
            style = MaterialTheme.typography.titleLarge,
            color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.text,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (task.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.noteTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One compact header row: the Q25's 387 dp window has no room for a large title. */
@Composable
internal fun Header(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SableActionButton(text = action, onClick = onAction)
    }
}

@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DeleteDialog(
    note: Note,
    actions: NotesActions,
) {
    AlertDialog(
        onDismissRequest = { actions.askDelete(null) },
        title = { Text("Delete note?") },
        text = { Text("“${note.title}” will be deleted from this phone.") },
        confirmButton = { TextButton(onClick = { actions.delete(note) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { actions.askDelete(null) }) { Text("Keep") } },
    )
}
