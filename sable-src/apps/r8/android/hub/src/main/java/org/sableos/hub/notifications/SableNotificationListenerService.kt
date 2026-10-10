package org.sableos.hub.notifications

import android.app.Notification
import android.app.Person
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
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
import org.sableos.hub.policy.AndroidVisibility
import org.sableos.hub.policy.ReplyActionFacts
import org.sableos.hub.policy.ReplyEligibility
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Hub's NotificationListenerService. It only reads what Android delivers: it never cancels,
 * snoozes, re-posts or re-ranks notifications, never changes interruption filters or channels,
 * and never posts a notification of its own (DESIGN-KF-A: Android owns delivery and DND; the
 * shade is the notification center). tests/kf-a-notification-policy-check.sh enforces this.
 */
class SableNotificationListenerService : NotificationListenerService() {
    private val workerScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default,
        )

    private lateinit var policies: ConnectedAppsRepository
    private lateinit var history: ConnectedNotificationHistoryStore
    private lateinit var normalizer: ConnectedNotificationNormalizer
    private lateinit var userManager: UserManager
    private lateinit var attention: AttentionDispatcher
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
        attention = AttentionDispatcher(applicationContext)

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
        notifyHiddenAppsChanged()
        refreshPoliciesAndReprocess()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
    ) {
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        attention.onPosted(sbn, ranking)
        onNotificationPostedForHub(sbn, ranking)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onNotificationPosted(sbn, currentRanking)
    }

    private fun onNotificationPostedForHub(
        sbn: StatusBarNotification,
        ranking: Ranking?,
    ) {
        val envelope = eligibleEnvelope(sbn, ranking)
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

    private fun eligibleEnvelope(
        sbn: StatusBarNotification,
        ranking: Ranking?,
    ): ConnectedNotificationEnvelope? {
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
                    contentRestricted = isSourceRestricted(sbn.notification, ranking),
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

    /**
     * "Source restricted": the source (or its channel) marked the notification SECRET, i.e. not
     * to be revealed on insecure surfaces. Hub then keeps source/count/action only and does not
     * cache the text. Android 15+ additionally strips sensitive content (such as one-time codes)
     * before it reaches non-system listeners like Hub.
     */
    private fun isSourceRestricted(
        notification: Notification,
        ranking: Ranking?,
    ): Boolean =
        AndroidVisibility.effective(
            notificationVisibility = notification.visibility,
            channelLockscreenVisibility =
                ranking?.channel?.lockscreenVisibility ?: AndroidVisibility.VISIBILITY_NO_OVERRIDE,
        ) == AndroidVisibility.Secret

    private fun captureEnvelope(
        sbn: StatusBarNotification,
        key: ConnectedAppKey,
        policy: ConnectedAppPolicy,
        contentRestricted: Boolean,
    ): ConnectedNotificationEnvelope {
        val notification = sbn.notification
        val messagingUser = messagingUser(notification)
        val envelope =
            ConnectedNotificationEnvelope(
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
        return if (contentRestricted) envelope.withoutContent() else envelope
    }

    private fun ConnectedNotificationEnvelope.withoutContent(): ConnectedNotificationEnvelope =
        copy(
            notificationTitle = null,
            conversationTitle = null,
            notificationBody = null,
            messages = messages.map { it.copy(senderLabel = null, body = null) },
        )

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

    /**
     * Only the source's own action is used (`HUB_INVENTS_PROVIDER_ACTIONS=NO`): a free-form
     * RemoteInput with a PendingIntent, never a system-generated contextual (smart) action.
     */
    private fun captureReplyCandidate(notification: Notification): ConnectedNotificationReplyCandidate? {
        val candidates =
            notification.actions
                .orEmpty()
                .asSequence()
                .take(MAX_NOTIFICATION_ACTIONS)
                .map { action ->
                    val pendingIntent = action.actionIntent
                    val remoteInput =
                        action.remoteInputs
                            ?.asSequence()
                            ?.take(MAX_NOTIFICATION_REMOTE_INPUTS)
                            ?.firstOrNull { input ->
                                input.allowFreeFormInput
                            }
                    val facts =
                        ReplyActionFacts(
                            hasPendingIntent = pendingIntent != null,
                            hasFreeFormRemoteInput = remoteInput != null,
                            isContextual = action.isContextual,
                            semanticReply = action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY,
                        )
                    val candidate =
                        if (pendingIntent != null && remoteInput != null) {
                            ConnectedNotificationReplyCandidate(
                                pendingIntent = pendingIntent,
                                remoteInput = remoteInput,
                            )
                        } else {
                            null
                        }
                    facts to candidate
                }.toList()

        return ReplyEligibility.chooseReplyAction(candidates)?.second
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
                // Re-derive Hub state only; never re-signal attention for old notifications.
                val rankingMap = currentRanking
                activeNotifications
                    .orEmpty()
                    .forEach { sbn ->
                        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
                        onNotificationPostedForHub(sbn, ranking)
                    }
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
        ConnectedReplyRegistry.clear()
        notifyHubDataChanged()
        notifyHiddenAppsChanged()
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

    /** Notification access changed: Sable Start re-reads which apps Hub may hide. */
    private fun notifyHiddenAppsChanged() {
        applicationContext.contentResolver.notifyChange(
            ConnectedAppsRepository.HIDDEN_URI,
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
}
