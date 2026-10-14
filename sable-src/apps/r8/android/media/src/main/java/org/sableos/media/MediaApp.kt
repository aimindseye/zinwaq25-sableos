package org.sableos.media

import android.content.ContentResolver
import android.net.Uri
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import org.sableos.design.SableGlobalTheme

internal data class MediaRepositories(
    val localAudio: LocalAudioRepository,
    val audioFolderScanner: AudioFolderScanner,
    val stations: RadioStationRepository,
    val libraryState: MediaLibraryStateRepository,
    val podcasts: PodcastRepository,
    val podcastState: PodcastStateRepository,
    val settings: MediaSettingsRepository,
)

internal data class MediaHostServices(
    val contentResolver: ContentResolver,
    val window: Window,
    val playbackController: PlaybackController,
    val playUri: (Uri, String, String) -> Unit,
    val playLocalQueue: (List<LocalAudioItem>, Int) -> Unit,
    val adjustVolume: (raise: Boolean) -> Unit,
    val setKeyHandler: (((KeyEvent) -> Boolean)?) -> Unit,
)

@Composable
internal fun MediaApp(
    repositories: MediaRepositories,
    host: MediaHostServices,
    playbackState: PlaybackUiState,
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val latestPlayback by rememberUpdatedState(playbackState)
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val latestBackDispatcher by rememberUpdatedState(backDispatcher)

    val state =
        remember {
            MediaAppState(
                repositories = repositories,
                initialSettings = repositories.settings.load(),
            )
        }
    val controllers =
        remember {
            MediaControllers(
                state = state,
                repositories = repositories,
                host = host,
                scope = uiScope,
                environment =
                    MediaUiEnvironment(
                        toast = { text -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() },
                        playback = { latestPlayback },
                        backDispatcher = { latestBackDispatcher },
                    ),
            )
        }

    LaunchedEffect(repositories.localAudio, repositories.libraryState) {
        controllers.library.load()
    }

    LaunchedEffect(state.destination, host.playbackController) {
        host.playbackController.setProgressUpdatesEnabled(
            state.destination == MediaDestination.NowPlaying,
        )
    }

    KeepScreenAwakeEffect(
        window = host.window,
        keepAwake = state.settings.keepScreenAwakeWhilePlaying && playbackState.isPlaying,
    )

    DisposableEffect(host) {
        host.setKeyHandler(controllers.keys::handle)
        onDispose { host.setKeyHandler(null) }
    }

    // BACK_RETURNS_ONE_LOGICAL_LAYER: hardware Back and Esc leave a sub-screen or search, not the app.
    BackHandler(enabled = state.backStep() != null) {
        state.goBack()
    }

    val localAudioPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
            controllers.library::importAudio,
        )
    val localAudioFolderPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
            controllers.library::importFolder,
        )

    SableGlobalTheme(window = host.window) {
        MediaKeyDialogs(
            playback = playbackState,
            showInfo = state.showInfo,
            pendingRemoval = state.pendingRemoval,
            onDismissInfo = { state.showInfo = false },
            onConfirmRemoval = controllers.library::confirmRemoval,
            onDismissRemoval = { state.pendingRemoval = null },
        )
        CompositionLocalProvider(LocalMediaTextInputFocus provides state.textInputFocus) {
            MediaScreen(
                state = state.screenState(playbackState),
                actions =
                    mediaScreenActions(
                        state = state,
                        controllers = controllers,
                        host = host,
                        pickers =
                            MediaPickers(
                                audioFiles = { localAudioPicker.launch(arrayOf(AUDIO_MIME_FILTER)) },
                                audioFolder = { localAudioFolderPicker.launch(null) },
                            ),
                    ),
            )
        }
    }
}

/** FLAG_KEEP_SCREEN_ON while [keepAwake], cleared when it turns false or the app leaves composition. */
@Composable
private fun KeepScreenAwakeEffect(
    window: Window,
    keepAwake: Boolean,
) {
    DisposableEffect(window, keepAwake) {
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private class MediaPickers(
    val audioFiles: () -> Unit,
    val audioFolder: () -> Unit,
)

private fun mediaScreenActions(
    state: MediaAppState,
    controllers: MediaControllers,
    host: MediaHostServices,
    pickers: MediaPickers,
): MediaScreenActions {
    val library = controllers.library
    val playlists = controllers.playlists
    val podcasts = controllers.podcasts
    val stations = controllers.stations
    val playback = host.playbackController
    return MediaScreenActions(
        onDestinationChange = state::navigate,
        onLibraryPivotChange = { state.libraryPivot = it },
        onAddLocalAudio = { library.whenLoaded(pickers.audioFiles) },
        onAddLocalFolder = { library.whenLoaded(pickers.audioFolder) },
        onPlayLocal = library::playLocal,
        onRemoveLocal = library::removeLocal,
        onToggleFavoriteAudio = library::toggleFavorite,
        onPodcastFeedUrlChange = podcasts::changeFeedUrl,
        onAddPodcastFeed = podcasts::addFeed,
        onRefreshPodcasts = podcasts::refresh,
        onRemovePodcastSubscription = podcasts::remove,
        onPlayPodcastEpisode = podcasts::play,
        onQueuePodcastEpisode = podcasts::queue,
        onToggleSavedPodcastEpisode = podcasts::toggleSaved,
        onPlayStation = stations::play,
        onToggleFavoriteStation = stations::toggleFavorite,
        onRadioFavoritesOnlyChange = { state.showFavoriteStationsOnly = it },
        onAddStation = { stations.openForm(MediaDestination.AddStation) },
        onManageStations = { stations.openForm(MediaDestination.ManageStations) },
        onEditStation = stations::edit,
        onDeleteStation = stations::delete,
        onStationNameChange = {
            state.stationName = it
            state.message = null
        },
        onStationUrlChange = {
            state.stationUrl = it
            state.message = null
        },
        onSaveNewStation = stations::saveNew,
        onSaveEditedStation = stations::saveEdited,
        onCancelStationFlow = stations::cancel,
        onCreatePlaylist = { library.whenLoaded(playlists::create) },
        onEditPlaylist = playlists::edit,
        onDeletePlaylist = playlists::delete,
        onPlayPlaylist = playlists::play,
        onPlaylistNameChange = { state.playlistName = it },
        onTogglePlaylistItem = playlists::toggleItem,
        onSavePlaylist = playlists::save,
        onCancelPlaylistEditor = playlists::cancel,
        onSettingsChange = controllers::changeSettings,
        onPlayQueueIndex = playback::playQueueIndex,
        onMoveQueueItem = playback::moveQueueItem,
        onRemoveQueueItem = playback::removeQueueItem,
        onPlayPause = { controllers.keys.run(MediaKeyCommand.PlayPause) },
        onPrevious = { playback.skip(PlaybackSkip.Previous) },
        onNext = { playback.skip(PlaybackSkip.Next) },
        onStop = playback::stop,
        onSeek = playback::seekTo,
        onOpenSearch = state::openSearch,
        onSearchQueryChange = { state.searchQuery = it },
        onCloseSearch = { state.searchQuery = null },
        onTrackFocusChange = state::changeTrackFocus,
    )
}

private const val AUDIO_MIME_FILTER = "audio/*"
