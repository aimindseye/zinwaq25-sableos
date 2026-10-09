package org.sableos.reader.ui.pdf

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
import org.sableos.reader.input.KeyContext
import org.sableos.reader.input.KeyHints
import org.sableos.reader.input.LocalEditableFocus
import org.sableos.reader.input.ReaderSurface
import org.sableos.reader.input.reportsEditableFocus

private val OVERLAY_SPACING = 12.dp
private val OVERLAY_LIST_MAX_HEIGHT = 240.dp
private const val MAX_PAGE_DIGITS = 6

/** All PDF overlays are dialogs: they own focus, close with Escape/Back and never leave the document half-open. */
@Composable
internal fun PdfOverlays(state: PdfReaderUiState, viewModel: PdfReaderViewModel) {
    when (state.overlay) {
        PdfOverlay.NONE -> Unit
        PdfOverlay.PAGES -> PdfPagesDialog(state, viewModel)
        PdfOverlay.BOOKMARKS -> PdfBookmarksDialog(state, viewModel)
        PdfOverlay.HINTS -> PdfHintsDialog(viewModel)
    }
}

@Composable
private fun PdfPagesDialog(state: PdfReaderUiState, viewModel: PdfReaderViewModel) {
    val dismiss = { viewModel.onAction(PdfUiAction.Show(PdfOverlay.NONE)) }
    var text by remember { mutableStateOf(state.pageNumber.toString()) }
    val jump = {
        text.toIntOrNull()?.let { number ->
            viewModel.onAction(PdfUiAction.GoToPage(number.coerceIn(1, state.pageCount.coerceAtLeast(1))))
            viewModel.onAction(PdfUiAction.Show(PdfOverlay.NONE))
        }
        Unit
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Go to page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OVERLAY_SPACING)) {
                OutlinedTextField(
                    modifier = Modifier.reportsEditableFocus(LocalEditableFocus.current),
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(MAX_PAGE_DIGITS) },
                    label = { Text("Page (1 – ${state.pageCount})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { jump() }),
                )
                TextButton(onClick = { viewModel.onAction(PdfUiAction.Show(PdfOverlay.BOOKMARKS)) }) {
                    Text("Bookmarks (${state.bookmarks.size})")
                }
            }
        },
        confirmButton = { TextButton(onClick = { jump() }) { Text("Go") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PdfBookmarksDialog(state: PdfReaderUiState, viewModel: PdfReaderViewModel) {
    val dismiss = { viewModel.onAction(PdfUiAction.Show(PdfOverlay.NONE)) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Bookmarks") },
        text = {
            if (state.bookmarks.isEmpty()) {
                Text("No bookmarks yet. Press B to bookmark this page.")
            } else {
                LazyColumn(Modifier.heightIn(max = OVERLAY_LIST_MAX_HEIGHT)) {
                    items(state.bookmarks, key = PdfBookmarkUi::id) { bookmark ->
                        BookmarkRow(bookmark, viewModel)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
private fun BookmarkRow(bookmark: PdfBookmarkUi, viewModel: PdfReaderViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                viewModel.onAction(PdfUiAction.GoToPage(bookmark.pageIndex + 1))
                viewModel.onAction(PdfUiAction.Show(PdfOverlay.NONE))
            }
            .padding(vertical = OVERLAY_SPACING / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(bookmark.label, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { viewModel.onAction(PdfUiAction.DeleteBookmark(bookmark.id)) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete ${bookmark.label}")
        }
    }
}

@Composable
private fun PdfHintsDialog(viewModel: PdfReaderViewModel) {
    val dismiss = { viewModel.onAction(PdfUiAction.Show(PdfOverlay.NONE)) }
    val hints = KeyHints.forContext(KeyContext(ReaderSurface.PDF_PAGED, ReaderCapabilities.PDF))
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
