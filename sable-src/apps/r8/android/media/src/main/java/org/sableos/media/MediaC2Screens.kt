package org.sableos.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableSpacing
import org.sableos.design.SableTile

@Composable
internal fun QueueScreen(
    playback: PlaybackUiState,
    onPlayIndex: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val queue = playback.queue
    val firstUpcomingPosition =
        if (playback.currentIndex >= 0) {
            (playback.currentIndex + 1).coerceIn(0, queue.size)
        } else {
            0
        }
    val upcomingCount = queue.size - firstUpcomingPosition

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        contentPadding =
            PaddingValues(
                horizontal = SableSpacing.ScreenHorizontal,
                vertical = SableSpacing.ScreenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        item(key = "queue-back") {
            C2BackTitle(
                title = "up next",
                onBack = onBack,
            )
        }

        if (queue.isNotEmpty()) {
            item(key = "queue-controls") {
                QueueTransportControls(
                    playback = playback,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onStop = onStop,
                )
            }
        }

        if (upcomingCount <= 0) {
            item(key = "queue-empty") {
                C2InfoPanel(
                    title = "Nothing else queued",
                    detail =
                        "The current track is shown above. Add or play more local audio to build Up Next.",
                )
            }
        } else {
            items(
                count = upcomingCount,
                key = { offset -> queue[firstUpcomingPosition + offset].index },
            ) { offset ->
                val item = queue[firstUpcomingPosition + offset]
                val listIndex = firstUpcomingPosition + offset
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(SableSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(SableSpacing.Md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "up next",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.source,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            SableActionButton(
                                text = "Play",
                                onClick = {
                                    onPlayIndex(item.index)
                                },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
                        ) {
                            SableActionButton(
                                text = "Move up",
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (
                                        listIndex > firstUpcomingPosition ||
                                        item.index > playback.currentIndex + 1
                                    ) {
                                        onMove(item.index, item.index - 1)
                                    }
                                },
                            )
                            SableActionButton(
                                text = "Move down",
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (item.index < queue.lastIndex) {
                                        onMove(item.index, item.index + 1)
                                    }
                                },
                            )
                            SableActionButton(
                                text = "Remove",
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onRemove(item.index)
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "queue-footer") {
            Text(
                text =
                    "Queue changes are applied directly to the active Media3 session. " +
                        "Sable does not persist listening history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueTransportControls(
    playback: PlaybackUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Md),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                Text(
                    text = playback.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playback.source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
            ) {
                SableActionButton(
                    text = "Prev",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onPrevious,
                )
                SableActionButton(
                    text = if (playback.isPlaying) "Pause" else "Play",
                    modifier = Modifier.weight(1f),
                    onClick = onPlayPause,
                )
                SableActionButton(
                    text = "Next",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onNext,
                )
                SableActionButton(
                    text = "Stop",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                )
            }
        }
    }
}

@Composable
internal fun PlaylistEditorScreen(
    editingPlaylist: MediaPlaylist?,
    playlistName: String,
    selectedUris: Set<String>,
    localItems: List<LocalAudioItem>,
    onNameChange: (String) -> Unit,
    onToggleItem: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        contentPadding =
            PaddingValues(
                horizontal = SableSpacing.ScreenHorizontal,
                vertical = SableSpacing.ScreenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        item(key = "playlist-back") {
            C2BackTitle(
                title =
                    if (editingPlaylist == null) {
                        "new playlist"
                    } else {
                        "edit playlist"
                    },
                onBack = onCancel,
            )
        }

        item(key = "playlist-name") {
            OutlinedTextField(
                value = playlistName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Playlist name") },
            )
        }

        item(key = "playlist-tracks-title") {
            Text(
                text = "tracks",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (localItems.isEmpty()) {
            item(key = "playlist-empty") {
                C2InfoPanel(
                    title = "No local tracks",
                    detail = "Add local audio to the Collection before creating a playlist.",
                )
            }
        } else {
            items(
                items = localItems,
                key = { item -> item.uri },
            ) { item ->
                val selected = item.uri in selectedUris

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    border =
                        BorderStroke(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(SableSpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                onToggleItem(item.uri)
                            },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        item(key = "playlist-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
            ) {
                SableActionButton(
                    text = "Cancel",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                SableActionButton(
                    text = "Save playlist",
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                )
            }
        }

        item(key = "playlist-footer") {
            Text(
                text =
                    "Playlists contain references to audio files you explicitly added to Sable Media. " +
                        "They stay local on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsScreen(
    settings: MediaSettings,
    onSettingsChange: (MediaSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        C2BackTitle(
            title = "media settings",
            onBack = onBack,
        )

        Text(
            text = "start screen",
            style = MaterialTheme.typography.titleLarge,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            SableTile(
                title = "Collection",
                subtitle = "local library",
                active = settings.startPage == MediaStartPage.Collection,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSettingsChange(
                        settings.copy(
                            startPage = MediaStartPage.Collection,
                        ),
                    )
                },
            )
            SableTile(
                title = "Radio",
                subtitle = "saved stations",
                active = settings.startPage == MediaStartPage.Radio,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSettingsChange(
                        settings.copy(
                            startPage = MediaStartPage.Radio,
                        ),
                    )
                },
            )
        }

        SettingsToggleRow(
            title = "Show mini player",
            detail = "Show the active item below Collection and Radio.",
            checked = settings.showMiniPlayer,
            onCheckedChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        showMiniPlayer = enabled,
                    ),
                )
            },
        )

        SettingsToggleRow(
            title = "Keep screen awake while playing",
            detail = "Prevents display sleep only while Sable Media is actively playing.",
            checked = settings.keepScreenAwakeWhilePlaying,
            onCheckedChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        keepScreenAwakeWhilePlaying = enabled,
                    ),
                )
            },
        )

        C2InfoPanel(
            title = "privacy",
            detail =
                "These preferences are stored locally. Sable Media does not create a cloud profile " +
                    "or send playback preferences to a recommendation service.",
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(SableSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
internal fun C2BackTitle(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.width(48.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Box(
                modifier = Modifier.aspectRatio(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
internal fun C2InfoPanel(
    title: String,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
