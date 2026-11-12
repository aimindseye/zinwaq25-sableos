package net.thunderbird.android

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import app.k9mail.legacy.mailstore.MessageDetailsAccessor
import app.k9mail.legacy.mailstore.MessageListChangedListener
import app.k9mail.legacy.mailstore.MessageListRepository
import app.k9mail.legacy.message.controller.MessageReference
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import org.koin.core.context.GlobalContext

/**
 * Sable Mail's inbox for the SableOS Hub: the newest messages of every account's Inbox, read-only.
 *
 * Sable Mail keeps the accounts, passwords and tokens and does the syncing and push (IMAP IDLE);
 * the Hub only reads sender, subject, preview, date and read state from here, and opens a message
 * through [SableMailOpenActivity]. Only the SableOS home and Sable Hub may read it.
 */
class SableMailHubProvider : ContentProvider() {
    @Volatile
    private var listening = false

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val appContext = checkNotNull(context)
        enforceHubCaller(appContext)
        require(uri.pathSegments == listOf(PATH_MESSAGES)) { "Unknown Sable Mail Hub uri" }
        val koin = GlobalContext.get()
        val accounts = koin.get<LegacyAccountManager>()
        val repository = koin.get<MessageListRepository>()
        listenForChanges(appContext, repository)
        val rows =
            accounts.getAccounts()
                .flatMap { account -> inbox(repository, account) }
                .sortedByDescending(HubMail::date)
                .take(MAX_MESSAGES)
        return MatrixCursor(COLUMNS, rows.size).apply {
            rows.forEach { mail -> addRow(mail.row()) }
            setNotificationUri(appContext.contentResolver, MESSAGES_URI)
        }
    }

    private fun inbox(
        repository: MessageListRepository,
        account: LegacyAccount,
    ): List<HubMail> {
        val inbox = account.inboxFolderId ?: return emptyList()
        return runCatching {
            repository.getMessages(
                account.uuid,
                "folder_id = ?",
                arrayOf(inbox.toString()),
                // The repository appends this to ORDER BY; LIMIT keeps the read small.
                "date DESC, id DESC LIMIT $MAX_MESSAGES",
            ) { message -> HubMail.from(account, message) }
        }.getOrDefault(emptyList())
    }

    private fun listenForChanges(
        context: Context,
        repository: MessageListRepository,
    ) {
        if (listening) return
        synchronized(this) {
            if (listening) return
            repository.addListener(
                MessageListChangedListener {
                    context.contentResolver.notifyChange(MESSAGES_URI, null)
                },
            )
            listening = true
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.org.sableos.mail.hub.message"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Read-only provider")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    private class HubMail(
        val id: Long,
        val account: String,
        val accountColor: Int,
        val sender: String,
        val subject: String,
        val preview: String,
        val date: Long,
        val read: Boolean,
        val attachments: Boolean,
        val reference: String,
    ) {
        fun row(): Array<Any?> =
            arrayOf(
                id,
                account,
                accountColor,
                sender,
                subject,
                preview,
                date,
                if (read) 1 else 0,
                if (attachments) 1 else 0,
                reference,
            )

        companion object {
            fun from(
                account: LegacyAccount,
                message: MessageDetailsAccessor,
            ): HubMail {
                val from = message.fromAddresses.firstOrNull()
                val preview = message.preview
                return HubMail(
                    id = ((account.accountNumber + 1).toLong() shl ACCOUNT_SHIFT) + message.id,
                    account = account.profile.name,
                    accountColor = account.profile.color,
                    sender = from?.personal?.takeIf(String::isNotBlank) ?: from?.address.orEmpty(),
                    subject = message.subject.orEmpty(),
                    preview = if (preview.isPreviewTextAvailable) preview.previewText.orEmpty() else "",
                    date = message.messageDate,
                    read = message.isRead,
                    attachments = message.hasAttachments,
                    reference =
                        MessageReference(account.uuid, message.folderId, message.messageServerId)
                            .toIdentityString(),
                )
            }
        }
    }

    companion object {
        const val AUTHORITY = "org.sableos.mail.hub"
        private const val PATH_MESSAGES = "messages"
        val MESSAGES_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_MESSAGES")
        private const val MAX_MESSAGES = 50
        private const val ACCOUNT_SHIFT = 52
        private val COLUMNS =
            arrayOf(
                "id",
                "account",
                "account_color",
                "sender",
                "subject",
                "preview",
                "date",
                "read",
                "attachments",
                "reference",
            )

        internal val HUB_CALLERS = setOf("org.sableos.launcher", "org.sableos.hub")

        fun enforceHubCaller(context: Context) {
            val callingUid = Binder.getCallingUid()
            if (callingUid == Process.myUid()) return
            val packages = context.packageManager.getPackagesForUid(callingUid)?.toSet().orEmpty()
            if (packages.intersect(HUB_CALLERS).isEmpty()) {
                throw SecurityException("Sable Mail's Hub feed is restricted to the SableOS home and Sable Hub.")
            }
        }
    }
}
