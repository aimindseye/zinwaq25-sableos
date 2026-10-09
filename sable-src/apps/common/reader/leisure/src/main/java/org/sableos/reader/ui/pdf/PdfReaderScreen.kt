package org.sableos.reader.ui.pdf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sableos.reader.capability.ReaderCapabilities
import org.sableos.reader.input.KeyContext
import org.sableos.reader.input.KeyInput
import org.sableos.reader.input.KeyResolution
import org.sableos.reader.input.LocalEditableFocus
import org.sableos.reader.input.LocalReaderKeyDispatcher
import org.sableos.reader.input.ReaderAction
import org.sableos.reader.input.ReaderKeyMap
import org.sableos.reader.input.ReaderSurface
import org.sableos.reader.layout.rememberReaderLayoutProfile

/** Fraction of the page width at each edge that turns pages when tapped; the middle toggles the controls. */
private const val TAP_EDGE_FRACTION = 0.25f

/**
 * Keyboard-first PDF reader (Android PdfRenderer; page reading, progress, page bookmarks, page navigation).
 * Text search, highlighting and speech do not exist for PDF in v2.0, so no such control or shortcut is offered.
 */
@Composable
fun PdfReaderScreen(
    bookHash: String,
    onBack: () -> Unit,
    viewModel: PdfReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = rememberReaderLayoutProfile()
    LaunchedEffect(bookHash, profile.transientChrome) {
        viewModel.open(bookHash, profile.chromeInitiallyVisible, profile.transientChrome)
    }
    val leave = rememberUpdatedState {
        viewModel.closeDocument()
        onBack()
    }
    BackHandler { if (!viewModel.dismissTransientSurface()) leave.value() }
    PdfKeyboard(viewModel, onLeave = { leave.value() })

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PdfPageArea(state, viewModel)
        if (state.chromeVisible || !state.transientChrome) {
            PdfTopBar(state, viewModel, onBack = { leave.value() }, modifier = Modifier.align(Alignment.TopCenter))
            PdfBottomBar(state, viewModel, modifier = Modifier.align(Alignment.BottomCenter))
        }
        PdfOverlays(state, viewModel)
    }
}

@Composable
private fun PdfKeyboard(viewModel: PdfReaderViewModel, onLeave: () -> Unit) {
    val dispatcher = LocalReaderKeyDispatcher.current
    val editable = LocalEditableFocus.current
    val latestLeave = rememberUpdatedState(onLeave)
    DisposableEffect(dispatcher, viewModel) {
        val unregister = dispatcher.register { input ->
            handlePdfKey(input, viewModel, editable.focused, latestLeave.value)
        }
        onDispose(unregister)
    }
}

private fun handlePdfKey(
    input: KeyInput,
    viewModel: PdfReaderViewModel,
    editing: Boolean,
    onLeave: () -> Unit,
): Boolean {
    val context = KeyContext(ReaderSurface.PDF_PAGED, ReaderCapabilities.PDF, editableFocused = editing)
    val action = (ReaderKeyMap.resolve(input, context) as? KeyResolution.Handled)?.action
    val overlayOpen = viewModel.uiState.value.overlay != PdfOverlay.NONE
    val intent = if (overlayOpen && action != ReaderAction.DISMISS_OR_BACK) null else action?.let(::toUiAction)
    return when {
        action == ReaderAction.DISMISS_OR_BACK && !overlayOpenOrChromeDismissed(viewModel) -> {
            onLeave()
            true
        }
        action == ReaderAction.DISMISS_OR_BACK -> true
        intent != null -> {
            viewModel.onAction(intent)
            true
        }
        else -> false
    }
}

private fun overlayOpenOrChromeDismissed(viewModel: PdfReaderViewModel): Boolean = viewModel.dismissTransientSurface()

private fun toUiAction(action: ReaderAction): PdfUiAction? = when (action) {
    ReaderAction.PAGE_NEXT, ReaderAction.ADVANCE -> PdfUiAction.NextPage
    ReaderAction.PAGE_PREVIOUS, ReaderAction.REVERSE -> PdfUiAction.PreviousPage
    ReaderAction.TOGGLE_BOOKMARK -> PdfUiAction.ToggleBookmark
    ReaderAction.OPEN_CONTENTS -> PdfUiAction.Show(PdfOverlay.PAGES)
    ReaderAction.TOGGLE_CHROME -> PdfUiAction.ToggleChrome
    else -> null
}

@Composable
private fun PdfPageArea(state: PdfReaderUiState, viewModel: PdfReaderViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewModel.onViewportChanged(it.width, it.height) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val edge = size.width * TAP_EDGE_FRACTION
                    when {
                        offset.x < edge -> viewModel.onAction(PdfUiAction.PreviousPage)
                        offset.x > size.width - edge -> viewModel.onAction(PdfUiAction.NextPage)
                        else -> viewModel.onAction(PdfUiAction.ToggleChrome)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = state.page
        when {
            state.error != null -> PdfErrorMessage(state.error)
            bitmap != null -> {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "Page ${state.pageNumber} of ${state.pageCount}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> CircularProgressIndicator(Modifier.padding(PROGRESS_PADDING))
        }
    }
}

private val PROGRESS_PADDING = 16.dp

@Composable
private fun PdfErrorMessage(error: PdfReaderError) {
    val message = when (error) {
        PdfReaderError.NotFound -> "This PDF is no longer in your library."
        is PdfReaderError.CannotOpen -> when (error.reason) {
            org.sableos.reader.core.pdf.PdfOpenFailure.PASSWORD_PROTECTED -> "This PDF is password protected."
            org.sableos.reader.core.pdf.PdfOpenFailure.CORRUPT -> "This PDF is damaged and cannot be displayed."
            org.sableos.reader.core.pdf.PdfOpenFailure.UNREADABLE -> "This PDF could not be read."
        }
    }
    Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(PROGRESS_PADDING))
}
