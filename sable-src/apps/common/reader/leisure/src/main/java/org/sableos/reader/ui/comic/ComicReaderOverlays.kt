package org.sableos.reader.ui.comic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.sableos.reader.capability.ReaderCapabilities
import org.sableos.reader.comic.ComicFit
import org.sableos.reader.comic.ComicKeyPolicy
import org.sableos.reader.comic.ComicReadingMode
import org.sableos.reader.input.KeyContext
import org.sableos.reader.input.KeyHints
import org.sableos.reader.input.LocalEditableFocus
import org.sableos.reader.input.reportsEditableFocus

private val OVERLAY_SPACING = 12.dp
private val OVERLAY_LIST_MAX_HEIGHT = 240.dp
private const val MAX_PAGE_DIGITS = 6

/** All comic overlays are dialogs: they own focus, close with Escape/Back and never leave the viewer half-open. */
@Composable
internal fun ComicOverlays(state: ComicReaderUiState, onAction: (ComicUiAction) -> Unit) {
    val dismiss = { onAction(ComicUiAction.Show(ComicOverlay.NONE)) }
    when (state.overlay) {
        ComicOverlay.NONE -> Unit
        ComicOverlay.CONTENTS -> ContentsDialog(state, onAction, dismiss)
        ComicOverlay.BOOKMARKS -> BookmarksDialog(state, onAction, dismiss)
        ComicOverlay.MODE -> ModeDialog(state, onAction, dismiss)
        ComicOverlay.HINTS -> HintsDialog(state.settings.mode, dismiss)
    }
}

@Composable
private fun ContentsDialog(state: ComicReaderUiState, onAction: (ComicUiAction) -> Unit, dismiss: () -> Unit) {
    var text by remember { mutableStateOf(state.pageNumber.toString()) }
    val jump = {
        text.toIntOrNull()?.let { number ->
            onAction(ComicUiAction.GoToPage(number.coerceIn(1, state.pageCount.coerceAtLeast(1))))
            dismiss()
        }
        Unit
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Contents") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OVERLAY_SPACING)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(MAX_PAGE_DIGITS) },
                    label = { Text("Page (1 – ${state.pageCount})") },
                    singleLine = true,
                    modifier = Modifier.reportsEditableFocus(LocalEditableFocus.current),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { jump() }),
                )
                if (state.chapters.isNotEmpty()) ChapterList(state, onAction, dismiss)
                TextButton(onClick = { onAction(ComicUiAction.Show(ComicOverlay.BOOKMARKS)) }) {
                    Text("Bookmarks (${state.bookmarks.size})")
                }
            }
        },
        confirmButton = { TextButton(onClick = { jump() }) { Text("Go") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChapterList(state: ComicReaderUiState, onAction: (ComicUiAction) -> Unit, dismiss: () -> Unit) {
    LazyColumn(Modifier.heightIn(max = OVERLAY_LIST_MAX_HEIGHT)) {
        items(state.chapters, key = { it.firstPage }) { chapter ->
            Text(
                text = "${chapter.title} · page ${chapter.firstPage + 1}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        onAction(ComicUiAction.GoToPage(chapter.firstPage + 1))
                        dismiss()
                    }
                    .padding(vertical = OVERLAY_SPACING / 2),
            )
        }
    }
}

@Composable
private fun BookmarksDialog(state: ComicReaderUiState, onAction: (ComicUiAction) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Bookmarks") },
        text = {
            if (state.bookmarks.isEmpty()) {
                Text("No bookmarks yet. Press B to bookmark this page.")
            } else {
                LazyColumn(Modifier.heightIn(max = OVERLAY_LIST_MAX_HEIGHT)) {
                    items(state.bookmarks, key = ComicBookmarkUi::id) { bookmark ->
                        BookmarkRow(bookmark, onAction, dismiss)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
private fun BookmarkRow(bookmark: ComicBookmarkUi, onAction: (ComicUiAction) -> Unit, dismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                onAction(ComicUiAction.GoToPage(bookmark.pageIndex + 1))
                dismiss()
            }
            .padding(vertical = OVERLAY_SPACING / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(bookmark.label, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onAction(ComicUiAction.DeleteBookmark(bookmark.id)) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete ${bookmark.label}")
        }
    }
}

private fun modeLabel(mode: ComicReadingMode): String = when (mode) {
    ComicReadingMode.PAGED_LTR -> "Pages, left to right"
    ComicReadingMode.PAGED_RTL -> "Pages, right to left (manga)"
    ComicReadingMode.VERTICAL_PAGER -> "Pages, top to bottom"
    ComicReadingMode.CONTINUOUS_VERTICAL -> "Continuous scroll"
    ComicReadingMode.WEBTOON -> "Webtoon (seamless strip)"
}

@Composable
private fun ModeDialog(state: ComicReaderUiState, onAction: (ComicUiAction) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Reading mode") },
        text = {
            Column {
                ComicReadingMode.entries.forEach { mode ->
                    ChoiceRow(modeLabel(mode), state.settings.mode == mode) { onAction(ComicUiAction.SetMode(mode)) }
                }
                if (!state.settings.mode.isContinuous) {
                    Text(
                        "Fit",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = OVERLAY_SPACING),
                    )
                    FitChoice("Whole page", ComicFit.FIT_PAGE, state, onAction)
                    FitChoice("Page width", ComicFit.FIT_WIDTH, state, onAction)
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
private fun FitChoice(label: String, fit: ComicFit, state: ComicReaderUiState, onAction: (ComicUiAction) -> Unit) {
    ChoiceRow(label, state.settings.fit == fit) { onAction(ComicUiAction.SetFit(fit)) }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HintsDialog(mode: ComicReadingMode, dismiss: () -> Unit) {
    val hints = KeyHints.forContext(KeyContext(ComicKeyPolicy.surfaceOf(mode), ReaderCapabilities.COMIC))
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Keyboard shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OVERLAY_SPACING / 2)) {
                hints.forEach { hint ->
                    Row(horizontalArrangement = Arrangement.spacedBy(OVERLAY_SPACING)) {
                        Text(hint.keys, style = MaterialTheme.typography.labelLarge)
                        Text(hint.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}
