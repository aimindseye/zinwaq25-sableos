package org.sableos.reader.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import org.sableos.reader.engine.audio.AudioTimeFormat
import org.sableos.reader.layout.rememberReaderLayoutProfile

private val SCREEN_PADDING = 16.dp
private val COVER_SIZE = 220.dp
private val COMPACT_COVER_SIZE = 120.dp

/**
 * Keyboard-first audiobook player. Playback runs in a foreground media service (lock screen, notification and headset
 * controls work with the screen closed); this screen mirrors it. Space plays or pauses, Left/Right skip,
 * PageUp/PageDown change chapter, B bookmarks, T lists chapters, A opens playback settings (speed, skip, sleep timer).
 */
@Composable
fun AudiobookReaderScreen(
    bookHash: String,
    onBack: () -> Unit,
    viewModel: AudiobookReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(bookHash) { viewModel.open(bookHash) }
    val leave = rememberUpdatedState {
        viewModel.closeScreen()
        onBack()
    }
    val togglePlayback = rememberPlaybackToggle(viewModel)
    BackHandler {
        if (state.hasTransientSurface) viewModel.onAction(AudioUiAction.Show(AudioOverlay.NONE)) else leave.value()
    }
    AudiobookKeyboard(viewModel, onPlayPause = togglePlayback, onLeave = { leave.value() })

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.error != null && state.title.isEmpty() -> AudioMessage(errorText(state.error))
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> PlayerContent(state, viewModel, togglePlayback, onBack = { leave.value() })
        }
        AudiobookOverlays(state, viewModel::onAction)
    }
}

/** Starts or pauses playback; the first start on Android 13+ also asks once for the notification permission. */
@Composable
private fun rememberPlaybackToggle(viewModel: AudiobookReaderViewModel): () -> Unit {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Playback works either way; without the permission only the notification is hidden.
    }
    return {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.onAction(AudioUiAction.PlayPause)
    }
}

@Composable
private fun AudioMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(SCREEN_PADDING), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun errorText(error: AudiobookError?): String = when (error) {
    AudiobookError.NotFound -> "This audiobook is no longer in your library."
    AudiobookError.Unreadable -> "This audiobook can no longer be read. Check that its files are still on the device."
    AudiobookError.PlaybackFailed -> "Playback could not be started."
    null -> ""
}

@Composable
private fun PlayerContent(
    state: AudiobookUiState,
    viewModel: AudiobookReaderViewModel,
    togglePlayback: () -> Unit,
    onBack: () -> Unit,
) {
    val compact = rememberReaderLayoutProfile().let { it.isCompact || it.isSquareish }
    Column(
        modifier = Modifier.fillMaxSize().padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SCREEN_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AudioTopRow(state, viewModel::onAction, onBack)
        Cover(state, if (compact) COMPACT_COVER_SIZE else COVER_SIZE)
        Text(
            state.chapterTitle,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        state.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
        AudioSeekBar(state, viewModel::onAction)
        AudioTransport(state, viewModel::onAction, togglePlayback)
        AudioStatusRow(state, viewModel::onAction)
    }
}

@Composable
private fun AudioTopRow(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
        }
        Text(
            state.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onAction(AudioUiAction.AddBookmark) }) {
            Icon(Icons.Filled.Bookmark, contentDescription = "Bookmark this position (B)")
        }
        IconButton(onClick = { onAction(AudioUiAction.Show(AudioOverlay.CHAPTERS)) }) {
            Icon(Icons.Filled.Menu, contentDescription = "Chapters and bookmarks (T)")
        }
        IconButton(onClick = { onAction(AudioUiAction.Show(AudioOverlay.SETTINGS)) }) {
            Icon(Icons.Filled.Settings, contentDescription = "Speed, skip and sleep timer (A)")
        }
        IconButton(onClick = { onAction(AudioUiAction.Show(AudioOverlay.HINTS)) }) {
            Icon(Icons.Filled.Info, contentDescription = "Keyboard shortcuts")
        }
    }
}

@Composable
private fun Cover(state: AudiobookUiState, size: Dp) {
    val path = state.coverPath
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = "Cover of ${state.title}",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun AudioSeekBar(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = dragging ?: state.positionMs.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onAction(AudioUiAction.SeekTo(it.toLong())) }
                dragging = null
            },
            valueRange = 0f..duration.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = dragging?.toLong() ?: state.positionMs
            Text(AudioTimeFormat.clock(shown), style = MaterialTheme.typography.labelMedium)
            Text("-" + AudioTimeFormat.clock(state.durationMs - shown), style = MaterialTheme.typography.labelMedium)
        }
    }
}
