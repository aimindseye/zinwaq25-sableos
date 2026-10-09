package org.sableos.reader.ui.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sableos.reader.capability.ReaderCapabilities
import org.sableos.reader.engine.audio.AudioPlaybackSettings
import org.sableos.reader.engine.audio.AudioTimeFormat
import org.sableos.reader.engine.audio.SleepMode
import org.sableos.reader.input.KeyContext
import org.sableos.reader.input.KeyHints
import org.sableos.reader.input.ReaderSurface

private val OVERLAY_SPACING = 12.dp
private val OVERLAY_LIST_MAX_HEIGHT = 280.dp
private const val MS_PER_MINUTE = 60_000L
private val SLEEP_MINUTES = listOf(5, 10, 15, 30, 45, 60)

/** All audiobook overlays are dialogs: they own focus, close with Escape/Back and never leave the player half-open. */
@Composable
internal fun AudiobookOverlays(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit) {
    val dismiss = { onAction(AudioUiAction.Show(AudioOverlay.NONE)) }
    when (state.overlay) {
        AudioOverlay.NONE -> Unit
        AudioOverlay.CHAPTERS -> ChaptersDialog(state, onAction, dismiss)
        AudioOverlay.BOOKMARKS -> BookmarksDialog(state, onAction, dismiss)
        AudioOverlay.SETTINGS -> SettingsDialog(state, onAction, dismiss)
        AudioOverlay.HINTS -> HintsDialog(dismiss)
    }
}

@Composable
private fun ChaptersDialog(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Chapters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OVERLAY_SPACING)) {
                LazyColumn(Modifier.heightIn(max = OVERLAY_LIST_MAX_HEIGHT)) {
                    itemsIndexed(state.chapters) { index, chapter ->
                        Text(
                            text = "${index + 1}. ${chapter.title} · ${AudioTimeFormat.clock(chapter.startMs)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == state.chapterIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    onAction(AudioUiAction.GoToChapter(index))
                                    dismiss()
                                }
                                .padding(vertical = OVERLAY_SPACING / 2),
                        )
                    }
                }
                TextButton(onClick = { onAction(AudioUiAction.Show(AudioOverlay.BOOKMARKS)) }) {
                    Text("Bookmarks (${state.bookmarks.size})")
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
private fun BookmarksDialog(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Bookmarks") },
        text = {
            if (state.bookmarks.isEmpty()) {
                Text("No bookmarks yet. Press B to bookmark this position.")
            } else {
                LazyColumn(Modifier.heightIn(max = OVERLAY_LIST_MAX_HEIGHT)) {
                    items(state.bookmarks, key = AudioBookmarkUi::id) { bookmark ->
                        BookmarkRow(bookmark, onAction, dismiss)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
private fun BookmarkRow(bookmark: AudioBookmarkUi, onAction: (AudioUiAction) -> Unit, dismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                onAction(AudioUiAction.GoToBookmark(bookmark.id))
                dismiss()
            }
            .padding(vertical = OVERLAY_SPACING / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(bookmark.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onAction(AudioUiAction.DeleteBookmark(bookmark.id)) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark ${bookmark.label}")
        }
    }
}

@Composable
private fun SettingsDialog(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Playback") },
        text = {
            Column(Modifier.heightIn(max = OVERLAY_LIST_MAX_HEIGHT * 2).verticalScroll(rememberScrollState())) {
                Section("Speed")
                AudioPlaybackSettings.SPEEDS.forEach { speed ->
                    ChoiceRow("$speed×", state.settings.speed == speed) { onAction(AudioUiAction.SetSpeed(speed)) }
                }
                Section("Skip back")
                AudioPlaybackSettings.SKIP_BACK_CHOICES_MS.forEach { ms ->
                    ChoiceRow(AudioTimeFormat.interval(ms), state.settings.skipBackMs == ms) {
                        onAction(AudioUiAction.SetSkipBack(ms))
                    }
                }
                Section("Skip forward")
                AudioPlaybackSettings.SKIP_FORWARD_CHOICES_MS.forEach { ms ->
                    ChoiceRow(AudioTimeFormat.interval(ms), state.settings.skipForwardMs == ms) {
                        onAction(AudioUiAction.SetSkipForward(ms))
                    }
                }
                SleepSection(state, onAction)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
private fun SleepSection(state: AudiobookUiState, onAction: (AudioUiAction) -> Unit) {
    Section("Sleep timer")
    ChoiceRow("Off", state.sleep.kind == SleepKind.OFF) { onAction(AudioUiAction.SetSleep(null)) }
    SLEEP_MINUTES.forEach { minutes ->
        ChoiceRow("$minutes min", false) { onAction(AudioUiAction.SetSleep(SleepMode.After(minutes * MS_PER_MINUTE))) }
    }
    ChoiceRow("End of chapter", state.sleep.kind == SleepKind.END_OF_CHAPTER) {
        onAction(AudioUiAction.SetSleep(SleepMode.EndOfChapter))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = OVERLAY_SPACING))
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HintsDialog(dismiss: () -> Unit) {
    val hints = KeyHints.forContext(KeyContext(ReaderSurface.AUDIO, ReaderCapabilities.AUDIOBOOK))
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
