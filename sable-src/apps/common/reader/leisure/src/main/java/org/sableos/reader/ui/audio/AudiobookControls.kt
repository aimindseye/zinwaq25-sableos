package org.sableos.reader.ui.audio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.sableos.reader.engine.audio.AudioTimeFormat

private val FOCUS_BORDER_WIDTH = 2.dp
private val FOCUS_CORNER = 8.dp
private val BIG_BUTTON = 72.dp

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
private fun TransportButton(icon: ImageVector, description: String, onClick: () -> Unit, big: Boolean = false) {
    val sizing = if (big) Modifier.size(BIG_BUTTON) else Modifier
    IconButton(onClick = onClick, modifier = Modifier.visibleFocus().then(sizing)) {
        Icon(icon, contentDescription = description, modifier = if (big) Modifier.size(BIG_BUTTON / 2) else Modifier)
    }
}

@Composable
internal fun AudioTransport(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit, togglePlayback: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            Icons.Filled.SkipPrevious,
            "Previous chapter (Page Up)",
            { onAction(AudioUiAction.PreviousChapter) },
        )
        TransportButton(
            Icons.Filled.FastRewind,
            "Back ${AudioTimeFormat.interval(state.settings.skipBackMs)} (Left)",
            { onAction(AudioUiAction.SkipBack) },
        )
        TransportButton(
            icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (state.isPlaying) "Pause (Space)" else "Play (Space)",
            onClick = togglePlayback,
            big = true,
        )
        TransportButton(
            Icons.Filled.FastForward,
            "Forward ${AudioTimeFormat.interval(state.settings.skipForwardMs)} (Right)",
            { onAction(AudioUiAction.SkipForward) },
        )
        TransportButton(Icons.Filled.SkipNext, "Next chapter (Page Down)", { onAction(AudioUiAction.NextChapter) })
    }
}

@Composable
internal fun AudioStatusRow(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${state.settings.speed}×", style = MaterialTheme.typography.labelLarge)
        Text(sleepText(state.sleep), style = MaterialTheme.typography.labelLarge)
        Text(
            text = if (state.isBuffering) {
                "Loading…"
            } else {
                "Chapter ${state.chapterIndex + 1} of ${state.chapters.size}"
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
    if (state.sleep.kind != SleepKind.OFF) {
        TextButton(onClick = { onAction(AudioUiAction.SetSleep(null)) }) {
            Text("Cancel sleep timer")
        }
    }
}

private fun sleepText(sleep: SleepUi): String = when (sleep.kind) {
    SleepKind.OFF -> "Sleep timer off"
    SleepKind.END_OF_CHAPTER -> "Sleeping at end of chapter"
    SleepKind.AFTER -> "Sleeping in ${AudioTimeFormat.clock(sleep.remainingMs ?: 0L)}"
}
