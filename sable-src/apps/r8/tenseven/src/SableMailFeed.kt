package io.github.aimindseye.tenseven.conversations

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.aimindseye.tenseven.model.HubMessageKind
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SableOS: Sable Mail's inboxes in the Hub, read from Sable Mail's own feed (org.sableos.mail.hub), not from
 * its notifications. Sable Mail keeps the accounts and does the syncing and push; the Hub holds the newest
 * inbox messages in memory only, and opens one in Sable Mail. While the feed answers, Sable Mail's
 * notifications are left out of the Hub so each email shows once.
 */
object SableMailFeed {
    const val MAIL_PACKAGE = "org.sableos.mail"
    val URI: Uri by lazy { Uri.parse("content://org.sableos.mail.hub/messages") }
    private const val ACTION_OPEN = "org.sableos.mail.action.OPEN_MESSAGE"
    private const val APP_NAME = "Sable Mail"
    private const val KEY_PREFIX = "sablemail|"
    private const val HIGH_BITS = 32

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var items: List<HubNotification> = emptyList()
        private set

    private val started = AtomicBoolean(false)
    private val reader by lazy { Executors.newSingleThreadExecutor() }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Reads the feed now and again whenever Sable Mail says its inboxes changed. Once per process. */
    fun start(
        context: Context,
        onChange: () -> Unit,
    ) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val observer =
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) = refresh(app, onChange)
            }
        runCatching { app.contentResolver.registerContentObserver(URI, false, observer) }
        refresh(app, onChange)
    }

    private fun refresh(
        context: Context,
        onChange: () -> Unit,
    ) {
        reader.execute {
            val next = read(context)
            val changed = (next != null) != active || (next ?: emptyList()) != items
            active = next != null
            items = next.orEmpty()
            if (changed) main.post(onChange)
        }
    }

    /** Null when Sable Mail is not installed or refuses; then the Hub shows its notifications as before. */
    private fun read(context: Context): List<HubNotification>? =
        runCatching {
            context.contentResolver.query(URI, null, null, null, null)?.use { cursor ->
                buildList { while (cursor.moveToNext()) add(row(context, cursor)) }
            }
        }.getOrNull()

    private fun row(
        context: Context,
        cursor: Cursor,
    ): HubNotification {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        val key = KEY_PREFIX + id
        val account = cursor.getString(cursor.getColumnIndexOrThrow("account")).orEmpty()
        val sender = cursor.getString(cursor.getColumnIndexOrThrow("sender")).orEmpty().ifBlank { account }
        val subject = cursor.getString(cursor.getColumnIndexOrThrow("subject")).orEmpty()
        val preview = cursor.getString(cursor.getColumnIndexOrThrow("preview")).orEmpty()
        val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
        val read = cursor.getInt(cursor.getColumnIndexOrThrow("read")) != 0
        val reference = cursor.getString(cursor.getColumnIndexOrThrow("reference")).orEmpty()
        val message =
            ConversationMessage(
                id = key,
                conversationId = key,
                notificationKey = key,
                packageName = MAIL_PACKAGE,
                appName = APP_NAME,
                text = mailText(subject, preview),
                timestamp = date,
                senderName = sender,
                isOutgoing = false,
                // Read in Sable Mail: shown, not counted as new.
                isHistoric = read,
            )
        return HubNotification(
            key = key,
            conversationId = key,
            conversationName = sender,
            packageName = MAIL_PACKAGE,
            appName = APP_NAME,
            postedAt = date,
            isOngoing = false,
            messages = listOf(message),
            contentIntent = openIntent(context, id, reference),
            replyAction = null,
            kind = HubMessageKind.Email,
        )
    }

    private fun openIntent(
        context: Context,
        id: Long,
        reference: String,
    ): PendingIntent? =
        runCatching {
            val intent =
                Intent(ACTION_OPEN)
                    .setPackage(MAIL_PACKAGE)
                    .putExtra("reference", reference)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(
                context,
                (id xor (id ushr HIGH_BITS)).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }.getOrNull()

    /** "Subject · start of the text", or whichever of the two there is. Pure, unit tested. */
    internal fun mailText(
        subject: String,
        preview: String,
    ): String {
        val clean = preview.replace(Regex("\\s+"), " ").trim()
        return listOf(subject.trim(), clean).filter(String::isNotEmpty).joinToString(" · ")
    }

    /** The Hub's notifications with Sable Mail's own left out while the feed answers, plus the feed. */
    internal fun merge(
        notifications: List<HubNotification>,
        feedActive: Boolean = active,
        feed: List<HubNotification> = items,
    ): List<HubNotification> =
        if (!feedActive) {
            notifications
        } else {
            (notifications.filterNot { it.packageName == MAIL_PACKAGE } + feed).sortedByDescending(HubNotification::postedAt)
        }
}
