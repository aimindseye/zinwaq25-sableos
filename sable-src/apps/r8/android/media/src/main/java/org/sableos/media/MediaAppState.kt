package org.sableos.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The Sable Media UI state, held in Compose snapshot state so the controllers can change it outside composition. */
internal class MediaAppState(
    repositories: MediaRepositories,
    initialSettings: MediaSettings,
) {
    var settings by mutableStateOf(initialSettings)
    var destination by mutableStateOf(initialSettings.startPage.toDestination())
    var libraryPivot by mutableStateOf(LibraryPivot.Songs)
    var localItems by mutableStateOf(emptyList<LocalAudioItem>())
    var localLibraryLoading by mutableStateOf(true)

    var podcastSubscriptions by mutableStateOf(repositories.podcasts.loadSubscriptions())
    var savedPodcastEpisodeIds by mutableStateOf(repositories.podcastState.loadSavedEpisodeIds())
    var playedPodcastEpisodeIds by mutableStateOf(repositories.podcastState.loadPlayedEpisodeIds())
    var podcastFeedUrl by mutableStateOf("")
    var podcastBusy by mutableStateOf(false)
    var podcastRefreshing by mutableStateOf(false)
    var podcastMessage by mutableStateOf<String?>(null)

    var stations by mutableStateOf(repositories.stations.loadStations())
    var fmFavorites by mutableStateOf(repositories.fmPresets.load())
    var favoriteAudioUris by mutableStateOf(repositories.libraryState.loadFavoriteAudioUris())
    var favoriteStationIds by mutableStateOf(repositories.libraryState.loadFavoriteStationIds())
    var playlists by mutableStateOf(emptyList<MediaPlaylist>())
    var showFavoriteStationsOnly by mutableStateOf(false)

    var editingStation by mutableStateOf<RadioStation?>(null)
    var stationName by mutableStateOf("")
    var stationUrl by mutableStateOf("")

    var editingPlaylist by mutableStateOf<MediaPlaylist?>(null)
    var playlistName by mutableStateOf("")
    var selectedPlaylistUris by mutableStateOf(emptySet<String>())

    var message by mutableStateOf<String?>(null)

    /** Library search: null while closed, the typed query while open. */
    var searchQuery by mutableStateOf<String?>(null)
    var searchFocusToken by mutableIntStateOf(0)
    var focusedTrack by mutableStateOf<LocalAudioItem?>(null)
    var pendingRemoval by mutableStateOf<LocalAudioItem?>(null)
    var showInfo by mutableStateOf(false)

    val textInputFocus = MediaTextInputFocus()

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

    fun navigate(next: MediaDestination) {
        destination = next
        message = null

        if (next != MediaDestination.ManageStations && next != MediaDestination.AddStation) {
            clearStationForm()
        }

        if (next != MediaDestination.PlaylistEditor) {
            clearPlaylistForm()
        }

        if (next != MediaDestination.Collection) {
            searchQuery = null
            focusedTrack = null
        }
    }

    fun openCollection(pivot: LibraryPivot? = null) {
        if (destination != MediaDestination.Collection) navigate(MediaDestination.Collection)
        if (pivot != null) libraryPivot = pivot
    }

    fun openSearch() {
        openCollection()
        if (searchQuery == null) searchQuery = ""
        searchFocusToken += 1
    }

    fun changeTrackFocus(
        item: LocalAudioItem,
        focused: Boolean,
    ) {
        if (focused) {
            focusedTrack = item
        } else if (focusedTrack?.uri == item.uri) {
            focusedTrack = null
        }
    }

    /** One logical layer back from here, or null when Back leaves the app. */
    fun backStep(): MediaBackStep? = MediaKeymap.backStep(destination, searchQuery != null)

    fun goBack() {
        when (val step = backStep()) {
            MediaBackStep.CloseSearch -> searchQuery = null
            is MediaBackStep.Navigate -> navigate(step.destination)
            null -> Unit
        }
    }

    fun screenState(
        playback: PlaybackUiState,
        fm: FmUiState,
    ): MediaScreenState =
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
            playback = playback,
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
            searchQuery = searchQuery,
            searchFocusToken = searchFocusToken,
            fm = fm,
            fmFavorites = fmFavorites,
        )
}

private fun MediaStartPage.toDestination(): MediaDestination =
    when (this) {
        MediaStartPage.Collection -> MediaDestination.Collection
        MediaStartPage.Radio -> MediaDestination.Radio
    }
