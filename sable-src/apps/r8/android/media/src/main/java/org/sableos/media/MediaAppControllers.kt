package org.sableos.media

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.OnBackPressedDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The event handlers behind [MediaApp], grouped by area so each stays small. */
internal class MediaControllers(
    private val state: MediaAppState,
    private val repositories: MediaRepositories,
    host: MediaHostServices,
    scope: CoroutineScope,
    environment: MediaUiEnvironment,
) {
    val library = MediaLibraryController(state, repositories, host, scope, environment.toast)
    val playlists = MediaPlaylistController(state, repositories.libraryState, host, scope)
    val podcasts = MediaPodcastController(state, repositories, host, scope)
    val stations = MediaStationController(state, repositories, host)
    val fm = MediaFmController(state, repositories, host)
    val keys = MediaKeyController(state, host, library, environment, fm)

    fun changeSettings(updated: MediaSettings) {
        state.settings = updated
        repositories.settings.save(updated)
    }
}

/** What the controllers read from the composition: a toast, the latest playback state and the Back dispatcher. */
internal class MediaUiEnvironment(
    val toast: (String) -> Unit,
    val playback: () -> PlaybackUiState,
    val backDispatcher: () -> OnBackPressedDispatcher?,
)

internal class MediaLibraryController(
    private val state: MediaAppState,
    private val repositories: MediaRepositories,
    private val host: MediaHostServices,
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit,
) {
    suspend fun load() {
        val loadResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val loadedItems = repositories.localAudio.loadItems()
                    val loadedPlaylists =
                        repositories.libraryState.pruneMissingAudio(
                            current = repositories.libraryState.loadPlaylists(),
                            validUris = loadedItems.mapTo(mutableSetOf()) { it.uri },
                        )
                    loadedItems to loadedPlaylists
                }
            }

        loadResult
            .onSuccess { (loadedItems, loadedPlaylists) ->
                state.localItems = loadedItems
                state.playlists = loadedPlaylists
            }.onFailure {
                state.message = "Local collection could not be loaded."
            }
        state.localLibraryLoading = false
    }

    /** Runs [action] once the local collection has loaded; until then it only explains why nothing happened. */
    fun whenLoaded(action: () -> Unit) {
        if (state.localLibraryLoading) {
            state.message = LIBRARY_LOADING_MESSAGE
        } else {
            action()
        }
    }

    fun playLocal(selectedIndex: Int) {
        host.playLocalQueue(state.localItems, selectedIndex)
        state.destination = MediaDestination.NowPlaying
    }

    fun removeLocal(item: LocalAudioItem) {
        state.localItems =
            repositories.localAudio.remove(
                current = state.localItems,
                uri = item.uri,
            )

        if (item.uri in state.favoriteAudioUris) {
            toggleFavorite(item)
        }

        state.playlists =
            repositories.libraryState.pruneMissingAudio(
                current = state.playlists,
                validUris = state.localItems.map { it.uri }.toSet(),
            )

        if (state.focusedTrack?.uri == item.uri) {
            state.focusedTrack = null
        }

        state.message = REMOVED_MESSAGE
    }

    fun confirmRemoval(item: LocalAudioItem) {
        state.pendingRemoval = null
        removeLocal(item)
        toast(REMOVED_MESSAGE)
    }

    fun toggleFavorite(item: LocalAudioItem) {
        state.favoriteAudioUris =
            repositories.libraryState.toggleFavoriteAudio(
                current = state.favoriteAudioUris,
                uri = item.uri,
            )
    }

    fun toggleFocusedFavorite() {
        val item = state.focusedTrack
        if (item == null) {
            toast("Move focus to a track to change its favorite.")
            return
        }
        toggleFavorite(item)
        toast(if (item.uri in state.favoriteAudioUris) "Added to favorites." else "Removed from favorites.")
    }

    fun importAudio(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }

        uris.forEach(::takeReadPermission)

        val currentItems = state.localItems
        val currentPlaylists = state.playlists
        state.message = "Importing ${uris.size} audio file${pluralSuffix(uris.size)}…"

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    addToCollection(currentItems, currentPlaylists, uris)
                }
            }.onSuccess { (updatedItems, updatedPlaylists) ->
                applyImport(updatedItems, updatedPlaylists)
                state.message = "${uris.size} local audio file${pluralSuffix(uris.size)} added."
            }.onFailure {
                state.message = "Audio import failed. Existing collection was not changed."
            }
        }
    }

    fun importFolder(treeUri: Uri?) {
        if (treeUri == null) {
            return
        }

        takeReadPermission(treeUri)

        val currentItems = state.localItems
        val currentPlaylists = state.playlists
        state.message = "Scanning audio folder…"

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val uris = repositories.audioFolderScanner.scan(treeUri)
                    if (uris.isEmpty()) null else addToCollection(currentItems, currentPlaylists, uris) to uris.size
                }
            }.onSuccess { imported ->
                if (imported == null) {
                    state.message = "No audio files found in that folder."
                } else {
                    val (collection, importedCount) = imported
                    applyImport(collection.first, collection.second)
                    state.message =
                        "$importedCount audio file${pluralSuffix(importedCount)} imported from folder."
                }
            }.onFailure {
                state.message = "Folder import failed. Existing collection was not changed."
            }
        }
    }

    private fun takeReadPermission(uri: Uri) {
        runCatching {
            host.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun addToCollection(
        currentItems: List<LocalAudioItem>,
        currentPlaylists: List<MediaPlaylist>,
        uris: List<Uri>,
    ): Pair<List<LocalAudioItem>, List<MediaPlaylist>> {
        val updatedItems =
            repositories.localAudio.addUris(
                current = currentItems,
                uris = uris,
            )
        val updatedPlaylists =
            repositories.libraryState.pruneMissingAudio(
                current = currentPlaylists,
                validUris = updatedItems.map { it.uri }.toSet(),
            )
        return updatedItems to updatedPlaylists
    }

    private fun applyImport(
        updatedItems: List<LocalAudioItem>,
        updatedPlaylists: List<MediaPlaylist>,
    ) {
        state.localItems = updatedItems
        state.playlists = updatedPlaylists
        state.libraryPivot = LibraryPivot.Songs
    }

    private companion object {
        const val REMOVED_MESSAGE = "Removed from the Sable Media collection."
        const val LIBRARY_LOADING_MESSAGE = "Local collection is still loading."
    }
}

internal class MediaPlaylistController(
    private val state: MediaAppState,
    private val libraryState: MediaLibraryStateRepository,
    private val host: MediaHostServices,
    private val scope: CoroutineScope,
) {
    fun create() {
        state.clearPlaylistForm()
        state.message = null
        state.destination = MediaDestination.PlaylistEditor
    }

    fun edit(playlist: MediaPlaylist) {
        state.editingPlaylist = playlist
        state.playlistName = playlist.name
        state.selectedPlaylistUris = playlist.itemUris.toSet()
        state.message = null
        state.destination = MediaDestination.PlaylistEditor
    }

    fun delete(playlist: MediaPlaylist) {
        state.playlists =
            libraryState.deletePlaylist(
                current = state.playlists,
                playlistId = playlist.id,
            )
        state.message = "Playlist removed."
    }

    fun play(playlist: MediaPlaylist) {
        val itemsSnapshot = state.localItems
        scope.launch {
            val queue =
                withContext(Dispatchers.Default) {
                    val byUri = itemsSnapshot.associateBy { item -> item.uri }
                    playlist.itemUris.mapNotNull { uri -> byUri[uri] }
                }

            if (queue.isEmpty()) {
                state.message = "This playlist has no available tracks."
            } else {
                host.playLocalQueue(queue, 0)
                state.destination = MediaDestination.NowPlaying
            }
        }
    }

    fun toggleItem(uri: String) {
        state.selectedPlaylistUris =
            state.selectedPlaylistUris
                .toMutableSet()
                .apply {
                    if (!add(uri)) {
                        remove(uri)
                    }
                }.toSet()
    }

    fun save() {
        state.playlists =
            libraryState.savePlaylist(
                current = state.playlists,
                editingId = state.editingPlaylist?.id,
                name = state.playlistName,
                itemUris = state.selectedPlaylistUris,
            )
        closeEditor(message = "Playlist saved locally.")
    }

    fun cancel() {
        closeEditor(message = null)
    }

    private fun closeEditor(message: String?) {
        state.clearPlaylistForm()
        state.libraryPivot = LibraryPivot.Playlists
        state.destination = MediaDestination.Collection
        state.message = message
    }
}

internal class MediaPodcastController(
    private val state: MediaAppState,
    private val repositories: MediaRepositories,
    private val host: MediaHostServices,
    private val scope: CoroutineScope,
) {
    fun changeFeedUrl(value: String) {
        state.podcastFeedUrl = value
        state.podcastMessage = null
    }

    fun addFeed() {
        val candidate = state.podcastFeedUrl.trim()
        when {
            state.podcastBusy -> Unit
            !PodcastRepository.isSupportedFeedUrl(candidate) -> state.podcastMessage = "Podcast feed must use HTTPS."
            else -> subscribe(candidate)
        }
    }

    fun refresh() {
        if (state.podcastBusy || state.podcastSubscriptions.isEmpty()) {
            return
        }

        val currentSubscriptions = state.podcastSubscriptions
        state.podcastBusy = true
        state.podcastRefreshing = true
        state.podcastMessage = "Refreshing subscribed feeds…"

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repositories.podcasts.refreshAll(current = currentSubscriptions)
                }
            }.onSuccess { refresh ->
                state.podcastSubscriptions = refresh.subscriptions
                state.podcastMessage = refreshMessage(refresh.failedFeeds)
            }.onFailure { error ->
                state.podcastMessage = error.messageOr("Podcast refresh failed; cached episodes were kept.")
            }
            state.podcastRefreshing = false
            state.podcastBusy = false
        }
    }

    fun remove(subscription: PodcastSubscription) {
        state.podcastSubscriptions =
            repositories.podcasts.remove(
                current = state.podcastSubscriptions,
                podcastId = subscription.id,
            )
        state.podcastMessage = "Podcast removed from this device."
    }

    fun play(episode: PodcastEpisode) {
        state.playedPodcastEpisodeIds =
            repositories.podcastState.markPlayed(
                current = state.playedPodcastEpisodeIds,
                episodeId = episode.id,
            )
        host.playUri(Uri.parse(episode.mediaUrl), episode.title, episode.podcastTitle)
        state.destination = MediaDestination.NowPlaying
    }

    fun queue(episode: PodcastEpisode) {
        val queued =
            host.playbackController.addToQueue(
                uri = episode.mediaUrl,
                title = episode.title,
                source = episode.podcastTitle,
            )
        state.podcastMessage = if (queued) "Added to Up Next." else "Player is still connecting. Try again."
    }

    fun toggleSaved(episode: PodcastEpisode) {
        state.savedPodcastEpisodeIds =
            repositories.podcastState.toggleSaved(
                current = state.savedPodcastEpisodeIds,
                episodeId = episode.id,
            )
    }

    private fun subscribe(candidate: String) {
        val currentSubscriptions = state.podcastSubscriptions
        state.podcastBusy = true
        state.podcastMessage = "Contacting podcast feed…"

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repositories.podcasts.subscribe(
                        current = currentSubscriptions,
                        feedUrl = candidate,
                    )
                }
            }.onSuccess { updated ->
                state.podcastSubscriptions = updated
                state.podcastFeedUrl = ""
                state.podcastMessage = "Podcast subscribed. Pull down to refresh feeds."
            }.onFailure { error ->
                state.podcastMessage = error.messageOr("Podcast feed could not be added.")
            }
            state.podcastBusy = false
        }
    }

    private fun refreshMessage(failedFeeds: Int): String =
        if (failedFeeds == 0) {
            "Podcasts refreshed."
        } else {
            "Podcasts refreshed with $failedFeeds feed failure(s); cached episodes were kept."
        }
}

internal class MediaStationController(
    private val state: MediaAppState,
    private val repositories: MediaRepositories,
    private val host: MediaHostServices,
) {
    fun play(station: RadioStation) {
        host.playUri(Uri.parse(station.url), station.name, "live radio")
        state.destination = MediaDestination.NowPlaying
    }

    fun toggleFavorite(station: RadioStation) {
        state.favoriteStationIds =
            repositories.libraryState.toggleFavoriteStation(
                current = state.favoriteStationIds,
                stationId = station.id,
            )
    }

    /** Opens the add or manage screen with an empty form. */
    fun openForm(destination: MediaDestination) {
        state.clearStationForm()
        state.message = null
        state.destination = destination
    }

    fun edit(station: RadioStation) {
        state.editingStation = station
        state.stationName = station.name
        state.stationUrl = station.url
        state.message = null
    }

    fun delete(station: RadioStation) {
        state.stations =
            repositories.stations.deleteStation(
                current = state.stations,
                id = station.id,
            )

        if (station.id in state.favoriteStationIds) {
            toggleFavorite(station)
        }

        if (state.editingStation?.id == station.id) {
            state.clearStationForm()
        }

        state.message = "Station removed."
    }

    fun saveNew() {
        val candidate = state.stationUrl.trim()

        if (!RadioStationRepository.isSupportedUrl(candidate)) {
            state.message = UNSUPPORTED_URL_MESSAGE
            return
        }

        state.stations =
            repositories.stations.saveStation(
                current = state.stations,
                name = state.stationName,
                url = candidate,
            )
        state.clearStationForm()
        state.message = "Station saved locally."
        state.destination = MediaDestination.Radio
    }

    fun saveEdited() {
        val editing = state.editingStation
        val candidate = state.stationUrl.trim()

        when {
            editing == null -> {
                state.message = "Choose a station to edit."
            }

            !RadioStationRepository.isSupportedUrl(candidate) -> {
                state.message = UNSUPPORTED_URL_MESSAGE
            }

            else -> {
                state.stations =
                    repositories.stations.updateStation(
                        current = state.stations,
                        id = editing.id,
                        name = state.stationName,
                        url = candidate,
                    )
                state.clearStationForm()
                state.message = "Station updated."
            }
        }
    }

    fun cancel() {
        state.clearStationForm()
        state.message = null
        if (state.destination == MediaDestination.AddStation) {
            state.destination = MediaDestination.Radio
        }
    }

    private companion object {
        const val UNSUPPORTED_URL_MESSAGE = "Station URL must use HTTP or HTTPS."
    }
}

/** FM mode actions that need Sable Media state (presets); the rest go straight to [FmController]. */
internal class MediaFmController(
    private val state: MediaAppState,
    private val repositories: MediaRepositories,
    private val host: MediaHostServices,
) {
    fun togglePower() = host.fm.togglePower(state.fmFavorites)

    fun toggleFavorite(station: Int) {
        val updated = FmPresets.toggle(state.fmFavorites, station)
        if (updated != state.fmFavorites) {
            state.fmFavorites = updated
            repositories.fmPresets.save(updated)
        }
    }

    fun run(action: FmKeyAction): Boolean {
        val fm = host.fm
        when (action) {
            is FmKeyAction.Preset -> {
                val station = FmPresets.keyed(state.fmFavorites, action.number) ?: return false
                fm.tune(station)
            }

            is FmKeyAction.Command -> {
                when (action.command) {
                    FmKeyCommand.PowerToggle -> togglePower()
                    FmKeyCommand.SeekDown -> fm.seek(false)
                    FmKeyCommand.SeekUp -> fm.seek(true)
                    FmKeyCommand.TuneDown -> fm.step(false)
                    FmKeyCommand.TuneUp -> fm.step(true)
                    FmKeyCommand.Favorite -> toggleFavorite(fm.state.station)
                    FmKeyCommand.Output -> fm.toggleSpeaker()
                    FmKeyCommand.Mute -> fm.toggleMute()
                    FmKeyCommand.Scan -> fm.toggleScan()
                }
            }
        }
        return true
    }
}

/** Turns Media keyboard commands into actions; false leaves the key to the focused view. */
internal class MediaKeyController(
    private val state: MediaAppState,
    private val host: MediaHostServices,
    private val library: MediaLibraryController,
    private val environment: MediaUiEnvironment,
    private val fm: MediaFmController,
) {
    fun handle(event: KeyEvent): Boolean {
        val press = event.toMediaKeyPress()
        val textInputActive = state.textInputFocus.active
        val onFm = state.destination == MediaDestination.Fm
        if (onFm) {
            FmKeymap.action(press, textInputActive)?.let { action ->
                if (fm.run(action)) return true
            }
        }
        val command =
            MediaKeymap
                .command(
                    press = press,
                    textInputActive = textInputActive,
                    onNowPlaying = state.destination == MediaDestination.NowPlaying,
                )?.takeIf { !onFm || it in FmKeymap.MEDIA_COMMANDS_ON_FM }
        return command != null && run(command)
    }

    fun run(command: MediaKeyCommand): Boolean = runPlaybackCommand(command) || runNavigationCommand(command)

    private fun runPlaybackCommand(command: MediaKeyCommand): Boolean {
        val controller = host.playbackController
        when (command) {
            MediaKeyCommand.PlayPause -> controller.setPlaying(!environment.playback().isPlaying)
            MediaKeyCommand.Previous -> controller.skip(PlaybackSkip.Previous)
            MediaKeyCommand.Next -> controller.skip(PlaybackSkip.Next)
            MediaKeyCommand.SeekBack -> seekBy(-MediaKeymap.SEEK_STEP_MS)
            MediaKeyCommand.SeekForward -> seekBy(MediaKeymap.SEEK_STEP_MS)
            MediaKeyCommand.VolumeUp -> host.adjustVolume(true)
            MediaKeyCommand.VolumeDown -> host.adjustVolume(false)
            else -> return false
        }
        return true
    }

    private fun runNavigationCommand(command: MediaKeyCommand): Boolean =
        when (command) {
            MediaKeyCommand.Search -> {
                state.openSearch()
                true
            }

            MediaKeyCommand.ViewMode -> {
                cycleView()
                true
            }

            MediaKeyCommand.Favorite -> {
                library.toggleFocusedFavorite()
                true
            }

            MediaKeyCommand.Delete -> {
                // Without a focused track the key is left to the focused view.
                state.pendingRemoval = state.focusedTrack
                state.pendingRemoval != null
            }

            MediaKeyCommand.Info -> {
                state.showInfo = true
                true
            }

            MediaKeyCommand.Back -> {
                val dispatcher = environment.backDispatcher()
                dispatcher?.onBackPressed()
                dispatcher != null
            }

            else -> {
                runDestinationCommand(command)
            }
        }

    private fun runDestinationCommand(command: MediaKeyCommand): Boolean {
        when (command) {
            MediaKeyCommand.Queue -> state.navigate(MediaDestination.Queue)
            MediaKeyCommand.Latest -> state.navigate(MediaDestination.NowPlaying)
            MediaKeyCommand.Albums -> state.openCollection(LibraryPivot.Albums)
            else -> return false
        }
        return true
    }

    private fun cycleView() {
        if (state.destination == MediaDestination.Collection) {
            state.libraryPivot = MediaKeymap.nextPivot(state.libraryPivot)
        } else {
            state.openCollection()
        }
    }

    private fun seekBy(stepMs: Long) {
        val playback = environment.playback()
        if (playback.durationMs > 0L) {
            host.playbackController.seekTo((playback.positionMs + stepMs).coerceIn(0L, playback.durationMs))
        }
    }
}

private fun pluralSuffix(count: Int): String = if (count == 1) "" else "s"

private fun Throwable.messageOr(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback
