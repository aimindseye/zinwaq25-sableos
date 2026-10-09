package org.sableos.media

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
)

@Composable
internal fun MediaApp(
    repositories: MediaRepositories,
    host: MediaHostServices,
    playbackState: PlaybackUiState,
) {
    val localAudioRepository = repositories.localAudio
    val audioFolderScanner = repositories.audioFolderScanner
    val stationRepository = repositories.stations
    val libraryStateRepository = repositories.libraryState
    val podcastRepository = repositories.podcasts
    val podcastStateRepository = repositories.podcastState
    val settingsRepository = repositories.settings
    val initialSettings = remember(settingsRepository) { settingsRepository.load() }
    val playbackController = host.playbackController
    val contentResolver = host.contentResolver
    val window = host.window
    val playUri = host.playUri
    val playLocalQueue = host.playLocalQueue

    val uiScope = rememberCoroutineScope()

    var settings by remember {
        mutableStateOf(initialSettings)
    }
    var destination by remember {
        mutableStateOf(
            when (initialSettings.startPage) {
                MediaStartPage.Collection -> MediaDestination.Collection
                MediaStartPage.Radio -> MediaDestination.Radio
            },
        )
    }
    var libraryPivot by remember {
        mutableStateOf(LibraryPivot.Songs)
    }
    var localItems by remember {
        mutableStateOf(emptyList<LocalAudioItem>())
    }
    var localLibraryLoading by remember {
        mutableStateOf(true)
    }
    var podcastSubscriptions by remember {
        mutableStateOf(podcastRepository.loadSubscriptions())
    }
    var savedPodcastEpisodeIds by remember {
        mutableStateOf(podcastStateRepository.loadSavedEpisodeIds())
    }
    var playedPodcastEpisodeIds by remember {
        mutableStateOf(podcastStateRepository.loadPlayedEpisodeIds())
    }
    var podcastFeedUrl by remember {
        mutableStateOf("")
    }
    var podcastBusy by remember {
        mutableStateOf(false)
    }
    var podcastRefreshing by remember {
        mutableStateOf(false)
    }
    var podcastMessage by remember {
        mutableStateOf<String?>(null)
    }
    var stations by remember {
        mutableStateOf(stationRepository.loadStations())
    }
    var favoriteAudioUris by remember {
        mutableStateOf(libraryStateRepository.loadFavoriteAudioUris())
    }
    var favoriteStationIds by remember {
        mutableStateOf(libraryStateRepository.loadFavoriteStationIds())
    }
    var playlists by remember {
        mutableStateOf(emptyList<MediaPlaylist>())
    }
    var showFavoriteStationsOnly by remember {
        mutableStateOf(false)
    }

    var editingStation by remember {
        mutableStateOf<RadioStation?>(null)
    }
    var stationName by remember {
        mutableStateOf("")
    }
    var stationUrl by remember {
        mutableStateOf("")
    }

    var editingPlaylist by remember {
        mutableStateOf<MediaPlaylist?>(null)
    }
    var playlistName by remember {
        mutableStateOf("")
    }
    var selectedPlaylistUris by remember {
        mutableStateOf(emptySet<String>())
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(localAudioRepository, libraryStateRepository) {
        val loadResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val loadedItems = localAudioRepository.loadItems()
                    val loadedPlaylists =
                        libraryStateRepository.pruneMissingAudio(
                            current = libraryStateRepository.loadPlaylists(),
                            validUris = loadedItems.mapTo(mutableSetOf()) { it.uri },
                        )
                    loadedItems to loadedPlaylists
                }
            }

        loadResult
            .onSuccess { (loadedItems, loadedPlaylists) ->
                localItems = loadedItems
                playlists = loadedPlaylists
            }.onFailure {
                message = "Local collection could not be loaded."
            }
        localLibraryLoading = false
    }

    LaunchedEffect(destination, playbackController) {
        playbackController.setProgressUpdatesEnabled(
            destination == MediaDestination.NowPlaying,
        )
    }

    DisposableEffect(
        settings.keepScreenAwakeWhilePlaying,
        playbackState.isPlaying,
    ) {
        val shouldKeepAwake =
            settings.keepScreenAwakeWhilePlaying &&
                playbackState.isPlaying

        if (shouldKeepAwake) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        } else {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }

        onDispose {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    fun clearStationForm() {
        editingStation = null
        stationName = ""
        stationUrl = ""
    }

    fun clearPlaylistForm() {
        editingPlaylist = null
        playlistName = ""
        selectedPlaylistUris = emptySet()
    }

    val localAudioPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isEmpty()) {
                return@rememberLauncherForActivityResult
            }

            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            val currentItems = localItems
            val currentPlaylists = playlists
            message = "Importing ${uris.size} audio file${if (uris.size == 1) "" else "s"}…"

            uiScope.launch {
                val result =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val updatedItems =
                                localAudioRepository.addUris(
                                    current = currentItems,
                                    uris = uris,
                                )
                            val updatedPlaylists =
                                libraryStateRepository.pruneMissingAudio(
                                    current = currentPlaylists,
                                    validUris = updatedItems.map { it.uri }.toSet(),
                                )
                            updatedItems to updatedPlaylists
                        }
                    }

                result
                    .onSuccess { (updatedItems, updatedPlaylists) ->
                        localItems = updatedItems
                        playlists = updatedPlaylists
                        libraryPivot = LibraryPivot.Songs
                        message =
                            "${uris.size} local audio file${if (uris.size == 1) "" else "s"} added."
                    }.onFailure {
                        message = "Audio import failed. Existing collection was not changed."
                    }
            }
        }

    val localAudioFolderPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { treeUri ->
            if (treeUri == null) {
                return@rememberLauncherForActivityResult
            }

            runCatching {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            val currentItems = localItems
            val currentPlaylists = playlists
            message = "Scanning audio folder…"

            uiScope.launch {
                val result =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val uris = audioFolderScanner.scan(treeUri)
                            if (uris.isEmpty()) {
                                return@withContext Triple(
                                    emptyList<LocalAudioItem>(),
                                    currentPlaylists,
                                    0,
                                )
                            }

                            val updatedItems =
                                localAudioRepository.addUris(
                                    current = currentItems,
                                    uris = uris,
                                )
                            val updatedPlaylists =
                                libraryStateRepository.pruneMissingAudio(
                                    current = currentPlaylists,
                                    validUris = updatedItems.map { it.uri }.toSet(),
                                )
                            Triple(
                                updatedItems,
                                updatedPlaylists,
                                uris.size,
                            )
                        }
                    }

                result
                    .onSuccess { (updatedItems, updatedPlaylists, importedCount) ->
                        if (importedCount == 0) {
                            message = "No audio files found in that folder."
                        } else {
                            localItems = updatedItems
                            playlists = updatedPlaylists
                            libraryPivot = LibraryPivot.Songs
                            message =
                                "$importedCount audio file${if (importedCount == 1) "" else "s"} imported from folder."
                        }
                    }.onFailure {
                        message = "Folder import failed. Existing collection was not changed."
                    }
            }
        }

    SableGlobalTheme(window = window) {
        MediaScreen(
            state =
                MediaScreenState(
                    destination = destination,
                    libraryPivot = libraryPivot,
                    localItems = localItems,
                    localLibraryLoading = localLibraryLoading,
                    podcastSubscriptions = podcastSubscriptions,
                    savedPodcastEpisodeIds = savedPodcastEpisodeIds,
                    playedPodcastEpisodeIds = playedPodcastEpisodeIds,
                    podcastFeedUrl = podcastFeedUrl,
                    podcastBusy = podcastBusy,
                    podcastRefreshing = podcastRefreshing,
                    podcastMessage = podcastMessage,
                    stations = stations,
                    playback = playbackState,
                    favoriteAudioUris = favoriteAudioUris,
                    favoriteStationIds = favoriteStationIds,
                    playlists = playlists,
                    settings = settings,
                    playlistName = playlistName,
                    selectedPlaylistUris = selectedPlaylistUris,
                    editingPlaylist = editingPlaylist,
                    showFavoriteStationsOnly = showFavoriteStationsOnly,
                    stationName = stationName,
                    stationUrl = stationUrl,
                    editingStation = editingStation,
                    message = message,
                ),
            actions =
                MediaScreenActions(
                    onDestinationChange = { next ->
                        destination = next
                        message = null

                        if (
                            next != MediaDestination.ManageStations &&
                            next != MediaDestination.AddStation
                        ) {
                            clearStationForm()
                        }

                        if (next != MediaDestination.PlaylistEditor) {
                            clearPlaylistForm()
                        }
                    },
                    onLibraryPivotChange = {
                        libraryPivot = it
                    },
                    onAddLocalAudio = {
                        if (localLibraryLoading) {
                            message = "Local collection is still loading."
                        } else {
                            localAudioPicker.launch(arrayOf("audio/*"))
                        }
                    },
                    onAddLocalFolder = {
                        if (localLibraryLoading) {
                            message = "Local collection is still loading."
                        } else {
                            localAudioFolderPicker.launch(null)
                        }
                    },
                    onPlayLocal = { selectedIndex ->
                        playLocalQueue(
                            localItems,
                            selectedIndex,
                        )
                        destination = MediaDestination.NowPlaying
                    },
                    onRemoveLocal = { item ->
                        localItems =
                            localAudioRepository.remove(
                                current = localItems,
                                uri = item.uri,
                            )

                        if (item.uri in favoriteAudioUris) {
                            favoriteAudioUris =
                                libraryStateRepository.toggleFavoriteAudio(
                                    current = favoriteAudioUris,
                                    uri = item.uri,
                                )
                        }

                        playlists =
                            libraryStateRepository.pruneMissingAudio(
                                current = playlists,
                                validUris = localItems.map { it.uri }.toSet(),
                            )

                        message = "Removed from the Sable Media collection."
                    },
                    onToggleFavoriteAudio = { item ->
                        favoriteAudioUris =
                            libraryStateRepository.toggleFavoriteAudio(
                                current = favoriteAudioUris,
                                uri = item.uri,
                            )
                    },
                    onPodcastFeedUrlChange = { value ->
                        podcastFeedUrl = value
                        podcastMessage = null
                    },
                    onAddPodcastFeed = {
                        val candidate = podcastFeedUrl.trim()
                        when {
                            podcastBusy -> {
                                Unit
                            }

                            !PodcastRepository.isSupportedFeedUrl(candidate) -> {
                                podcastMessage = "Podcast feed must use HTTPS."
                            }

                            else -> {
                                val currentSubscriptions = podcastSubscriptions
                                podcastBusy = true
                                podcastMessage = "Contacting podcast feed…"

                                uiScope.launch {
                                    val result =
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                podcastRepository.subscribe(
                                                    current = currentSubscriptions,
                                                    feedUrl = candidate,
                                                )
                                            }
                                        }

                                    result
                                        .onSuccess { updated ->
                                            podcastSubscriptions = updated
                                            podcastFeedUrl = ""
                                            podcastMessage =
                                                "Podcast subscribed. Pull down to refresh feeds."
                                        }.onFailure { error ->
                                            podcastMessage =
                                                error.message
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?: "Podcast feed could not be added."
                                        }
                                    podcastBusy = false
                                }
                            }
                        }
                    },
                    onRefreshPodcasts = {
                        if (!podcastBusy && podcastSubscriptions.isNotEmpty()) {
                            val currentSubscriptions = podcastSubscriptions
                            podcastBusy = true
                            podcastRefreshing = true
                            podcastMessage = "Refreshing subscribed feeds…"

                            uiScope.launch {
                                val result =
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            podcastRepository.refreshAll(
                                                current = currentSubscriptions,
                                            )
                                        }
                                    }

                                result
                                    .onSuccess { refresh ->
                                        podcastSubscriptions = refresh.subscriptions
                                        podcastMessage =
                                            if (refresh.failedFeeds == 0) {
                                                "Podcasts refreshed."
                                            } else {
                                                "Podcasts refreshed with " +
                                                    "${refresh.failedFeeds} feed failure(s); " +
                                                    "cached episodes were kept."
                                            }
                                    }.onFailure { error ->
                                        podcastMessage =
                                            error.message
                                                ?.takeIf { it.isNotBlank() }
                                                ?: "Podcast refresh failed; cached episodes were kept."
                                    }
                                podcastRefreshing = false
                                podcastBusy = false
                            }
                        }
                    },
                    onRemovePodcastSubscription = { subscription ->
                        podcastSubscriptions =
                            podcastRepository.remove(
                                current = podcastSubscriptions,
                                podcastId = subscription.id,
                            )
                        podcastMessage = "Podcast removed from this device."
                    },
                    onPlayPodcastEpisode = { episode ->
                        playedPodcastEpisodeIds =
                            podcastStateRepository.markPlayed(
                                current = playedPodcastEpisodeIds,
                                episodeId = episode.id,
                            )
                        playUri(
                            Uri.parse(episode.mediaUrl),
                            episode.title,
                            episode.podcastTitle,
                        )
                        destination = MediaDestination.NowPlaying
                    },
                    onQueuePodcastEpisode = { episode ->
                        val queued =
                            playbackController.addToQueue(
                                uri = episode.mediaUrl,
                                title = episode.title,
                                source = episode.podcastTitle,
                            )
                        podcastMessage =
                            if (queued) {
                                "Added to Up Next."
                            } else {
                                "Player is still connecting. Try again."
                            }
                    },
                    onToggleSavedPodcastEpisode = { episode ->
                        savedPodcastEpisodeIds =
                            podcastStateRepository.toggleSaved(
                                current = savedPodcastEpisodeIds,
                                episodeId = episode.id,
                            )
                    },
                    onPlayStation = { station ->
                        playUri(
                            Uri.parse(station.url),
                            station.name,
                            "live radio",
                        )
                        destination = MediaDestination.NowPlaying
                    },
                    onToggleFavoriteStation = { station ->
                        favoriteStationIds =
                            libraryStateRepository.toggleFavoriteStation(
                                current = favoriteStationIds,
                                stationId = station.id,
                            )
                    },
                    onRadioFavoritesOnlyChange = {
                        showFavoriteStationsOnly = it
                    },
                    onAddStation = {
                        clearStationForm()
                        message = null
                        destination = MediaDestination.AddStation
                    },
                    onManageStations = {
                        clearStationForm()
                        message = null
                        destination = MediaDestination.ManageStations
                    },
                    onEditStation = { station ->
                        editingStation = station
                        stationName = station.name
                        stationUrl = station.url
                        message = null
                    },
                    onDeleteStation = { station ->
                        stations =
                            stationRepository.deleteStation(
                                current = stations,
                                id = station.id,
                            )

                        if (station.id in favoriteStationIds) {
                            favoriteStationIds =
                                libraryStateRepository.toggleFavoriteStation(
                                    current = favoriteStationIds,
                                    stationId = station.id,
                                )
                        }

                        if (editingStation?.id == station.id) {
                            clearStationForm()
                        }

                        message = "Station removed."
                    },
                    onStationNameChange = {
                        stationName = it
                        message = null
                    },
                    onStationUrlChange = {
                        stationUrl = it
                        message = null
                    },
                    onSaveNewStation = {
                        val candidate = stationUrl.trim()

                        if (!RadioStationRepository.isSupportedUrl(candidate)) {
                            message = "Station URL must use HTTP or HTTPS."
                        } else {
                            stations =
                                stationRepository.saveStation(
                                    current = stations,
                                    name = stationName,
                                    url = candidate,
                                )
                            clearStationForm()
                            message = "Station saved locally."
                            destination = MediaDestination.Radio
                        }
                    },
                    onSaveEditedStation = {
                        val editing = editingStation
                        val candidate = stationUrl.trim()

                        when {
                            editing == null -> {
                                message = "Choose a station to edit."
                            }

                            !RadioStationRepository.isSupportedUrl(candidate) -> {
                                message = "Station URL must use HTTP or HTTPS."
                            }

                            else -> {
                                stations =
                                    stationRepository.updateStation(
                                        current = stations,
                                        id = editing.id,
                                        name = stationName,
                                        url = candidate,
                                    )
                                clearStationForm()
                                message = "Station updated."
                            }
                        }
                    },
                    onCancelStationFlow = {
                        if (destination == MediaDestination.AddStation) {
                            clearStationForm()
                            message = null
                            destination = MediaDestination.Radio
                        } else {
                            clearStationForm()
                            message = null
                        }
                    },
                    onCreatePlaylist = {
                        if (localLibraryLoading) {
                            message = "Local collection is still loading."
                        } else {
                            clearPlaylistForm()
                            message = null
                            destination = MediaDestination.PlaylistEditor
                        }
                    },
                    onEditPlaylist = { playlist ->
                        editingPlaylist = playlist
                        playlistName = playlist.name
                        selectedPlaylistUris = playlist.itemUris.toSet()
                        message = null
                        destination = MediaDestination.PlaylistEditor
                    },
                    onDeletePlaylist = { playlist ->
                        playlists =
                            libraryStateRepository.deletePlaylist(
                                current = playlists,
                                playlistId = playlist.id,
                            )
                        message = "Playlist removed."
                    },
                    onPlayPlaylist = { playlist ->
                        val itemsSnapshot = localItems
                        uiScope.launch {
                            val queue =
                                withContext(Dispatchers.Default) {
                                    val byUri =
                                        itemsSnapshot.associateBy { item ->
                                            item.uri
                                        }
                                    playlist.itemUris.mapNotNull { uri ->
                                        byUri[uri]
                                    }
                                }

                            if (queue.isEmpty()) {
                                message = "This playlist has no available tracks."
                            } else {
                                playLocalQueue(
                                    queue,
                                    0,
                                )
                                destination = MediaDestination.NowPlaying
                            }
                        }
                    },
                    onPlaylistNameChange = {
                        playlistName = it
                    },
                    onTogglePlaylistItem = { uri ->
                        selectedPlaylistUris =
                            selectedPlaylistUris
                                .toMutableSet()
                                .apply {
                                    if (!add(uri)) {
                                        remove(uri)
                                    }
                                }.toSet()
                    },
                    onSavePlaylist = {
                        playlists =
                            libraryStateRepository.savePlaylist(
                                current = playlists,
                                editingId = editingPlaylist?.id,
                                name = playlistName,
                                itemUris = selectedPlaylistUris,
                            )
                        clearPlaylistForm()
                        libraryPivot = LibraryPivot.Playlists
                        destination = MediaDestination.Collection
                        message = "Playlist saved locally."
                    },
                    onCancelPlaylistEditor = {
                        clearPlaylistForm()
                        libraryPivot = LibraryPivot.Playlists
                        destination = MediaDestination.Collection
                        message = null
                    },
                    onSettingsChange = { updated ->
                        settings = updated
                        settingsRepository.save(updated)
                    },
                    onPlayQueueIndex = playbackController::playQueueIndex,
                    onMoveQueueItem = playbackController::moveQueueItem,
                    onRemoveQueueItem = playbackController::removeQueueItem,
                    onPlayPause = {
                        playbackController.setPlaying(!playbackState.isPlaying)
                    },
                    onPrevious = {
                        playbackController.skip(PlaybackSkip.Previous)
                    },
                    onNext = {
                        playbackController.skip(PlaybackSkip.Next)
                    },
                    onStop = playbackController::stop,
                    onSeek = playbackController::seekTo,
                ),
        )
    }
}
