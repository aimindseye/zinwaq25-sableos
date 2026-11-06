package org.sableos.assistant.platform

import android.os.Handler
import android.os.Looper
import org.sableos.assistant.core.Speech
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException
import java.util.concurrent.ExecutorService

/**
 * The microphone is open only between [start] and [stop], while the owner holds the key or the
 * Talk button. Sound goes to the recognizer on this phone and nowhere else; nothing is recorded.
 * [onHeard] gets the words (empty when nothing was understood) on the main thread.
 */
class HoldToTalk(
    private val files: SpeechModelFiles,
    private val worker: ExecutorService,
    private val onHeard: (String) -> Unit,
) : RecognitionListener {
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private val parts = mutableListOf<String>()
    private var holding = false

    /** From [start] until [onHeard] is called, once. */
    private var active = false

    /** Call on the main thread once the model is downloaded and the microphone allowed. */
    fun start() {
        if (holding) return
        holding = true
        active = true
        parts.clear()
        worker.execute {
            val loaded = loadModel()
            main.post { if (loaded == null) finish() else listen(loaded) }
        }
    }

    fun stop() {
        if (!holding) return
        holding = false
        // The recognizer thread then sends what it heard last to onFinalResult.
        if (service?.stop() != true) finish()
    }

    /** Leaves the microphone and frees the model, for when the screen goes away. */
    fun close() {
        holding = false
        active = false
        service?.cancel()
        release()
        worker.execute {
            model?.close()
            model = null
        }
    }

    override fun onPartialResult(hypothesis: String) = Unit

    override fun onResult(hypothesis: String) {
        parts += Speech.textOf(hypothesis)
    }

    override fun onFinalResult(hypothesis: String) {
        parts += Speech.textOf(hypothesis)
        finish()
    }

    override fun onError(exception: Exception) = finish()

    override fun onTimeout() = finish()

    private fun loadModel(): Model? =
        model ?: try {
            Model(files.dir.path).also { model = it }
        } catch (_: IOException) {
            null
        }

    private fun listen(loaded: Model) {
        // Let go before the model was ready: nothing to hear.
        if (!holding) return finish()
        try {
            val newRecognizer = Recognizer(loaded, Speech.SAMPLE_RATE)
            recognizer = newRecognizer
            service = SpeechService(newRecognizer, Speech.SAMPLE_RATE).also { it.startListening(this) }
        } catch (_: IOException) {
            holding = false
            finish()
        }
    }

    private fun finish() {
        if (!active) return
        active = false
        release()
        onHeard(Speech.join(parts))
        parts.clear()
    }

    private fun release() {
        service?.shutdown()
        service = null
        recognizer?.close()
        recognizer = null
    }
}
