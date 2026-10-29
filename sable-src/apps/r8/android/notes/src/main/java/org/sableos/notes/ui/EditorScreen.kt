@file:Suppress("FunctionNaming")

package org.sableos.notes.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import org.sableos.design.SableScreen
import org.sableos.design.SableSize
import org.sableos.design.SableSpacing
import org.sableos.design.SableSquareAction
import org.sableos.design.sableFocusRing
import org.sableos.notes.core.Checklist
import org.sableos.notes.core.EditorKey
import org.sableos.notes.core.EditorMenu
import org.sableos.notes.core.Note
import org.sableos.notes.core.NoteSearch
import org.sableos.notes.core.NoteTime
import org.sableos.notes.core.NotesKeys

/** The editor's state and commands, so the composable below only lays them out. */
private class EditorModel(
    var note: Note,
    private val actions: NotesActions,
) {
    var value by mutableStateOf(TextFieldValue(note.body, TextRange(note.body.length)))
    var finding by mutableStateOf(false)
    var query by mutableStateOf("")
    var menuOpen by mutableStateOf(false)
    var miss by mutableStateOf(false)
    val editorFocus = FocusRequester()

    fun set(next: TextFieldValue) {
        val changed = next.text != value.text
        value = next
        if (changed) actions.edit(note.id, next.text)
    }

    fun changeQuery(text: String) {
        query = text
        miss = false
    }

    fun focusEditor() {
        runCatching { editorFocus.requestFocus() }
    }

    fun closeFind() {
        finding = false
        focusEditor()
    }

    fun findNext(): Boolean {
        val hit = NoteSearch.nextMatch(value.text, query, value.selection.max)
        miss = hit == null && query.isNotEmpty()
        if (hit != null) {
            value = value.copy(selection = TextRange(hit.first, hit.last + 1))
            closeFind()
        }
        return hit != null
    }

    fun run(action: EditorMenu) {
        menuOpen = false
        val cursor = value.selection.start
        when (action) {
            EditorMenu.MakeItem -> apply(Checklist.convertAt(value.text, cursor))
            EditorMenu.ToggleItem -> apply(Checklist.toggleAt(value.text, cursor))
            EditorMenu.Find -> finding = true
            EditorMenu.FindNext -> if (query.isEmpty()) finding = true else findNext()
            EditorMenu.Label -> actions.cycleLabel(note.id)
            EditorMenu.Share -> actions.share(note)
            EditorMenu.Export -> actions.export(note)
            EditorMenu.Delete -> actions.askDelete(note)
        }
        if (!finding) focusEditor()
    }

    /** Editor keys before the text field sees them; plain Enter continues a checklist. */
    fun onPreviewKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        val down = event.type == KeyEventType.KeyDown
        val key = NotesKeys.editor(native.keyCode, native.isCtrlPressed, native.isAltPressed)
        return when {
            key != null -> {
                if (down && native.repeatCount == 0) onKey(key)
                true
            }

            down && isPlainEnter(native) && value.selection.collapsed -> {
                apply(Checklist.newline(value.text, value.selection.start))
            }

            else -> {
                false
            }
        }
    }

    private fun onKey(key: EditorKey) {
        when (key) {
            EditorKey.Save -> actions.edit(note.id, value.text)
            EditorKey.Find -> run(EditorMenu.Find)
            EditorKey.FindNext -> run(EditorMenu.FindNext)
            EditorKey.ToggleItem -> run(EditorMenu.ToggleItem)
            EditorKey.MakeItem -> run(EditorMenu.MakeItem)
            EditorKey.Label -> run(EditorMenu.Label)
            EditorKey.Export -> run(EditorMenu.Export)
            EditorKey.Menu -> menuOpen = true
            EditorKey.Close -> actions.back()
        }
    }

    private fun apply(edit: Checklist.Edit?): Boolean {
        edit?.let { set(TextFieldValue(it.text, TextRange(it.cursor))) }
        return edit != null
    }
}

/**
 * The full-screen editor (FULL_SCREEN_EDITOR_MODE: the Q25's square window has no room for a list
 * beside it). Text input owns every printable key; actions sit behind the Menu key and Ctrl chords.
 */
@Composable
internal fun EditorScreen(
    note: Note,
    actions: NotesActions,
) {
    val model = remember(note.id) { EditorModel(note, actions) }
    model.note = note

    // Registered after NotesApp's handler, so Back closes the find bar before it leaves the note.
    BackHandler(enabled = model.finding) { model.closeFind() }
    LaunchedEffect(note.id) { model.focusEditor() }

    SableScreen(modifier = Modifier.imePadding()) {
        EditorBar(note, model.value.text, onMenu = { model.menuOpen = true }, onLabel = { actions.cycleLabel(note.id) })
        if (model.finding) {
            FindBar(model.query, model.miss, model::changeQuery, onEnter = { model.findNext() })
        }
        EditorField(model)
        Hint(NotesKeys.EDITOR_HINT)
    }
    if (model.menuOpen) EditorMenuDialog(onDismiss = { model.menuOpen = false }, onAction = model::run)
}

@Composable
private fun ColumnScope.EditorField(model: EditorModel) {
    BasicTextField(
        value = model.value,
        onValueChange = model::set,
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(model.editorFocus)
                .onPreviewKeyEvent(model::onPreviewKey),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (model.value.text.isEmpty()) {
                    Text(
                        text = "Type your note. The first line is its title.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

private fun isPlainEnter(event: KeyEvent): Boolean =
    (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
        !event.isShiftPressed &&
        !event.isAltPressed &&
        !event.isCtrlPressed

@Composable
private fun EditorBar(
    note: Note,
    text: String,
    onMenu: () -> Unit,
    onLabel: () -> Unit,
) {
    val progress = Checklist.progress(text)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        Box(
            modifier =
                Modifier
                    .heightIn(min = SableSize.TouchTarget)
                    .sableFocusRing()
                    .clickable(onClickLabel = "Change colour, now ${note.label.displayName}", onClick = onLabel)
                    .padding(horizontal = SableSpacing.Sm),
            contentAlignment = Alignment.Center,
        ) {
            LabelBar(note.label)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val done = if (progress.total > 0) " · ☑ ${progress.done}/${progress.total}" else ""
            Text(
                text = "Edited ${NoteTime.label(note.updatedAt, System.currentTimeMillis())}$done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        SableSquareAction(text = "⋯", description = "Note actions (Menu key)", onClick = onMenu)
    }
}

@Composable
private fun FindBar(
    query: String,
    miss: Boolean,
    onQuery: (String) -> Unit,
    onEnter: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onPreviewKeyEvent { event ->
                    val enter =
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    if (enter && event.type == KeyEventType.KeyDown) onEnter()
                    enter
                },
        singleLine = true,
        isError = miss,
        placeholder = { Text("Find in this note · Enter goes to it") },
        supportingText = if (miss) ({ Text("Not in this note") }) else null,
    )
}

/** The Menu-key menu: Up/Down and Enter, or the item's letter. */
@Composable
private fun EditorMenuDialog(
    onDismiss: () -> Unit,
    onAction: (EditorMenu) -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier =
                    Modifier
                        .focusRequester(requester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            if (native.keyCode == KeyEvent.KEYCODE_MENU) {
                                // The up of the press that opened the menu lands here too; only a new press closes it.
                                if (event.type == KeyEventType.KeyDown) onDismiss()
                                return@onPreviewKeyEvent true
                            }
                            val item = EditorMenu.forKey(native.keyCode) ?: return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyUp) onAction(item)
                            true
                        }.verticalScroll(rememberScrollState())
                        .padding(vertical = SableSpacing.Sm),
            ) {
                EditorMenu.entries.forEach { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = SableSize.TouchTarget)
                                .sableFocusRing()
                                .clickable { onAction(item) }
                                .padding(horizontal = SableSpacing.Lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.letter.toString(),
                            modifier = Modifier.width(SableSpacing.Xl),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (item == EditorMenu.Delete) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            }
        }
    }
}
