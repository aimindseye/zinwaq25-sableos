package org.sableos.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.sableos.assistant.core.AskPlan
import org.sableos.assistant.core.AssistantSettings
import org.sableos.assistant.core.AssistantText.t
import org.sableos.assistant.core.ChatProtocol
import org.sableos.assistant.core.ChatReply
import org.sableos.assistant.core.ChatTurn
import org.sableos.assistant.core.Speech
import org.sableos.assistant.platform.AssistantClient
import org.sableos.assistant.platform.AssistantStore
import org.sableos.assistant.platform.CallResult
import org.sableos.assistant.platform.HoldToTalk
import org.sableos.assistant.platform.SpeechModelFiles
import org.sableos.assistant.ui.AssistantActions
import org.sableos.assistant.ui.AssistantApp
import org.sableos.assistant.ui.AssistantState
import org.sableos.design.SableGlobalTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Cosgrove, the Sable assistant: a chat with a language model on the owner's own server (Ollama)
 * or a hosted provider under their own key. He answers; he cannot act on the phone.
 */
class MainActivity : ComponentActivity() {
    private val store by lazy { AssistantStore(this) }
    private val state = AssistantState()
    private val network: ExecutorService = Executors.newSingleThreadExecutor()
    private val speechWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val speechFiles by lazy { SpeechModelFiles(this) }
    private val talk by lazy { HoldToTalk(speechFiles, speechWorker, ::heard) }
    private val askMicrophone =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) state.error = t("voice_permission")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state.settings = store.load()
        state.voiceReady = speechFiles.ready
        val actions = Actions()
        setContent {
            SableGlobalTheme(window = window) {
                AssistantApp(state, actions)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Leaving the screen lets go of the microphone at once. */
    override fun onPause() {
        talkUp()
        super.onPause()
    }

    override fun onDestroy() {
        talk.close()
        network.shutdownNow()
        speechWorker.shutdown()
        super.onDestroy()
    }

    /** Shared text waits in the message box; Command's question is asked at once. */
    private fun handleIntent(intent: Intent?) {
        val text =
            intent
                ?.getStringExtra(Intent.EXTRA_TEXT)
                ?.trim()
                ?.take(MAX_SHARED)
                ?.ifEmpty { null } ?: return
        when (intent.action) {
            ACTION_ASK -> send(text)
            Intent.ACTION_SEND -> state.draft = text
        }
    }

    /** Menu opens and closes Settings; it reaches here only when no field used it. */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event.repeatCount == 0) {
            state.showSettings = !state.showSettings
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || state.busy) return
        val turns = state.turns + ChatTurn(fromOwner = true, text = question)
        when (val plan = AskPlan.chat(state.settings, turns)) {
            is AskPlan.Blocked -> {
                state.draft = question
                state.error = t(plan.key)
            }

            is AskPlan.Send -> {
                state.turns.add(turns.last())
                state.draft = ""
                state.error = null
                state.busy = true
                val apiKey = state.settings.apiKey
                network.execute {
                    val result = AssistantClient.chat(plan.base, apiKey, plan.body)
                    runOnUiThread { answered(result) }
                }
            }
        }
    }

    private fun answered(result: CallResult) {
        state.busy = false
        val reply = if (result is CallResult.Ok) ChatProtocol.reply(result.body) else null
        if (reply is ChatReply.Text) {
            state.turns.add(ChatTurn(fromOwner = false, text = reply.text))
        } else {
            state.error = failure(result, reply)
        }
    }

    private fun test(settings: AssistantSettings) {
        if (state.testing) return
        when (val plan = AskPlan.target(settings)) {
            is AskPlan.Blocked -> {
                state.testStatus = t(plan.key)
            }

            is AskPlan.Send -> {
                state.testing = true
                state.testStatus = t("testing")
                network.execute {
                    val result = AssistantClient.models(plan.base, settings.apiKey)
                    runOnUiThread { tested(result) }
                }
            }
        }
    }

    private fun tested(result: CallResult) {
        state.testing = false
        val models = if (result is CallResult.Ok) ChatProtocol.models(result.body) else emptyList()
        state.models = models
        state.testStatus =
            when {
                models.isNotEmpty() -> t("models_found", models.size.toString())
                result is CallResult.Ok -> t("no_models")
                else -> failure(result, null)
            }
    }

    private fun save(settings: AssistantSettings) {
        state.settings = settings
        store.save(settings)
    }

    private fun talkDown() {
        when {
            state.listening || state.busy -> {
                Unit
            }

            !speechFiles.ready -> {
                state.error = t("voice_missing")
            }

            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> {
                askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                state.error = null
                state.listening = true
                talk.start()
            }
        }
    }

    private fun talkUp() {
        if (!state.listening) return
        state.listening = false
        talk.stop()
    }

    /** The words wait in the message box; the owner reads them and sends. */
    private fun heard(text: String) {
        state.listening = false
        if (text.isEmpty()) {
            state.error = t("voice_nothing")
        } else {
            state.draft = Speech.appendToDraft(state.draft, text)
        }
    }

    private fun downloadVoice() {
        if (state.voiceDownloading) return
        state.voiceDownloading = true
        state.voiceStatus = t("voice_downloading")
        speechWorker.execute {
            val ok = speechFiles.download()
            runOnUiThread {
                state.voiceDownloading = false
                state.voiceReady = ok
                state.voiceStatus = if (ok) t("voice_ready") else t("voice_failed")
            }
        }
    }

    private fun removeVoice() {
        if (state.voiceDownloading || state.listening) return
        talk.close()
        speechWorker.execute { speechFiles.remove() }
        state.voiceReady = false
        state.voiceStatus = null
    }

    private fun failure(
        result: CallResult,
        reply: ChatReply?,
    ): String =
        when (result) {
            is CallResult.Ok -> t("error_server", (reply as? ChatReply.Failed)?.message ?: "?")
            is CallResult.HttpError -> t("error_server", result.message)
            CallResult.Unreachable -> t("error_unreachable")
            CallResult.Timeout -> t("error_timeout")
        }

    /** The screens' requests, answered by the activity's own steps. */
    private inner class Actions : AssistantActions {
        override fun send(text: String) = this@MainActivity.send(text)

        override fun test(settings: AssistantSettings) = this@MainActivity.test(settings)

        override fun save(settings: AssistantSettings) = this@MainActivity.save(settings)

        override fun talkDown() = this@MainActivity.talkDown()

        override fun talkUp() = this@MainActivity.talkUp()

        override fun downloadVoice() = this@MainActivity.downloadVoice()

        override fun removeVoice() = this@MainActivity.removeVoice()
    }

    companion object {
        /** Command's /cosgrove question, in [Intent.EXTRA_TEXT]. */
        const val ACTION_ASK = "org.sableos.assistant.action.ASK"
        private const val MAX_SHARED = 4_000
    }
}
