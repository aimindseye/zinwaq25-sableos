package org.sableos.messages.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.sableos.messages.core.ConversationMessage
import org.sableos.messages.core.ThreadSummary
import org.sableos.messages.data.ContactNumber

/** What the app may do on this device right now. */
data class Access(
    /** False until the first check, so no permission screen flashes on start. */
    val checked: Boolean = false,
    val hasTelephony: Boolean = true,
    val canRead: Boolean = false,
    val canSend: Boolean = false,
    val canReadContacts: Boolean = false,
    val isDefault: Boolean = false,
    val roleAvailable: Boolean = false,
    /** The user was asked and Android will not ask again: only Settings can grant now. */
    val deniedForGood: Boolean = false,
) {
    val canUseSms: Boolean get() = canRead && canSend
}

sealed interface Screen {
    data object Threads : Screen

    data class Conversation(
        val threadId: Long,
        val addresses: List<String>,
        val title: String,
    ) : Screen {
        /** Replies in a group thread would need MMS, which Sable Messages does not send. */
        val canReply: Boolean get() = addresses.size == 1
    }

    data class Compose(
        val recipients: String = "",
        val body: String = "",
    ) : Screen
}

class MessagesUiState {
    var access by mutableStateOf(Access())
    var screen: Screen by mutableStateOf(Screen.Threads)
    var threads by mutableStateOf(emptyList<ThreadSummary>())
    var threadsLoaded by mutableStateOf(false)
    var filter by mutableStateOf("")
    var conversation by mutableStateOf(emptyList<ConversationMessage>())
    var conversationLoaded by mutableStateOf(false)

    /** Contact names of the conversation's senders, for group threads. */
    var senderNames by mutableStateOf(emptyMap<String, String>())
    var draft by mutableStateOf("")
    var notice by mutableStateOf<String?>(null)
    var suggestions by mutableStateOf(emptyList<ContactNumber>())
}

/** Everything the screens can ask for; the controller implements it. */
data class MessagesActions(
    val openThread: (ThreadSummary) -> Unit,
    val newMessage: () -> Unit,
    val back: () -> Unit,
    val setFilter: (String) -> Unit,
    val setDraft: (String) -> Unit,
    val send: () -> Unit,
    val sendNew: (recipients: String, body: String) -> Unit,
    val retry: (ConversationMessage) -> Unit,
    val searchContacts: (String) -> Unit,
    val requestPermissions: () -> Unit,
    val requestDefaultRole: () -> Unit,
    val openAppSettings: () -> Unit,
    val dismissNotice: () -> Unit,
    /** Decodes an MMS image part, scaled to at most the given size. Blocking: call off main. */
    val loadImage: (partId: Long, maxPixels: Int) -> Bitmap?,
)
