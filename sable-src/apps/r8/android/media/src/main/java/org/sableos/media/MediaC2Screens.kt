package org.sableos.media

import android.view.KeyEvent
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableDenseRow
import org.sableos.design.SableRowAction
import org.sableos.design.SableSpacing
import org.sableos.design.SableTile
import org.sableos.design.sableFocusRing
import org.sableos.media.MediaText.t

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
                title = t("up_next"),
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
                    title = t("nothing_else_queued"),
                    detail =
                        t("the_current_track_is_shown"),
                )
            }
        } else {
            items(
                count = upcomingCount,
                key = { offset -> queue[firstUpcomingPosition + offset].index },
            ) { offset ->
                val item = queue[firstUpcomingPosition + offset]
                val listIndex = firstUpcomingPosition + offset
                val canMoveUp = listIndex > firstUpcomingPosition || item.index > playback.currentIndex + 1
                val canMoveDown = item.index < queue.lastIndex
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
                    // Dense row: play stays visible, reordering/removal move to the row menu
                    // (no four-button strip that clips at large font sizes).
                    SableDenseRow(
                        title = item.title,
                        subtitle = item.source,
                        onClick = { onPlayIndex(item.index) },
                        modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Xs),
                        actions =
                            buildList {
                                add(SableRowAction(QUEUE_ACTION_PLAY, t("play"), primary = true))
                                if (canMoveUp) add(SableRowAction(QUEUE_ACTION_UP, t("move_up")))
                                if (canMoveDown) add(SableRowAction(QUEUE_ACTION_DOWN, t("move_down")))
                                add(SableRowAction(QUEUE_ACTION_REMOVE, t("remove_from_up_next")))
                            },
                        onAction = { action ->
                            when (action.id) {
                                QUEUE_ACTION_PLAY -> onPlayIndex(item.index)
                                QUEUE_ACTION_UP -> onMove(item.index, item.index - 1)
                                QUEUE_ACTION_DOWN -> onMove(item.index, item.index + 1)
                                QUEUE_ACTION_REMOVE -> onRemove(item.index)
                            }
                        },
                    )
                }
            }
        }

        item(key = "queue-footer") {
            Text(
                text =
                    t("queue_changes_are_applied_directly"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val QUEUE_ACTION_PLAY = "play"
private const val QUEUE_ACTION_UP = "up"
private const val QUEUE_ACTION_DOWN = "down"
private const val QUEUE_ACTION_REMOVE = "remove"

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
                    text = t("prev"),
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onPrevious,
                )
                SableActionButton(
                    text = if (playback.isPlaying) t("pause") else t("play"),
                    modifier = Modifier.weight(1f),
                    onClick = onPlayPause,
                )
                SableActionButton(
                    text = t("next"),
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onNext,
                )
                SableActionButton(
                    text = t("stop"),
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
                        t("new_playlist")
                    } else {
                        t("edit_playlist")
                    },
                onBack = onCancel,
            )
        }

        item(key = "playlist-name") {
            OutlinedTextField(
                value = playlistName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().mediaTextInput(),
                singleLine = true,
                label = { Text(t("playlist_name")) },
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
                    title = t("no_local_tracks"),
                    detail = t("add_local_audio_to_the"),
                )
            }
        } else {
            items(
                items = localItems,
                key = { item -> item.uri },
            ) { item ->
                val selected = item.uri in selectedUris

                Surface(
                    // The whole row is one keyboard stop with a visible focus ring; Space/Enter toggles it.
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .sableFocusRing()
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { onToggleItem(item.uri) },
                            ),
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
                            onCheckedChange = null,
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
                    text = t("cancel"),
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                SableActionButton(
                    text = t("save_playlist"),
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                )
            }
        }

        item(key = "playlist-footer") {
            Text(
                text =
                    t("playlists_contain_references_to_audio"),
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
            title = t("media_settings"),
            onBack = onBack,
        )

        Text(
            text = t("start_screen"),
            style = MaterialTheme.typography.titleLarge,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            SableTile(
                title = t("collection"),
                subtitle = t("local_library"),
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
                title = t("radio"),
                subtitle = t("saved_stations"),
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
            title = t("show_mini_player"),
            detail = t("show_the_active_item_below"),
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
            title = t("keep_screen_awake_while_playing"),
            detail = t("prevents_display_sleep_only_while"),
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
                t("these_preferences_are_stored_locally"),
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
        modifier =
            Modifier
                .fillMaxWidth()
                .sableFocusRing()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
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
                onCheckedChange = null,
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
            modifier =
                Modifier
                    .width(48.dp)
                    .sableFocusRing(),
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

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/** Dialogs opened from the keyboard: I (info) and D / Backspace (remove, with confirmation). */
@Composable
internal fun MediaKeyDialogs(
    playback: PlaybackUiState,
    showInfo: Boolean,
    pendingRemoval: LocalAudioItem?,
    onDismissInfo: () -> Unit,
    onConfirmRemoval: (LocalAudioItem) -> Unit,
    onDismissRemoval: () -> Unit,
) {
    if (showInfo) {
        AlertDialog(
            onDismissRequest = onDismissInfo,
            title = { Text(t("now_playing")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs)) {
                    mediaInfoLines(playback).forEach { (label, value) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissInfo, modifier = Modifier.sableFocusRing()) {
                    Text(t("close"))
                }
            },
        )
    }

    if (pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = onDismissRemoval,
            title = { Text(t("remove_from_collection")) },
            text = {
                Text(
                    t("leaves_the_sable_media_collection", pendingRemoval.title),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirmRemoval(pendingRemoval) },
                    modifier = Modifier.sableFocusRing(),
                ) {
                    Text(t("remove"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemoval, modifier = Modifier.sableFocusRing()) {
                    Text(t("keep"))
                }
            },
        )
    }
}

internal fun mediaInfoLines(playback: PlaybackUiState): List<Pair<String, String>> =
    buildList {
        add("title" to playback.title)
        add("source" to playback.source)
        val position = formatClock(playback.positionMs)
        add(
            "position" to
                if (playback.durationMs > 0L) {
                    "$position / ${formatClock(playback.durationMs)}"
                } else {
                    t("live", position)
                },
        )
        if (playback.currentIndex >= 0 && playback.queue.isNotEmpty()) {
            add("queue" to t("of", playback.currentIndex + 1, playback.queue.size))
        }
        add("state" to if (playback.isPlaying) "playing" else "paused")
    }

private fun formatClock(valueMs: Long): String {
    val totalSeconds = valueMs.coerceAtLeast(0L) / MILLIS_PER_SECOND
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
}

/** Library search state for the collection screen; [query] is null while search is closed. */
internal data class CollectionSearch(
    val query: String?,
    val onOpen: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit,
    /** Bumped each time `/` or S is pressed, so the field takes focus again. */
    val focusToken: Int = 0,
)

/**
 * LIBRARY_SEARCH_EXPLICIT: opened with `/` or S, filters the collection by title,
 * artist or album as the user types. Enter or Down moves into the results; Esc closes.
 */
@Composable
internal fun MediaSearchBar(
    query: String,
    resultCount: Int,
    pivotLabel: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    focusToken: Int = 0,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun submit() {
        onSubmit()
        focusManager.moveFocus(FocusDirection.Down)
    }

    LaunchedEffect(focusToken) {
        focusRequester.requestFocus()
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SableSpacing.ScreenHorizontal, vertical = SableSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .mediaTextInput()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_ESCAPE -> {
                                onClose()
                                true
                            }

                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            -> {
                                submit()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
            singleLine = true,
            label = { Text(t("search_title_artist_or_album")) },
            supportingText = {
                Text(
                    text = t("in_enter_for_results_esc", resultCount, pivotLabel),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        TextButton(
            onClick = onClose,
            modifier = Modifier.sableFocusRing(),
        ) {
            Text(t("close"))
        }
    }
}
