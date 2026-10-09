package org.vaachak.reader.leisure.domain.tts

import android.content.Context
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Decoration.Style
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.vaachak.reader.core.domain.tts.TtsConfig
import org.vaachak.reader.core.domain.tts.TtsManager
import org.vaachak.reader.core.domain.tts.TtsState
import timber.log.Timber
import java.util.Locale

/**
 * Native TTS Manager compatible with Readium 3.1.2
 * Uses android.speech.tts.TextToSpeech to fix "Jump to Start" bugs.
 */
@OptIn(ExperimentalReadiumApi::class)
class AndroidTtsManager(
    context: Context,
    private val publication: Publication,
    private val visualNavigatorProvider: () -> DecorableNavigator?,
    private val isEinkProvider: () -> Boolean
) : TtsManager, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    private val _state = MutableStateFlow(TtsState.IDLE)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _config = MutableStateFlow(TtsConfig())
    override val config: StateFlow<TtsConfig> = _config.asStateFlow()

    // FIX: Use the specific Readium Content.Iterator type
    private var currentContentIterator: Content.Iterator? = null

    private var speechJob: Job? = null

    init {
        // Initialize Native Android TTS
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            setupUtteranceListener()
            _state.value = TtsState.IDLE
        } else {
            Timber.e("Native TTS Init Failed")
            _state.value = TtsState.ERROR
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = TtsState.PLAYING
            }

            override fun onDone(utteranceId: String?) {
                // Auto-play next sentence
                scope.launch { playNextSentence() }
            }

            override fun onError(utteranceId: String?) {
                // Skip error and try next
                scope.launch { playNextSentence() }
            }
        })
    }

    override fun play(locator: Locator?) {
        // Stop current speech
        speechJob?.cancel()
        tts?.stop()

        speechJob = scope.launch {
            if (locator != null) {
                // 1. Get Content starting at this Locator
                val content = publication.content(locator)
                if (content != null) {
                    currentContentIterator = content.iterator()
                    playNextSentence()
                }
            } else {
                // 2. Resume or Start from Beginning
                if (currentContentIterator != null && _state.value == TtsState.PAUSED) {
                    playNextSentence() // Resume existing iterator
                } else if (currentContentIterator == null) {
                    // Fallback: Start from the very beginning
                    val content = publication.content()
                    if (content != null) {
                        currentContentIterator = content.iterator()
                        playNextSentence()
                    }
                }
            }
        }
    }

    private suspend fun playNextSentence() {
        val iterator = currentContentIterator ?: return

        // Check if we have more content
        if (iterator.hasNext()) {
            // FIX: In 3.1.2, iterator returns 'Content.Element'
            val element = iterator.next()

            // FIX: Extract text safely from the Locator
            val text = element.locator.text.highlight
            val locator = element.locator

            if (!text.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    val visualNav = visualNavigatorProvider()

                    // 1. Move Screen (No Animation = No Jumping)
                    (visualNav as? Navigator)?.go(locator, animated = false)

                    // 2. Highlight for E-ink
                    highlightLocator(locator)
                }

                // 3. Speak
                // QUEUE_FLUSH overrides any previous speech immediately
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
            } else {
                // Recursive call to skip empty elements (images, breaks)
                playNextSentence()
            }
        } else {
            // End of book/chapter
            _state.value = TtsState.IDLE
            stop()
        }
    }

    private fun highlightLocator(locator: Locator) {
        val visualNav = visualNavigatorProvider() ?: return
        val isEink = isEinkProvider()

        val style = if (isEink) {
            // OPTIMIZED E-INK: Solid Black Underline
            // Visible during A2 fast refresh
            Style.Underline(tint = Color.BLACK, isActive = true)
        } else {
            // LCD: Standard Yellow Box
            Style.Highlight(tint = ColorUtils.setAlphaComponent(Color.YELLOW, 75))
        }

        val decoration = Decoration(id = "tts-highlight", locator = locator, style = style)

        // Ensure UI updates happen on Main thread
        scope.launch(Dispatchers.Main) {
            visualNav.applyDecorations(listOf(decoration), "tts_group")
        }
    }

    override fun pause() {
        tts?.stop()
        _state.value = TtsState.PAUSED
    }

    override fun stop() {
        tts?.stop()
        _state.value = TtsState.IDLE
        scope.launch(Dispatchers.Main) {
            visualNavigatorProvider()?.applyDecorations(emptyList(), "tts_group")
        }
    }

    override fun setRate(rate: Double) {
        tts?.setSpeechRate(rate.toFloat())
        _config.value = _config.value.copy(rate = rate)
    }

    override fun setPitch(pitch: Double) {
        tts?.setPitch(pitch.toFloat())
        _config.value = _config.value.copy(pitch = pitch)
    }

    override fun close() {
        tts?.stop()
        tts?.shutdown()
    }

    override fun previous() { /* Can implement history stack later */ }

    override fun next() {
        tts?.stop()
        scope.launch { playNextSentence() }
    }
}