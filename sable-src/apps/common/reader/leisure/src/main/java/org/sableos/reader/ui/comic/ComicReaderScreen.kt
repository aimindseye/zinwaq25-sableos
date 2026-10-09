package org.sableos.reader.ui.comic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sableos.reader.comic.ComicTapAction
import org.sableos.reader.comic.ComicTapZones
import org.sableos.reader.layout.rememberReaderLayoutProfile

private val MESSAGE_PADDING = 16.dp

/**
 * Keyboard-first comic reader. Five reading modes (paged left-to-right, paged right-to-left, vertical pager,
 * continuous scroll, webtoon), per-title persistence of the mode, bounded-memory page decoding, chapters from the
 * folder structure, page bookmarks and typed page progress. There is no text search, highlighting or speech for comics.
 */
@Composable
fun ComicReaderScreen(
    bookHash: String,
    onBack: () -> Unit,
    viewModel: ComicReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = rememberReaderLayoutProfile()
    LaunchedEffect(bookHash, profile.transientChrome) {
        viewModel.open(bookHash, profile.chromeInitiallyVisible, profile.transientChrome)
    }
    val leave = rememberUpdatedState {
        viewModel.closeComic()
        onBack()
    }
    val scroll = remember { ScrollTarget() }
    BackHandler { if (!viewModel.dismissTransientSurface()) leave.value() }
    ComicKeyboard(viewModel, scroll, onLeave = { leave.value() })

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ComicViewport(state, viewModel, scroll)
        if (state.chromeVisible || !state.transientChrome) {
            ComicTopBar(
                state = state,
                onAction = viewModel::onAction,
                onBack = { leave.value() },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            ComicBottomBar(state, viewModel::onAction, modifier = Modifier.align(Alignment.BottomCenter))
        }
        ComicOverlays(state, viewModel::onAction)
    }
}

@Composable
private fun ComicViewport(state: ComicReaderUiState, viewModel: ComicReaderViewModel, scroll: ScrollTarget) {
    val mode = state.settings.mode
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                scroll.viewportHeightPx = it.height
                viewModel.onViewportChanged(it.width, it.height)
            }
            .pointerInput(mode) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val action = ComicTapZones.resolve(mode, offset.x, offset.y, width, size.height.toFloat())
                    viewModel.onAction(
                        when (action) {
                            ComicTapAction.PREVIOUS_PAGE -> ComicUiAction.PreviousPage
                            ComicTapAction.NEXT_PAGE -> ComicUiAction.NextPage
                            ComicTapAction.TOGGLE_CHROME -> ComicUiAction.ToggleChrome
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.error != null -> ComicErrorMessage(state.error)
            state.isLoading || state.pageCount == 0 -> CircularProgressIndicator()
            // Recreating the viewer on a mode change restarts it at the current page.
            else -> key(mode) {
                if (mode.isContinuous) StripViewer(state, viewModel, scroll) else PagerViewer(state, viewModel)
            }
        }
    }
}

@Composable
private fun ComicErrorMessage(error: ComicReaderError) {
    val message = when (error) {
        ComicReaderError.NotFound -> "This comic is no longer in your library."
        is ComicReaderError.CannotOpen -> {
            val reason = error.failure.name.lowercase().replace('_', ' ')
            "This comic cannot be opened ($reason)."
        }
    }
    Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(MESSAGE_PADDING))
}
