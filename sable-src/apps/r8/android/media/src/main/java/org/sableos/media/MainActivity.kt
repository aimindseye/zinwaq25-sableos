package org.sableos.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var playbackState by mutableStateOf(PlaybackUiState())
    private lateinit var playbackController: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playbackController =
            PlaybackController(applicationContext) { state ->
                playbackState = state
            }

        val repositories =
            MediaRepositories(
                localAudio = LocalAudioRepository(applicationContext),
                audioFolderScanner = AudioFolderScanner(applicationContext),
                stations = RadioStationRepository(applicationContext),
                libraryState = MediaLibraryStateRepository(applicationContext),
                podcasts = PodcastRepository(applicationContext),
                podcastState = PodcastStateRepository(applicationContext),
                settings = MediaSettingsRepository(applicationContext),
            )

        setContent {
            MediaApp(
                repositories = repositories,
                host =
                    MediaHostServices(
                        contentResolver = contentResolver,
                        window = window,
                        playbackController = playbackController,
                        playUri = ::playUri,
                        playLocalQueue = ::playLocalQueue,
                    ),
                playbackState = playbackState,
            )
        }
    }

    override fun onDestroy() {
        playbackController.close()
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        super.onDestroy()
    }

    private fun playUri(
        uri: Uri,
        title: String,
        source: String,
    ) {
        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_PLAY_URI)
                .setData(uri)
                .putExtra(PlaybackService.EXTRA_TITLE, title)
                .putExtra(PlaybackService.EXTRA_SOURCE, source),
        )
    }

    private fun playLocalQueue(
        items: List<LocalAudioItem>,
        startIndex: Int,
    ) {
        if (items.isEmpty()) {
            return
        }

        val requestToken =
            PlaybackQueueRequestStore.publish(
                items = items,
                startIndex = startIndex,
            )

        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_PLAY_QUEUE)
                .putExtra(
                    PlaybackService.EXTRA_QUEUE_TOKEN,
                    requestToken,
                ),
        )
    }
}
