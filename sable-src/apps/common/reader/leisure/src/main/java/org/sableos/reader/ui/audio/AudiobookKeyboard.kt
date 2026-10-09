package org.sableos.reader.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import org.sableos.reader.audio.AudioKeyCommand
import org.sableos.reader.audio.AudioKeyPolicy
import org.sableos.reader.input.LocalEditableFocus
import org.sableos.reader.input.LocalReaderKeyDispatcher

/** Registers the audiobook key handler with the Activity-level dispatcher for as long as the screen is composed. */
@Composable
internal fun AudiobookKeyboard(
    viewModel: AudiobookReaderViewModel,
    onPlayPause: () -> Unit,
    onLeave: () -> Unit,
) {
    val dispatcher = LocalReaderKeyDispatcher.current
    val editable = LocalEditableFocus.current
    val latestPlayPause = rememberUpdatedState(onPlayPause)
    val latestLeave = rememberUpdatedState(onLeave)
    DisposableEffect(dispatcher, viewModel) {
        val unregister = dispatcher.register { input ->
            val overlayOpen = viewModel.uiState.value.hasTransientSurface
            val command = AudioKeyPolicy.decide(input, editable.focused, overlayOpen)
            command?.let { perform(it, viewModel, latestPlayPause.value, latestLeave.value) }
            command != null
        }
        onDispose(unregister)
    }
}

private fun perform(
    command: AudioKeyCommand,
    viewModel: AudiobookReaderViewModel,
    onPlayPause: () -> Unit,
    onLeave: () -> Unit,
) {
    when (command) {
        AudioKeyCommand.PLAY_PAUSE -> onPlayPause()
        AudioKeyCommand.SEEK_BACK -> viewModel.onAction(AudioUiAction.SkipBack)
        AudioKeyCommand.SEEK_FORWARD -> viewModel.onAction(AudioUiAction.SkipForward)
        AudioKeyCommand.PREVIOUS_CHAPTER -> viewModel.onAction(AudioUiAction.PreviousChapter)
        AudioKeyCommand.NEXT_CHAPTER -> viewModel.onAction(AudioUiAction.NextChapter)
        AudioKeyCommand.TOGGLE_BOOKMARK -> viewModel.onAction(AudioUiAction.AddBookmark)
        AudioKeyCommand.OPEN_CHAPTERS -> viewModel.onAction(AudioUiAction.Show(AudioOverlay.CHAPTERS))
        AudioKeyCommand.OPEN_SETTINGS -> viewModel.onAction(AudioUiAction.Show(AudioOverlay.SETTINGS))
        AudioKeyCommand.ESCAPE ->
            if (viewModel.uiState.value.hasTransientSurface) {
                viewModel.onAction(AudioUiAction.Show(AudioOverlay.NONE))
            } else {
                onLeave()
            }
    }
}
