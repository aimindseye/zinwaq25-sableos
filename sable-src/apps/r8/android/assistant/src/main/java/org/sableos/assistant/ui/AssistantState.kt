package org.sableos.assistant.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.sableos.assistant.core.AssistantSettings
import org.sableos.assistant.core.ButlerAction
import org.sableos.assistant.core.ChatTurn
import org.sableos.assistant.platform.Recipient

/** A text waiting for the owner on the confirm card: nothing is sent until they press Send. */
data class PendingSms(
    val action: ButlerAction.SendSms,
    val choices: List<Recipient>,
    val chosen: Int = 0,
) {
    val recipient: Recipient get() = choices[chosen.coerceIn(0, choices.lastIndex)]
}

/** The screen's state. The conversation lives here only: never written to storage. */
class AssistantState {
    var settings by mutableStateOf(AssistantSettings())
    val turns = mutableStateListOf<ChatTurn>()
    var draft by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(false)
    var testing by mutableStateOf(false)
    var testStatus by mutableStateOf<String?>(null)
    var models by mutableStateOf<List<String>>(emptyList())
    var listening by mutableStateOf(false)
    var voiceReady by mutableStateOf(false)
    var voiceDownloading by mutableStateOf(false)
    var voiceStatus by mutableStateOf<String?>(null)
    var pendingSms by mutableStateOf<PendingSms?>(null)

    fun newChat() {
        turns.clear()
        error = null
        pendingSms = null
    }
}
