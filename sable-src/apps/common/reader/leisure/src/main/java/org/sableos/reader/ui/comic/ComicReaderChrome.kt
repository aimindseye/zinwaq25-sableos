package org.sableos.reader.ui.comic

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val CHROME_PADDING = 8.dp
private val FOCUS_BORDER_WIDTH = 2.dp
private val FOCUS_CORNER = 8.dp
private const val CHROME_ALPHA = 0.94f

/** A visible, deterministic keyboard-focus ring that also works when the global ripple indication is disabled. */
@Composable
private fun Modifier.visibleFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused) {
        Modifier.border(FOCUS_BORDER_WIDTH, MaterialTheme.colorScheme.primary, RoundedCornerShape(FOCUS_CORNER))
    } else {
        Modifier
    }
    return onFocusChanged { focused = it.isFocused }.then(ring)
}

@Composable
private fun FocusIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.visibleFocus(), content = content)
}

@Composable
internal fun ComicTopBar(
    state: ComicReaderUiState,
    onAction: (ComicUiAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA)) {
        Row(
            modifier = Modifier.padding(CHROME_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CHROME_PADDING),
        ) {
            FocusIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
            }
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FocusIconButton(onClick = { onAction(ComicUiAction.ToggleBookmark) }) {
                Icon(
                    imageVector = if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (state.isBookmarked) "Remove bookmark (B)" else "Bookmark this page (B)",
                )
            }
            FocusIconButton(onClick = { onAction(ComicUiAction.Show(ComicOverlay.CONTENTS)) }) {
                Icon(Icons.Filled.Menu, contentDescription = "Chapters, pages and bookmarks (T)")
            }
            FocusIconButton(onClick = { onAction(ComicUiAction.Show(ComicOverlay.MODE)) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Reading mode (A)")
            }
            FocusIconButton(onClick = { onAction(ComicUiAction.Show(ComicOverlay.HINTS)) }) {
                Icon(Icons.Filled.Info, contentDescription = "Keyboard shortcuts")
            }
        }
    }
}

@Composable
internal fun ComicBottomBar(
    state: ComicReaderUiState,
    onAction: (ComicUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA)) {
        Row(
            modifier = Modifier.padding(CHROME_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FocusIconButton(onClick = { onAction(ComicUiAction.PreviousPage) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.pageNumber} / ${state.pageCount}", style = MaterialTheme.typography.labelLarge)
                state.chapterTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
            FocusIconButton(onClick = { onAction(ComicUiAction.NextPage) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
            }
        }
    }
}
