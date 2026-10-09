package org.sableos.hub.notifications

import android.app.Notification
import android.app.Person
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.ConnectedAppsInventory
import org.sableos.hub.ConnectedAppsRepository
import org.sableos.hub.ConnectedNotificationHistoryStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SableNotificationListenerService : NotificationListenerService() {
    private val workerScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default,
        )

    private lateinit var policies: ConnectedAppsRepository
    private lateinit var history: ConnectedNotificationHistoryStore
    private lateinit var normalizer: ConnectedNotificationNormalizer
    private lateinit var userManager: UserManager
    private val generationCounter = AtomicLong(0L)
    private val notificationGenerations = ConcurrentHashMap<String, Long>()

    private val policyObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshPoliciesAndReprocess()
            }
        }

    override fun onCreate() {
        super.onCreate()
        policies = ConnectedAppsRepository(applicationContext)
        history = ConnectedNotificationHistoryStore(applicationContext)
        normalizer =
            ConnectedNotificationNormalizer(
                inventory = ConnectedAppsInventory(applicationContext),
            )
        userManager =
            checkNotNull(
                applicationContext.getSystemService(UserManager::class.java),
            )

        contentResolver.registerContentObserver(
            ConnectedAppsRepository.POLICY_URI,
            false,
            policyObserver,
        )
        workerScope.launch(Dispatchers.IO) {
            policies.loadPolicies()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshPoliciesAndReprocess()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val envelope = eligibleEnvelope(sbn)
        if (envelope != null) {
            val generation = generationCounter.incrementAndGet()
            notificationGenerations[sbn.key] = generation
            workerScope.launch {
                processEnvelope(
                    envelope = envelope,
                    generation = generation,
                )
            }
        }
    }

    private fun eligibleEnvelope(sbn: StatusBarNotification): ConnectedNotificationEnvelope? {
        val userSerial = userManager.getSerialNumberForUser(sbn.user)
        val key =
            userSerial
                .takeIf { it >= 0L }
                ?.let { serial ->
                    ConnectedAppKey(
                        packageName = sbn.packageName,
                        userSerial = serial,
                    )
                }
        val policy =
            key?.let(policies::cachedPolicyFor)
        val envelope =
            if (key != null && policy?.includeInMessages == true) {
                captureEnvelope(
                    sbn = sbn,
                    key = key,
                    policy = policy,
                ).takeIf { candidate ->
                    candidate.isConversationCandidate()
                }
            } else {
                null
            }

        if (envelope == null) {
            clearNotificationState(sbn.key)
        }
        return envelope
    }

    private fun clearNotificationState(notificationKey: String) {
        ConnectedReplyRegistry.remove(notificationKey)
        ConnectedNotificationActivityRegistry.remove(notificationKey)
    }

    private fun ConnectedNotificationEnvelope.isConversationCandidate(): Boolean =
        !isGroupSummary &&
            (
                messages.isNotEmpty() ||
                    replyCandidate != null ||
                    shortcutId != null ||
                    conversationTitle != null ||
                    category == Notification.CATEGORY_MESSAGE
            )

    private fun captureEnvelope(
        sbn: StatusBarNotification,
        key: ConnectedAppKey,
        policy: ConnectedAppPolicy,
    ): ConnectedNotificationEnvelope {
        val notification = sbn.notification
        val messagingUser = messagingUser(notification)
        return ConnectedNotificationEnvelope(
            key = key,
            notificationKey = sbn.key,
            notificationId = sbn.id,
            notificationTag =
                boundedText(
                    sbn.tag,
                    MAX_NOTIFICATION_ID_LENGTH,
                ),
            shortcutId =
                boundedText(
                    notification.shortcutId,
                    MAX_NOTIFICATION_ID_LENGTH,
                ),
            postTimeMillis = sbn.postTime,
            notificationTitle =
                boundedText(
                    notification.extras
                        .getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString(),
                    MAX_NOTIFICATION_LABEL_LENGTH,
                ),
            notificationBody = captureNotificationBody(notification),
            conversationTitle =
                boundedText(
                    notification.extras
                        .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                        ?.toString(),
                    MAX_NOTIFICATION_LABEL_LENGTH,
                ),
            category =
                boundedText(
                    notification.category,
                    MAX_NOTIFICATION_LABEL_LENGTH,
                ),
            isGroupSummary =
                notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            messages =
                captureMessages(
                    notification = notification,
                    fallbackTimestampMillis = sbn.postTime,
                    messagingUser = messagingUser,
                ),
            replyCandidate = captureReplyCandidate(notification),
            allowQuickReply = policy.allowQuickReply,
        )
    }

    private fun captureNotificationBody(notification: Notification): String? =
        boundedText(
            notification.extras
                .getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.toString(),
            MAX_NOTIFICATION_BODY_LENGTH,
        ) ?: boundedText(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString(),
            MAX_NOTIFICATION_BODY_LENGTH,
        )

    @Suppress("DEPRECATION")
    private fun captureMessages(
        notification: Notification,
        fallbackTimestampMillis: Long,
        messagingUser: Person?,
    ): List<ConnectedNotificationMessageSnapshot> {
        val bundles =
            notification.extras
                .getParcelableArray(
                    Notification.EXTRA_MESSAGES,
                )?.takeLast(MAX_NOTIFICATION_MESSAGES)
                ?.toTypedArray()
        return Notification.MessagingStyle.Message
            .getMessagesFromBundleArray(bundles)
            .map { message ->
                val sender = message.senderPerson
                val incoming =
                    sender != null &&
                        (
                            messagingUser == null ||
                                !samePerson(sender, messagingUser)
                        )
                ConnectedNotificationMessageSnapshot(
                    senderLabel =
                        if (incoming) {
                            boundedText(
                                sender.name?.toString(),
                                MAX_NOTIFICATION_LABEL_LENGTH,
                            )
                        } else {
                            null
                        },
                    body =
                        boundedText(
                            message.text
                                ?.toString(),
                            MAX_NOTIFICATION_BODY_LENGTH,
                        ),
                    timestampMillis =
                        message.timestamp
                            .takeIf { it > 0L }
                            ?: fallbackTimestampMillis,
                    incoming = incoming,
                )
            }
    }

    @Suppress("DEPRECATION")
    private fun messagingUser(notification: Notification): Person? {
        val extras = notification.extras
        return extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON)
    }

    private fun samePerson(
        first: Person,
        second: Person,
    ): Boolean {
        val firstKey = first.key?.takeIf(String::isNotBlank)
        val secondKey = second.key?.takeIf(String::isNotBlank)
        val firstUri = first.uri?.takeIf(String::isNotBlank)
        val secondUri = second.uri?.takeIf(String::isNotBlank)
        val firstName = first.name?.toString()?.trim()
        val secondName = second.name?.toString()?.trim()

        return when {
            firstKey != null && secondKey != null -> {
                firstKey == secondKey
            }

            firstUri != null && secondUri != null -> {
                firstUri == secondUri
            }

            else -> {
                !firstName.isNullOrEmpty() &&
                    !secondName.isNullOrEmpty() &&
                    firstName.equals(secondName, ignoreCase = true)
            }
        }
    }

    private fun captureReplyCandidate(notification: Notification): ConnectedNotificationReplyCandidate? {
        val candidates =
            notification.actions
                .orEmpty()
                .asSequence()
                .take(MAX_NOTIFICATION_ACTIONS)
                .mapNotNull { action ->
                    val pendingIntent = action.actionIntent
                    val remoteInput =
                        action.remoteInputs
                            ?.asSequence()
                            ?.take(MAX_NOTIFICATION_REMOTE_INPUTS)
                            ?.firstOrNull { input ->
                                input.allowFreeFormInput
                            }
                    if (pendingIntent == null || remoteInput == null) {
                        null
                    } else {
                        CapturedReplyCandidate(
                            candidate =
                                ConnectedNotificationReplyCandidate(
                                    pendingIntent = pendingIntent,
                                    remoteInput = remoteInput,
                                ),
                            semanticAction = action.semanticAction,
                        )
                    }
                }.toList()

        return candidates
            .firstOrNull { candidate ->
                candidate.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
            }?.candidate
            ?: candidates.firstOrNull()?.candidate
    }

    private suspend fun processEnvelope(
        envelope: ConnectedNotificationEnvelope,
        generation: Long,
    ) {
        val latestPolicy = policies.cachedPolicyFor(envelope.key)?.normalized()
        if (latestPolicy?.includeInMessages != true) {
            ConnectedReplyRegistry.remove(envelope.notificationKey)
            return
        }

        val normalized =
            normalizer.normalize(
                envelope.copy(
                    allowQuickReply = latestPolicy.allowQuickReply,
                ),
            )

        if (notificationGenerations[envelope.notificationKey] != generation) {
            return
        }

        ConnectedReplyRegistry.remove(envelope.notificationKey)
        ConnectedReplyRegistry.register(normalized.replyHandle)
        normalized.records
            .firstOrNull()
            ?.let { record ->
                ConnectedNotificationActivityRegistry.upsert(
                    notificationKey = envelope.notificationKey,
                    sourceKey = record.key,
                    conversationId = record.conversationId,
                )
            }

        val policySnapshot = policies.cachedPolicySnapshot()
        withContext(Dispatchers.IO) {
            history.merge(
                records = normalized.records,
                policies = policySnapshot,
            )
        }
    }

    private fun refreshPoliciesAndReprocess() {
        workerScope.launch(Dispatchers.IO) {
            policies.loadPolicies()
            withContext(Dispatchers.Main.immediate) {
                activeNotifications
                    .orEmpty()
                    .forEach(::onNotificationPosted)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        notificationGenerations.remove(sbn.key)
        ConnectedReplyRegistry.remove(sbn.key)
        ConnectedNotificationActivityRegistry.remove(sbn.key)
        notifyHubDataChanged()
    }

    override fun onListenerDisconnected() {
        ConnectedNotificationActivityRegistry.clear()
        notifyHubDataChanged()
        super.onListenerDisconnected()
    }

    private fun notifyHubDataChanged() {
        applicationContext.contentResolver.notifyChange(
            ConnectedAppsRepository.HISTORY_URI,
            null,
        )
        applicationContext.contentResolver.notifyChange(
            org.sableos.hub.HubSnapshotProvider.SNAPSHOT_URI,
            null,
        )
    }

    override fun onDestroy() {
        runCatching {
            contentResolver.unregisterContentObserver(policyObserver)
        }
        workerScope.cancel()
        super.onDestroy()
    }

    private fun boundedText(
        value: String?,
        maxLength: Int,
    ): String? =
        value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(maxLength)

    private data class CapturedReplyCandidate(
        val candidate: ConnectedNotificationReplyCandidate,
        val semanticAction: Int,
    )
}
