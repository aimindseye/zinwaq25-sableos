package org.sableos.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.design.SableActionButton
import org.sableos.design.SableAdaptiveTopNav
import org.sableos.design.SableAlphabetIndex
import org.sableos.design.SableAlphabetRail
import org.sableos.design.SableAlphabetRailWidth
import org.sableos.design.SableDenseRow
import org.sableos.design.SableDestination
import org.sableos.design.SableHeroHeader
import org.sableos.design.SableRefreshableSurface
import org.sableos.design.SableResponsive
import org.sableos.design.SableRowAction
import org.sableos.design.SableSpacing
import org.sableos.design.sableLetterJump
import java.util.Locale

internal val MediaPink = Color(0xFFFF4F9A)
internal val MediaPurple = Color(0xFF7A42E8)
internal val MediaBlue = Color(0xFF4D9CFF)
internal val MediaGreen = Color(0xFF35C66B)
internal val MediaOrange = Color(0xFFF28C45)

private val MediaControlSize = 48.dp
private val MediaPrimaryTouchHeight = 54.dp
private val MediaCompactPadding = 12.dp
private const val COLLECTION_FIXED_ITEM_COUNT = 6
private const val ROW_ACTION_PLAY = "play"
private const val ROW_ACTION_FAVORITE = "favorite"
private const val ROW_ACTION_EDIT = "edit"
private const val ROW_ACTION_REMOVE = "remove"
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val LARGE_ARTWORK_ASPECT_RATIO = 1.9f

internal enum class MediaDestination(
    val label: String,
) {
    Collection("music"),
    Podcasts("podcasts"),
    NowPlaying("now playing"),
    Radio("radio"),
    ManageStations("manage"),
    AddStation("add"),
    Queue("queue"),
    PlaylistEditor("playlist"),
    Settings("settings"),
}

internal enum class LibraryPivot(
    val label: String,
) {
    Songs("songs"),
    Artists("artists"),
    Albums("albums"),
    Favorites("favorites"),
    Playlists("playlists"),
}

internal data class MediaScreenState(
    val destination: MediaDestination,
    val libraryPivot: LibraryPivot,
    val localItems: List<LocalAudioItem>,
    val localLibraryLoading: Boolean,
    val podcastSubscriptions: List<PodcastSubscription>,
    val savedPodcastEpisodeIds: Set<String>,
    val playedPodcastEpisodeIds: Set<String>,
    val podcastFeedUrl: String,
    val podcastBusy: Boolean,
    val podcastRefreshing: Boolean,
    val podcastMessage: String?,
    val stations: List<RadioStation>,
    val playback: PlaybackUiState,
    val favoriteAudioUris: Set<String>,
    val favoriteStationIds: Set<String>,
    val playlists: List<MediaPlaylist>,
    val settings: MediaSettings,
    val playlistName: String,
    val selectedPlaylistUris: Set<String>,
    val editingPlaylist: MediaPlaylist?,
    val showFavoriteStationsOnly: Boolean,
    val stationName: String,
    val stationUrl: String,
    val editingStation: RadioStation?,
    val message: String?,
)

internal data class MediaScreenActions(
    val onDestinationChange: (MediaDestination) -> Unit,
    val onLibraryPivotChange: (LibraryPivot) -> Unit,
    val onAddLocalAudio: () -> Unit,
    val onAddLocalFolder: () -> Unit,
    val onPlayLocal: (Int) -> Unit,
    val onRemoveLocal: (LocalAudioItem) -> Unit,
    val onToggleFavoriteAudio: (LocalAudioItem) -> Unit,
    val onPodcastFeedUrlChange: (String) -> Unit,
    val onAddPodcastFeed: () -> Unit,
    val onRefreshPodcasts: () -> Unit,
    val onRemovePodcastSubscription: (PodcastSubscription) -> Unit,
    val onPlayPodcastEpisode: (PodcastEpisode) -> Unit,
    val onQueuePodcastEpisode: (PodcastEpisode) -> Unit,
    val onToggleSavedPodcastEpisode: (PodcastEpisode) -> Unit,
    val onPlayStation: (RadioStation) -> Unit,
    val onToggleFavoriteStation: (RadioStation) -> Unit,
    val onRadioFavoritesOnlyChange: (Boolean) -> Unit,
    val onAddStation: () -> Unit,
    val onManageStations: () -> Unit,
    val onEditStation: (RadioStation) -> Unit,
    val onDeleteStation: (RadioStation) -> Unit,
    val onStationNameChange: (String) -> Unit,
    val onStationUrlChange: (String) -> Unit,
    val onSaveNewStation: () -> Unit,
    val onSaveEditedStation: () -> Unit,
    val onCancelStationFlow: () -> Unit,
    val onCreatePlaylist: () -> Unit,
    val onEditPlaylist: (MediaPlaylist) -> Unit,
    val onDeletePlaylist: (MediaPlaylist) -> Unit,
    val onPlayPlaylist: (MediaPlaylist) -> Unit,
    val onPlaylistNameChange: (String) -> Unit,
    val onTogglePlaylistItem: (String) -> Unit,
    val onSavePlaylist: () -> Unit,
    val onCancelPlaylistEditor: () -> Unit,
    val onSettingsChange: (MediaSettings) -> Unit,
    val onPlayQueueIndex: (Int) -> Unit,
    val onMoveQueueItem: (Int, Int) -> Unit,
    val onRemoveQueueItem: (Int) -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onStop: () -> Unit,
    val onSeek: (Long) -> Unit,
)

@Composable
internal fun MediaScreen(
    state: MediaScreenState,
    actions: MediaScreenActions,
) {
    val destination = state.destination
    val libraryPivot = state.libraryPivot
    val localItems = state.localItems
    val localLibraryLoading = state.localLibraryLoading
    val podcastSubscriptions = state.podcastSubscriptions
    val savedPodcastEpisodeIds = state.savedPodcastEpisodeIds
    val playedPodcastEpisodeIds = state.playedPodcastEpisodeIds
    val podcastFeedUrl = state.podcastFeedUrl
    val podcastBusy = state.podcastBusy
    val podcastRefreshing = state.podcastRefreshing
    val podcastMessage = state.podcastMessage
    val stations = state.stations
    val playback = state.playback
    val favoriteAudioUris = state.favoriteAudioUris
    val favoriteStationIds = state.favoriteStationIds
    val playlists = state.playlists
    val settings = state.settings
    val playlistName = state.playlistName
    val selectedPlaylistUris = state.selectedPlaylistUris
    val editingPlaylist = state.editingPlaylist
    val showFavoriteStationsOnly = state.showFavoriteStationsOnly
    val stationName = state.stationName
    val stationUrl = state.stationUrl
    val editingStation = state.editingStation
    val message = state.message
    val onDestinationChange = actions.onDestinationChange
    val onLibraryPivotChange = actions.onLibraryPivotChange
    val onAddLocalAudio = actions.onAddLocalAudio
    val onAddLocalFolder = actions.onAddLocalFolder
    val onPlayLocal = actions.onPlayLocal
    val onToggleFavoriteAudio = actions.onToggleFavoriteAudio
    val onPodcastFeedUrlChange = actions.onPodcastFeedUrlChange
    val onAddPodcastFeed = actions.onAddPodcastFeed
    val onRefreshPodcasts = actions.onRefreshPodcasts
    val onRemovePodcastSubscription = actions.onRemovePodcastSubscription
    val onPlayPodcastEpisode = actions.onPlayPodcastEpisode
    val onQueuePodcastEpisode = actions.onQueuePodcastEpisode
    val onToggleSavedPodcastEpisode = actions.onToggleSavedPodcastEpisode
    val onPlayStation = actions.onPlayStation
    val onToggleFavoriteStation = actions.onToggleFavoriteStation
    val onRadioFavoritesOnlyChange = actions.onRadioFavoritesOnlyChange
    val onAddStation = actions.onAddStation
    val onManageStations = actions.onManageStations
    val onEditStation = actions.onEditStation
    val onDeleteStation = actions.onDeleteStation
    val onStationNameChange = actions.onStationNameChange
    val onStationUrlChange = actions.onStationUrlChange
    val onSaveNewStation = actions.onSaveNewStation
    val onSaveEditedStation = actions.onSaveEditedStation
    val onCancelStationFlow = actions.onCancelStationFlow
    val onCreatePlaylist = actions.onCreatePlaylist
    val onEditPlaylist = actions.onEditPlaylist
    val onDeletePlaylist = actions.onDeletePlaylist
    val onPlayPlaylist = actions.onPlayPlaylist
    val onPlaylistNameChange = actions.onPlaylistNameChange
    val onTogglePlaylistItem = actions.onTogglePlaylistItem
    val onSavePlaylist = actions.onSavePlaylist
    val onCancelPlaylistEditor = actions.onCancelPlaylistEditor
    val onSettingsChange = actions.onSettingsChange
    val onPlayQueueIndex = actions.onPlayQueueIndex
    val onMoveQueueItem = actions.onMoveQueueItem
    val onRemoveQueueItem = actions.onRemoveQueueItem
    val onPlayPause = actions.onPlayPause
    val onPrevious = actions.onPrevious
    val onNext = actions.onNext
    val onStop = actions.onStop
    val onSeek = actions.onSeek

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (destination) {
            MediaDestination.Collection -> {
                CollectionScreen(
                    pivot = libraryPivot,
                    localItems = localItems,
                    localLibraryLoading = localLibraryLoading,
                    playback = playback,
                    favoriteAudioUris = favoriteAudioUris,
                    playlists = playlists,
                    showMiniPlayer = settings.showMiniPlayer,
                    onDestinationChange = onDestinationChange,
                    onPivotChange = onLibraryPivotChange,
                    onAddLocalAudio = onAddLocalAudio,
                    onAddLocalFolder = onAddLocalFolder,
                    onPlay = onPlayLocal,
                    onToggleFavorite = onToggleFavoriteAudio,
                    onCreatePlaylist = onCreatePlaylist,
                    onEditPlaylist = onEditPlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onPlayPlaylist = onPlayPlaylist,
                    onOpenNowPlaying = {
                        onDestinationChange(MediaDestination.NowPlaying)
                    },
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }

            MediaDestination.Queue -> {
                QueueScreen(
                    playback = playback,
                    onPlayIndex = onPlayQueueIndex,
                    onMove = onMoveQueueItem,
                    onRemove = onRemoveQueueItem,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onStop = onStop,
                    onBack = {
                        onDestinationChange(MediaDestination.NowPlaying)
                    },
                )
            }

            MediaDestination.PlaylistEditor -> {
                PlaylistEditorScreen(
                    editingPlaylist = editingPlaylist,
                    playlistName = playlistName,
                    selectedUris = selectedPlaylistUris,
                    localItems = localItems,
                    onNameChange = onPlaylistNameChange,
                    onToggleItem = onTogglePlaylistItem,
                    onSave = onSavePlaylist,
                    onCancel = onCancelPlaylistEditor,
                )
            }

            MediaDestination.Podcasts,
            MediaDestination.NowPlaying,
            MediaDestination.Radio,
            MediaDestination.ManageStations,
            MediaDestination.AddStation,
            MediaDestination.Settings,
            -> {
                val scrollableContent: @Composable () -> Unit = {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .imePadding(),
                    ) {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentPadding =
                                PaddingValues(
                                    horizontal = SableSpacing.ScreenHorizontal,
                                    vertical = SableSpacing.ScreenVertical,
                                ),
                            verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
                        ) {
                            item {
                                SableHeroHeader(
                                    eyebrow = "Sable Media",
                                    title =
                                        when (destination) {
                                            MediaDestination.Podcasts -> "podcasts"

                                            MediaDestination.Radio,
                                            MediaDestination.ManageStations,
                                            MediaDestination.AddStation,
                                            -> "radio"

                                            MediaDestination.NowPlaying,
                                            MediaDestination.Queue,
                                            -> "playing"

                                            MediaDestination.Settings -> "settings"

                                            MediaDestination.Collection,
                                            MediaDestination.PlaylistEditor,
                                            -> "media"
                                        },
                                    subtitle = "Music, podcasts, and radio in one private playback flow.",
                                )
                            }

                            item {
                                PrimaryPivot(
                                    destination = destination,
                                    onSelect = onDestinationChange,
                                )
                            }

                            if (
                                destination == MediaDestination.NowPlaying ||
                                destination == MediaDestination.Podcasts ||
                                destination == MediaDestination.Radio
                            ) {
                                item {
                                    MediaUtilityBar(
                                        queueCount = playback.queue.size,
                                        upNextCount = playback.upNextCount(),
                                        onQueue = {
                                            onDestinationChange(MediaDestination.Queue)
                                        },
                                        onSettings = {
                                            onDestinationChange(MediaDestination.Settings)
                                        },
                                    )
                                }
                            }

                            item {
                                when (destination) {
                                    MediaDestination.Collection,
                                    MediaDestination.Queue,
                                    MediaDestination.PlaylistEditor,
                                    -> {
                                        Unit
                                    }

                                    MediaDestination.Podcasts -> {
                                        PodcastScreen(
                                            state =
                                                PodcastScreenState(
                                                    subscriptions = podcastSubscriptions,
                                                    savedEpisodeIds = savedPodcastEpisodeIds,
                                                    playedEpisodeIds = playedPodcastEpisodeIds,
                                                    feedUrl = podcastFeedUrl,
                                                    busy = podcastBusy,
                                                    message = podcastMessage,
                                                    playback = playback,
                                                    // Pinned by this screen instead of inside the list.
                                                    showMiniPlayer = false,
                                                ),
                                            actions =
                                                PodcastScreenActions(
                                                    onFeedUrlChange = onPodcastFeedUrlChange,
                                                    onAddFeed = onAddPodcastFeed,
                                                    onRemoveSubscription = onRemovePodcastSubscription,
                                                    onPlayEpisode = onPlayPodcastEpisode,
                                                    onQueueEpisode = onQueuePodcastEpisode,
                                                    onToggleSavedEpisode = onToggleSavedPodcastEpisode,
                                                    onOpenNowPlaying = {
                                                        onDestinationChange(MediaDestination.NowPlaying)
                                                    },
                                                ),
                                        )
                                    }

                                    MediaDestination.NowPlaying -> {
                                        NowPlayingScreen(
                                            playback = playback,
                                            onPlayPause = onPlayPause,
                                            onPrevious = onPrevious,
                                            onNext = onNext,
                                            onStop = onStop,
                                            onSeek = onSeek,
                                        )
                                    }

                                    MediaDestination.Radio -> {
                                        RadioScreen(
                                            stations = stations,
                                            favoriteStationIds = favoriteStationIds,
                                            showFavoritesOnly = showFavoriteStationsOnly,
                                            message = message,
                                            onPlay = onPlayStation,
                                            onToggleFavorite = onToggleFavoriteStation,
                                            onFavoritesOnlyChange = onRadioFavoritesOnlyChange,
                                            onManage = onManageStations,
                                            onAdd = onAddStation,
                                        )
                                    }

                                    MediaDestination.ManageStations -> {
                                        ManageStationsScreen(
                                            stations = stations,
                                            editingStation = editingStation,
                                            stationName = stationName,
                                            stationUrl = stationUrl,
                                            message = message,
                                            onEdit = onEditStation,
                                            onDelete = onDeleteStation,
                                            onStationNameChange = onStationNameChange,
                                            onStationUrlChange = onStationUrlChange,
                                            onSave = onSaveEditedStation,
                                            onCancelEdit = onCancelStationFlow,
                                            onBack = {
                                                onDestinationChange(MediaDestination.Radio)
                                            },
                                        )
                                    }

                                    MediaDestination.AddStation -> {
                                        AddStationScreen(
                                            stationName = stationName,
                                            stationUrl = stationUrl,
                                            message = message,
                                            onStationNameChange = onStationNameChange,
                                            onStationUrlChange = onStationUrlChange,
                                            onSave = onSaveNewStation,
                                            onCancel = onCancelStationFlow,
                                        )
                                    }

                                    MediaDestination.Settings -> {
                                        SettingsScreen(
                                            settings = settings,
                                            onSettingsChange = onSettingsChange,
                                            onBack = {
                                                onDestinationChange(MediaDestination.Collection)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (
                            destination == MediaDestination.Podcasts ||
                            destination == MediaDestination.Radio
                        ) {
                            // Pinned below the list: current state visible on open.
                            MediaPinnedMiniPlayer(
                                playback = playback,
                                enabled = settings.showMiniPlayer,
                                onPlayPause = onPlayPause,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                onOpenNowPlaying = { onDestinationChange(MediaDestination.NowPlaying) },
                                onOpenQueue = { onDestinationChange(MediaDestination.Queue) },
                            )
                        }
                    }
                }

                if (destination == MediaDestination.Podcasts) {
                    SableRefreshableSurface(
                        isRefreshing = podcastRefreshing,
                        onRefresh = {
                            if (!podcastBusy && podcastSubscriptions.isNotEmpty()) {
                                onRefreshPodcasts()
                            }
                        },
                    ) {
                        scrollableContent()
                    }
                } else {
                    scrollableContent()
                }
            }
        }
    }
}

private fun PlaybackUiState.upNextCount(): Int =
    if (currentIndex < 0) {
        queue.size
    } else {
        (queue.size - currentIndex - 1).coerceAtLeast(0)
    }

@Composable
private fun PrimaryPivot(
    destination: MediaDestination,
    onSelect: (MediaDestination) -> Unit,
) {
    val current =
        when (destination) {
            MediaDestination.ManageStations,
            MediaDestination.AddStation,
            -> MediaDestination.Radio

            MediaDestination.Queue -> MediaDestination.NowPlaying

            MediaDestination.PlaylistEditor -> MediaDestination.Collection

            MediaDestination.Settings -> null

            else -> destination
        }

    // Four top destinations never fit a compact/square row without clipping:
    // the adaptive nav shows what fits plus a keyboard-reachable "more" menu.
    SableAdaptiveTopNav(
        destinations = MEDIA_TOP_DESTINATIONS,
        selectedId = current?.name,
        onSelect = { id -> onSelect(MediaDestination.valueOf(id)) },
        accent = MediaPink,
    )
}

private val MEDIA_TOP_DESTINATIONS =
    listOf(
        MediaDestination.Collection,
        MediaDestination.Podcasts,
        MediaDestination.Radio,
        MediaDestination.NowPlaying,
    ).map { SableDestination(it.name, it.label) }

private val LIBRARY_DESTINATIONS =
    LibraryPivot.entries.map { SableDestination(it.name, it.label) }

@Composable
private fun MediaUtilityBar(
    queueCount: Int,
    upNextCount: Int,
    onQueue: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (queueCount > 1) {
            Surface(
                onClick = onQueue,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = "up next · $upNextCount",
                    modifier = Modifier.padding(MediaCompactPadding),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Surface(
            onClick = onSettings,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = "settings",
                modifier = Modifier.padding(MediaCompactPadding),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun CollectionScreen(
    pivot: LibraryPivot,
    localItems: List<LocalAudioItem>,
    localLibraryLoading: Boolean,
    playback: PlaybackUiState,
    favoriteAudioUris: Set<String>,
    playlists: List<MediaPlaylist>,
    showMiniPlayer: Boolean,
    onDestinationChange: (MediaDestination) -> Unit,
    onPivotChange: (LibraryPivot) -> Unit,
    onAddLocalAudio: () -> Unit,
    onAddLocalFolder: () -> Unit,
    onPlay: (Int) -> Unit,
    onToggleFavorite: (LocalAudioItem) -> Unit,
    onCreatePlaylist: () -> Unit,
    onEditPlaylist: (MediaPlaylist) -> Unit,
    onDeletePlaylist: (MediaPlaylist) -> Unit,
    onPlayPlaylist: (MediaPlaylist) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val collectionIndex by
        produceState<MediaCollectionIndex?>(
            initialValue = null,
            key1 = localItems,
            key2 = favoriteAudioUris,
        ) {
            value = null
            value =
                if (localItems.isEmpty()) {
                    MediaCollectionIndex.Empty
                } else {
                    withContext(Dispatchers.Default) {
                        buildMediaCollectionIndex(
                            localItems = localItems,
                            favoriteAudioUris = favoriteAudioUris,
                        )
                    }
                }
        }

    val songs = collectionIndex?.songs.orEmpty()
    val artistGroups = collectionIndex?.artists.orEmpty()
    val albumGroups = collectionIndex?.albums.orEmpty()
    val favorites = collectionIndex?.favorites.orEmpty()
    val alphabet =
        collectionIndex
            ?.sections
            ?.get(pivot)
            ?: EMPTY_ALPHABET
    val availableUris = collectionIndex?.availableUris.orEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    fun jumpTo(itemIndex: Int) {
        scope.launch {
            listState.animateScrollToItem(COLLECTION_FIXED_ITEM_COUNT + itemIndex)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Keyboard A-Z jump is the primary index on keyboard devices.
                        .sableLetterJump(alphabet, enabled = !alphabet.isEmpty, onJump = ::jumpTo)
                        .padding(
                            end =
                                if (alphabet.isEmpty) {
                                    0.dp
                                } else {
                                    SableAlphabetRailWidth
                                },
                        ),
                state = listState,
                contentPadding =
                    PaddingValues(
                        horizontal = SableSpacing.ScreenHorizontal,
                        vertical = SableSpacing.ScreenVertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
            ) {
                item {
                    SableHeroHeader(
                        eyebrow = "Sable Media",
                        title = "music",
                        subtitle = "Your local collection, playlists, and one continuous playback queue.",
                    )
                }

                item {
                    PrimaryPivot(
                        destination = MediaDestination.Collection,
                        onSelect = onDestinationChange,
                    )
                }

                item {
                    MediaUtilityBar(
                        queueCount = playback.queue.size,
                        upNextCount = playback.upNextCount(),
                        onQueue = {
                            onDestinationChange(MediaDestination.Queue)
                        },
                        onSettings = {
                            onDestinationChange(MediaDestination.Settings)
                        },
                    )
                }

                item {
                    Text(
                        text = "collection",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }

                item {
                    SableAdaptiveTopNav(
                        destinations = LIBRARY_DESTINATIONS,
                        selectedId = pivot.name,
                        onSelect = { id -> onPivotChange(LibraryPivot.valueOf(id)) },
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
                    ) {
                        SableActionButton(
                            text = "Add audio files",
                            modifier = Modifier.weight(1f),
                            onClick = onAddLocalAudio,
                        )
                        SableActionButton(
                            text = "Import folder",
                            primary = false,
                            modifier = Modifier.weight(1f),
                            onClick = onAddLocalFolder,
                        )
                    }
                }

                if (localLibraryLoading) {
                    item {
                        C2InfoPanel(
                            title = "Loading local collection",
                            detail = "Indexing saved songs, artists, and albums off the UI thread.",
                        )
                    }
                } else if (localItems.isEmpty()) {
                    item {
                        EmptyPanel(
                            title = "No local audio yet",
                            detail =
                                "Choose audio with Android's document picker. " +
                                    "Sable keeps access only to files you explicitly select.",
                        )
                    }
                } else if (collectionIndex == null) {
                    item {
                        C2InfoPanel(
                            title = "Indexing collection",
                            detail = "Preparing songs, artists, albums, favorites, and A–Z navigation.",
                        )
                    }
                } else {
                    when (pivot) {
                        LibraryPivot.Songs -> {
                            items(
                                count = songs.size,
                                key = { index -> songs[index].item.uri },
                            ) { index ->
                                val indexedTrack = songs[index]
                                val item = indexedTrack.item
                                LocalTrackRow(
                                    item = item,
                                    accent = accentFor(indexedTrack.sourceIndex),
                                    favorite = item.uri in favoriteAudioUris,
                                    onToggleFavorite = { onToggleFavorite(item) },
                                    onPlay = { onPlay(indexedTrack.sourceIndex) },
                                )
                            }
                        }

                        LibraryPivot.Artists -> {
                            items(
                                count = artistGroups.size,
                                key = { index -> artistGroups[index].name },
                            ) { index ->
                                val group = artistGroups[index]
                                LibraryGroupRow(
                                    title = group.name,
                                    detail = group.detail,
                                    accent = accentFor(group.name.hashCode()),
                                    onClick = {
                                        onPlay(group.sourceIndex)
                                    },
                                )
                            }
                        }

                        LibraryPivot.Albums -> {
                            items(
                                count = albumGroups.size,
                                key = { index -> albumGroups[index].name },
                            ) { index ->
                                val group = albumGroups[index]
                                LibraryGroupRow(
                                    title = group.name,
                                    detail = group.detail,
                                    accent = accentFor(group.name.hashCode()),
                                    onClick = {
                                        onPlay(group.sourceIndex)
                                    },
                                )
                            }
                        }

                        LibraryPivot.Favorites -> {
                            if (favorites.isEmpty()) {
                                item {
                                    C2InfoPanel(
                                        title = "No favorite tracks",
                                        detail = "Mark local tracks with ★ to keep a quick local favorites view.",
                                    )
                                }
                            } else {
                                items(
                                    count = favorites.size,
                                    key = { index -> favorites[index].item.uri },
                                ) { favoriteIndex ->
                                    val indexedTrack = favorites[favoriteIndex]
                                    val item = indexedTrack.item
                                    LocalTrackRow(
                                        item = item,
                                        accent = accentFor(indexedTrack.sourceIndex),
                                        favorite = true,
                                        onToggleFavorite = { onToggleFavorite(item) },
                                        onPlay = { onPlay(indexedTrack.sourceIndex) },
                                    )
                                }
                            }
                        }

                        LibraryPivot.Playlists -> {
                            item {
                                SableActionButton(
                                    text = "Create playlist",
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = onCreatePlaylist,
                                )
                            }

                            if (playlists.isEmpty()) {
                                item {
                                    C2InfoPanel(
                                        title = "No playlists yet",
                                        detail = "Create a local playlist from audio already added to Sable Media.",
                                    )
                                }
                            } else {
                                items(
                                    count = playlists.size,
                                    key = { index -> playlists[index].id },
                                ) { index ->
                                    val playlist = playlists[index]
                                    PlaylistCard(
                                        playlist = playlist,
                                        availableUris = availableUris,
                                        onPlay = { onPlayPlaylist(playlist) },
                                        onEdit = { onEditPlaylist(playlist) },
                                        onDelete = { onDeletePlaylist(playlist) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!alphabet.isEmpty) {
                SableAlphabetRail(
                    index = alphabet,
                    activeKey = alphabet.sectionKeyAt((firstVisible - COLLECTION_FIXED_ITEM_COUNT).coerceAtLeast(0)),
                    onJump = ::jumpTo,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        // Pinned: the current playback state is visible on open, never at the end of the list.
        MediaPinnedMiniPlayer(
            playback = playback,
            enabled = showMiniPlayer,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenNowPlaying = onOpenNowPlaying,
            onOpenQueue = { onDestinationChange(MediaDestination.Queue) },
        )
    }
}

private val EMPTY_ALPHABET = SableAlphabetIndex.build(emptyList())

private data class MediaIndexedTrack(
    val item: LocalAudioItem,
    val sourceIndex: Int,
)

private data class MediaGroupSummary(
    val name: String,
    val detail: String,
    val sourceIndex: Int,
)

private data class MediaCollectionIndex(
    val songs: List<MediaIndexedTrack>,
    val artists: List<MediaGroupSummary>,
    val albums: List<MediaGroupSummary>,
    val favorites: List<MediaIndexedTrack>,
    val availableUris: Set<String>,
    val sections: Map<LibraryPivot, SableAlphabetIndex>,
) {
    companion object {
        val Empty =
            MediaCollectionIndex(
                songs = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                favorites = emptyList(),
                availableUris = emptySet(),
                sections = emptyMap(),
            )
    }
}

private fun buildMediaCollectionIndex(
    localItems: List<LocalAudioItem>,
    favoriteAudioUris: Set<String>,
): MediaCollectionIndex {
    val locale = Locale.ROOT
    val sourceIndexByUri =
        HashMap<String, Int>(localItems.size * 2).apply {
            localItems.forEachIndexed { index, item ->
                put(item.uri, index)
            }
        }

    val songs =
        localItems
            .asSequence()
            .sortedBy { item -> item.title.lowercase(locale) }
            .map { item ->
                MediaIndexedTrack(
                    item = item,
                    sourceIndex = sourceIndexByUri.getValue(item.uri),
                )
            }.toList()

    val favorites =
        songs.filter { indexed ->
            indexed.item.uri in favoriteAudioUris
        }

    val artists =
        localItems
            .groupBy { item -> item.artist }
            .entries
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.key },
            ).map { entry ->
                val tracks = entry.value
                MediaGroupSummary(
                    name = entry.key,
                    detail = "${tracks.size} track${if (tracks.size == 1) "" else "s"}",
                    sourceIndex = sourceIndexByUri.getValue(tracks.first().uri),
                )
            }

    val albums =
        localItems
            .groupBy { item -> item.album }
            .entries
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.key },
            ).map { entry ->
                val tracks = entry.value
                MediaGroupSummary(
                    name = entry.key,
                    detail = tracks.first().artist,
                    sourceIndex = sourceIndexByUri.getValue(tracks.first().uri),
                )
            }

    val sections =
        mapOf(
            LibraryPivot.Songs to
                SableAlphabetIndex.build(
                    songs.map { indexed -> indexed.item.title },
                ),
            LibraryPivot.Artists to
                SableAlphabetIndex.build(
                    artists.map { group -> group.name },
                ),
            LibraryPivot.Albums to
                SableAlphabetIndex.build(
                    albums.map { group -> group.name },
                ),
            LibraryPivot.Favorites to
                SableAlphabetIndex.build(
                    favorites.map { indexed -> indexed.item.title },
                ),
            LibraryPivot.Playlists to EMPTY_ALPHABET,
        )

    return MediaCollectionIndex(
        songs = songs,
        artists = artists,
        albums = albums,
        favorites = favorites,
        availableUris = sourceIndexByUri.keys.toSet(),
        sections = sections,
    )
}

@Composable
private fun LocalTrackRow(
    item: LocalAudioItem,
    accent: Color,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
) {
    // Dense row: one primary trailing action (play); favorite moves to the menu.
    Column(modifier = Modifier.fillMaxWidth()) {
        SableDenseRow(
            title = item.title,
            subtitle = listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" · "),
            onClick = onPlay,
            actions =
                listOf(
                    SableRowAction(ROW_ACTION_PLAY, "Play", primary = true),
                    SableRowAction(
                        ROW_ACTION_FAVORITE,
                        if (favorite) "Remove from favorites ★" else "Add to favorites ☆",
                    ),
                ),
            onAction = { action ->
                when (action.id) {
                    ROW_ACTION_PLAY -> onPlay()
                    ROW_ACTION_FAVORITE -> onToggleFavorite()
                }
            },
            leading = {
                ArtworkTile(
                    accent = accent,
                    modifier = Modifier.width(46.dp),
                )
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PlaylistCard(
    playlist: MediaPlaylist,
    availableUris: Set<String>,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val trackCount =
        playlist.itemUris.count { uri ->
            uri in availableUris
        }
    var confirmRemove by remember(playlist.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Xs)) {
            SableDenseRow(
                title = playlist.name,
                subtitle = "$trackCount track${if (trackCount == 1) "" else "s"}",
                onClick = onPlay,
                actions =
                    listOf(
                        SableRowAction(ROW_ACTION_PLAY, "Play", primary = true),
                        SableRowAction(ROW_ACTION_EDIT, "Edit"),
                        SableRowAction(ROW_ACTION_REMOVE, "Remove", destructive = true),
                    ),
                onAction = { action ->
                    when (action.id) {
                        ROW_ACTION_PLAY -> onPlay()
                        ROW_ACTION_EDIT -> onEdit()
                        ROW_ACTION_REMOVE -> confirmRemove = true
                    }
                },
            )
            if (confirmRemove) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
                ) {
                    SableActionButton(
                        text = "Remove playlist",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmRemove = false
                            onDelete()
                        },
                    )
                    SableActionButton(
                        text = "Keep",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = { confirmRemove = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGroupRow(
    title: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = SableSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkTile(
                    accent = accent,
                    modifier = Modifier.width(46.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = SableResponsive.PRIMARY_TEXT_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = SableResponsive.SECONDARY_TEXT_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "▶",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NowPlayingScreen(
    playback: PlaybackUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Md),
    ) {
        Text(
            text = "now playing",
            style = MaterialTheme.typography.headlineLarge,
        )

        ArtworkTile(
            accent = accentFor(playback.title.hashCode()),
            modifier = Modifier.fillMaxWidth(),
            large = true,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
        ) {
            Text(
                text = playback.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playback.source,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val duration = playback.durationMs.coerceAtLeast(0L)
        val sliderMax = duration.takeIf { it > 0L }?.toFloat() ?: 1f
        val sliderValue =
            playback.positionMs
                .coerceIn(0L, duration.takeIf { it > 0L } ?: 0L)
                .toFloat()

        Slider(
            value = sliderValue,
            onValueChange = { value ->
                if (duration > 0L) {
                    onSeek(value.toLong())
                }
            },
            valueRange = 0f..sliderMax,
            enabled = duration > 0L,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(playback.positionMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    if (duration > 0L) {
                        formatDuration(duration)
                    } else {
                        "LIVE"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (duration > 0L) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MediaPink
                    },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            PlayerButton(
                text = "◀",
                enabled = playback.hasPrevious,
                modifier = Modifier.weight(1f),
                onClick = onPrevious,
            )
            PlayerButton(
                text = if (playback.isPlaying) "❚❚" else "▶",
                active = true,
                modifier = Modifier.weight(1f),
                onClick = onPlayPause,
            )
            PlayerButton(
                text = "▶",
                enabled = playback.hasNext,
                modifier = Modifier.weight(1f),
                onClick = onNext,
            )
            PlayerButton(
                text = "■",
                modifier = Modifier.weight(1f),
                onClick = onStop,
            )
        }
    }
}

@Composable
private fun RadioScreen(
    stations: List<RadioStation>,
    favoriteStationIds: Set<String>,
    showFavoritesOnly: Boolean,
    message: String?,
    onPlay: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onManage: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "internet radio",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
            )
            SableActionButton(
                text = "Manage",
                primary = false,
                onClick = onManage,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
        ) {
            Surface(
                onClick = { onFavoritesOnlyChange(false) },
                color = Color.Transparent,
                contentColor =
                    if (!showFavoritesOnly) {
                        MediaPink
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Text(
                    text = "stations",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Surface(
                onClick = { onFavoritesOnlyChange(true) },
                color = Color.Transparent,
                contentColor =
                    if (showFavoritesOnly) {
                        MediaPink
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Text(
                    text = "favorites",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val visibleStations =
            if (showFavoritesOnly) {
                stations.filter { it.id in favoriteStationIds }
            } else {
                stations
            }

        if (visibleStations.isEmpty()) {
            EmptyPanel(
                title =
                    if (showFavoritesOnly) {
                        "No favorite stations"
                    } else {
                        "No saved stations"
                    },
                detail =
                    if (showFavoritesOnly) {
                        "Mark stations with ★ to keep a quick local favorites view."
                    } else {
                        "Add a station by name and direct HTTP(S) stream URL."
                    },
            )
        } else {
            visibleStations.forEach { station ->
                RadioStationRow(
                    station = station,
                    favorite = station.id in favoriteStationIds,
                    onToggleFavorite = { onToggleFavorite(station) },
                    onPlay = { onPlay(station) },
                )
            }
        }

        SableActionButton(
            text = "Add new station",
            modifier = Modifier.fillMaxWidth(),
            onClick = onAdd,
        )
        // The mini-player is pinned below the list by MediaScreen (current state visible on open).
    }
}

@Composable
private fun RadioStationRow(
    station: RadioStation,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
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
        SableDenseRow(
            title = station.name,
            subtitle = station.url,
            onClick = onPlay,
            modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Xs),
            actions =
                listOf(
                    SableRowAction(ROW_ACTION_PLAY, "Play", primary = true),
                    SableRowAction(
                        ROW_ACTION_FAVORITE,
                        if (favorite) "Remove from favorites ★" else "Add to favorites ☆",
                    ),
                ),
            onAction = { action ->
                when (action.id) {
                    ROW_ACTION_PLAY -> onPlay()
                    ROW_ACTION_FAVORITE -> onToggleFavorite()
                }
            },
            leading = {
                Surface(
                    modifier = Modifier.width(MediaControlSize),
                    shape = MaterialTheme.shapes.small,
                    color = MediaPink.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MediaPink.copy(alpha = 0.7f)),
                ) {
                    Box(
                        modifier = Modifier.aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MediaPink,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ManageStationsScreen(
    stations: List<RadioStation>,
    editingStation: RadioStation?,
    stationName: String,
    stationUrl: String,
    message: String?,
    onEdit: (RadioStation) -> Unit,
    onDelete: (RadioStation) -> Unit,
    onStationNameChange: (String) -> Unit,
    onStationUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        BackTitle(
            title = "station management",
            onBack = onBack,
        )

        stations.forEach { station ->
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
                    modifier = Modifier.padding(SableSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = station.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SableActionButton(
                        text = "Edit",
                        primary = false,
                        onClick = { onEdit(station) },
                    )
                    SableActionButton(
                        text = "Remove",
                        primary = false,
                        onClick = { onDelete(station) },
                    )
                }
            }
        }

        if (editingStation != null) {
            Text(
                text = "edit station",
                style = MaterialTheme.typography.titleLarge,
            )
            StationFields(
                stationName = stationName,
                stationUrl = stationUrl,
                onStationNameChange = onStationNameChange,
                onStationUrlChange = onStationUrlChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
            ) {
                SableActionButton(
                    text = "Cancel edit",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancelEdit,
                )
                SableActionButton(
                    text = "Save changes",
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                )
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "Manage edits and removes existing stations only. Use Add new station from Radio to create one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddStationScreen(
    stationName: String,
    stationUrl: String,
    message: String?,
    onStationNameChange: (String) -> Unit,
    onStationUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        BackTitle(
            title = "add station",
            onBack = onCancel,
        )

        Text(
            text = "Add one live-radio stream. Existing stations are managed separately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StationFields(
            stationName = stationName,
            stationUrl = stationUrl,
            onStationNameChange = onStationNameChange,
            onStationUrlChange = onStationUrlChange,
        )

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
                text = "Add station",
                modifier = Modifier.weight(1f),
                onClick = onSave,
            )
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StationFields(
    stationName: String,
    stationUrl: String,
    onStationNameChange: (String) -> Unit,
    onStationUrlChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = stationName,
        onValueChange = onStationNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Station name") },
    )
    OutlinedTextField(
        value = stationUrl,
        onValueChange = onStationUrlChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("HTTP(S) stream URL") },
    )
}

@Composable
private fun BackTitle(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.width(MediaControlSize),
            color = Color.Transparent,
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
private fun ArtworkTile(
    accent: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(if (large) LARGE_ARTWORK_ASPECT_RATIO else 1f)
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    accent,
                                    MediaPink,
                                    MediaBlue,
                                ),
                        ),
                    shape =
                        if (large) {
                            MaterialTheme.shapes.large
                        } else {
                            MaterialTheme.shapes.medium
                        },
                ).padding(
                    if (large) {
                        SableSpacing.Xl
                    } else {
                        SableSpacing.Sm
                    },
                ),
    ) {
        Text(
            text = "S",
            modifier = Modifier.align(Alignment.BottomStart),
            style =
                if (large) {
                    MaterialTheme.typography.displayLarge
                } else {
                    MaterialTheme.typography.titleLarge
                },
            fontWeight = FontWeight.Light,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MediaPrimaryTouchHeight),
        shape = MaterialTheme.shapes.medium,
        color =
            if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        contentColor =
            if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        border =
            BorderStroke(
                1.dp,
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun EmptyPanel(
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

private fun accentFor(seed: Int): Color {
    val palette =
        listOf(
            MediaPurple,
            MediaBlue,
            MediaPink,
            MediaOrange,
            MediaGreen,
        )

    return palette[Math.floorMod(seed, palette.size)]
}

private fun formatDuration(valueMs: Long): String {
    val totalSeconds = valueMs.coerceAtLeast(0L) / MILLIS_PER_SECOND
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
