package org.sableos.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.sableos.messages.core.Addresses
import org.sableos.messages.core.ConversationMessage
import org.sableos.messages.core.DeliveryState
import org.sableos.messages.core.MessageKind
import org.sableos.messages.core.SmsLinks
import org.sableos.messages.core.ThreadGrouping
import org.sableos.messages.core.ThreadSummary
import org.sableos.messages.data.Background
import org.sableos.messages.data.MessagesRepository
import org.sableos.messages.data.SmsSender
import org.sableos.messages.data.isDefaultSmsApp
import org.sableos.messages.receive.ForegroundThread
import org.sableos.messages.receive.MessageNotifier
import org.sableos.messages.receive.MmsTransfer
import org.sableos.messages.ui.Access
import org.sableos.messages.ui.MessagesActions
import org.sableos.messages.ui.MessagesUiState
import org.sableos.messages.ui.Screen

/** State and behaviour behind the screens; provider work runs on [Background]. */
class MessagesController(
    private val activity: ComponentActivity,
) {
    val state = MessagesUiState()
    private val repository = MessagesRepository(activity)
    private val sender = SmsSender(activity)
    private val drafts = mutableMapOf<Long, String>()
    private var resumed = false
    private var permissionsAsked = false

    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsAsked = true
            repository.contacts.clear()
            refresh()
        }

    private val roleLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh()
        }

    val actions =
        MessagesActions(
            openThread = ::openThread,
            newMessage = { show(Screen.Compose()) },
            back = ::back,
            setFilter = { state.filter = it },
            setDraft = ::setDraft,
            send = ::sendDraft,
            sendNew = ::sendNew,
            retry = ::retry,
            searchContacts = ::searchContacts,
            requestPermissions = ::requestPermissions,
            requestDefaultRole = ::requestDefaultRole,
            openAppSettings = ::openAppSettings,
            dismissNotice = { state.notice = null },
            loadImage = repository::attachmentBitmap,
        )

    val visibleThreads: List<ThreadSummary> get() = ThreadGrouping.filter(state.threads, state.filter)

    fun onResume() {
        resumed = true
        (state.screen as? Screen.Conversation)?.let { ForegroundThread.threadId = it.threadId }
        refresh()
    }

    fun onPause() {
        resumed = false
        ForegroundThread.threadId = ForegroundThread.NONE
    }

    /** Re-reads permissions and the role, then the visible data. */
    fun refresh() {
        state.access = readAccess()
        reload()
    }

    fun reload() {
        if (!state.access.canRead) return
        Background.load(repository::threads) { threads ->
            state.threads = threads
            state.threadsLoaded = true
        }
        (state.screen as? Screen.Conversation)?.let { loadConversation(it) }
    }

    /** Coalesces provider change bursts (a multipart SMS, an MMS with parts) into one reload. */
    fun scheduleReload() = Background.postDelayed(this, RELOAD_DELAY_MILLIS) { reload() }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.action == MainActivity.ACTION_OPEN_THREAD -> {
                openThreadById(intent.getLongExtra(MainActivity.EXTRA_THREAD_ID, ForegroundThread.NONE))
            }

            SmsLinks.isMessagingScheme(intent.data?.scheme) || intent.action == Intent.ACTION_SEND -> {
                val request =
                    SmsLinks.parse(
                        intent.data?.schemeSpecificPart,
                        intent.getStringExtra(EXTRA_SMS_BODY),
                        intent.getStringExtra(Intent.EXTRA_TEXT),
                    )
                openCompose(request.recipients, request.body)
            }
        }
    }

    private fun readAccess(): Access {
        val pm = activity.packageManager
        val telephony =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
            } else {
                pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
            }
        val roles = activity.getSystemService(RoleManager::class.java)
        val canRead = granted(Manifest.permission.READ_SMS)
        val canSend = granted(Manifest.permission.SEND_SMS)
        val deniedForGood =
            permissionsAsked &&
                REQUIRED.any { !granted(it) && !activity.shouldShowRequestPermissionRationale(it) }
        return Access(
            checked = true,
            hasTelephony = telephony,
            canRead = canRead,
            canSend = canSend,
            canReadContacts = granted(Manifest.permission.READ_CONTACTS),
            isDefault = activity.isDefaultSmsApp(),
            roleAvailable = roles?.isRoleAvailable(RoleManager.ROLE_SMS) == true,
            deniedForGood = deniedForGood,
        )
    }

    private fun granted(permission: String) =
        activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun show(screen: Screen) {
        state.screen = screen
        state.notice = null
        val conversation = screen as? Screen.Conversation
        ForegroundThread.threadId =
            if (resumed && conversation != null) conversation.threadId else ForegroundThread.NONE
        if (conversation == null) {
            state.conversation = emptyList()
            state.conversationLoaded = false
        }
    }

    private fun openThread(thread: ThreadSummary) {
        openConversation(Screen.Conversation(thread.threadId, thread.addresses, thread.title))
    }

    private fun openConversation(screen: Screen.Conversation) {
        val previous = state.screen as? Screen.Conversation
        if (previous?.threadId != screen.threadId) {
            state.conversation = emptyList()
            state.conversationLoaded = false
        }
        state.draft = drafts[screen.threadId].orEmpty()
        show(screen)
        loadConversation(screen)
        MessageNotifier.cancel(activity, screen.threadId)
    }

    private fun loadConversation(screen: Screen.Conversation) {
        Background.load({
            val messages = repository.conversation(screen.threadId)
            val names =
                messages
                    .map { it.address }
                    .distinct()
                    .mapNotNull { address -> repository.contacts.nameFor(address)?.let { address to it } }
                    .toMap()
            if (resumed && messages.any { !it.read }) repository.markRead(screen.threadId)
            messages to names
        }) { (messages, names) ->
            if ((state.screen as? Screen.Conversation)?.threadId == screen.threadId) {
                state.conversation = messages
                state.senderNames = names
                state.conversationLoaded = true
            }
        }
    }

    private fun openThreadById(threadId: Long) {
        if (threadId == ForegroundThread.NONE || !state.access.canRead) return
        Background.load(repository::threads) { threads ->
            state.threads = threads
            state.threadsLoaded = true
            val thread = threads.firstOrNull { it.threadId == threadId }
            openConversation(
                Screen.Conversation(
                    threadId,
                    thread?.addresses.orEmpty(),
                    thread?.title ?: activity.getString(R.string.conversation),
                ),
            )
        }
    }

    /** A link or share: one recipient opens their thread with the text as draft. */
    private fun openCompose(
        recipients: List<String>,
        body: String,
    ) {
        if (recipients.size != 1 || !state.access.canRead) {
            show(Screen.Compose(recipients.joinToString(", "), body))
            return
        }
        Background.load({
            val threadId = repository.threadIdFor(recipients)
            Triple(threadId, repository.threads(), repository.contacts.nameFor(recipients.single()))
        }) { (threadId, threads, name) ->
            state.threads = threads
            if (body.isNotBlank()) drafts[threadId] = body
            val title = name ?: recipients.single()
            openConversation(Screen.Conversation(threadId, recipients, title))
        }
    }

    private fun back() {
        when (state.screen) {
            Screen.Threads -> activity.finish()
            else -> show(Screen.Threads)
        }
        reload()
    }

    private fun setDraft(text: String) {
        state.draft = text
        (state.screen as? Screen.Conversation)?.let { drafts[it.threadId] = text }
    }

    private fun sendDraft() {
        val screen = state.screen as? Screen.Conversation ?: return
        val body = state.draft
        if (body.isBlank() || !screen.canReply) return
        setDraft("")
        val address = screen.addresses.single()
        Background.load({ runCatching { sender.send(address, body) } }) { result ->
            result.onFailure {
                setDraft(body)
                state.notice = activity.getString(R.string.send_failed, it.message.orEmpty())
            }
            reload()
        }
    }

    private fun sendNew(
        recipientsText: String,
        body: String,
    ) {
        val recipients = Addresses.splitRecipients(recipientsText).filter(Addresses::isSendable)
        if (recipients.isEmpty()) {
            state.notice = activity.getString(R.string.recipient_invalid)
            return
        }
        if (body.isBlank()) return
        Background.load({ runCatching { recipients.map { sender.send(it, body) } } }) { result ->
            result
                .onSuccess { threadIds ->
                    if (threadIds.size == 1) {
                        val name = state.threads.firstOrNull { it.threadId == threadIds.single() }?.title
                        openConversation(
                            Screen.Conversation(threadIds.single(), recipients, name ?: recipients.single()),
                        )
                    } else {
                        show(Screen.Threads)
                        state.notice =
                            activity.resources.getQuantityString(
                                R.plurals.sent_to_many,
                                recipients.size,
                                recipients.size,
                            )
                    }
                    reload()
                }.onFailure { state.notice = activity.getString(R.string.send_failed, it.message.orEmpty()) }
        }
    }

    private fun retry(message: ConversationMessage) {
        when {
            message.kind == MessageKind.Sms && message.state == DeliveryState.Failed -> {
                Background.load({ runCatching { sender.retry(message.id, message.address, message.body) } }) { result ->
                    result.onFailure { state.notice = activity.getString(R.string.send_failed, it.message.orEmpty()) }
                    reload()
                }
            }

            message.kind == MessageKind.MmsNotification && message.state == DeliveryState.DownloadFailed -> {
                if (!state.access.isDefault) {
                    state.notice = activity.getString(R.string.mms_needs_default)
                    return
                }
                Background.run { MmsTransfer.retry(activity.applicationContext, message.id) }
            }
        }
    }

    private fun searchContacts(query: String) {
        Background.load({ repository.contacts.search(query) }) { state.suggestions = it }
    }

    private fun requestPermissions() {
        val permissions =
            buildList {
                addAll(REQUIRED)
                add(Manifest.permission.RECEIVE_SMS)
                add(Manifest.permission.RECEIVE_MMS)
                add(Manifest.permission.READ_CONTACTS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestDefaultRole() {
        val roles = activity.getSystemService(RoleManager::class.java) ?: return
        runCatching { roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS)) }
            .onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
        runCatching { activity.startActivity(intent) }
    }

    private companion object {
        const val RELOAD_DELAY_MILLIS = 250L
        const val EXTRA_SMS_BODY = "sms_body"
        val REQUIRED = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
    }
}
