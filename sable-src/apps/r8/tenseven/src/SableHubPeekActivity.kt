package io.github.aimindseye.tenseven

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.SableHubPeek

/**
 * SableOS (2.0 plan, BB10): Hub over any app. Holding the Recents key (frameworks/base 0326) or Hub in the
 * Alt+BlackBerry key menu opens this panel over the app in front: the newest conversations, unread first.
 * The trackpad moves, Enter opens one in its app, Open Hub shows the full Hub, and Back closes the panel
 * back to the app. It reads only the Hub's in-memory list; the system never opens it on the lock screen.
 * Added by the SableOS flavor (sable-src/apps/r8/tenseven).
 */
class SableHubPeekActivity : Activity() {
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConversationHub.ensureStateLoaded(applicationContext)
        val now = System.currentTimeMillis()
        val state = ConversationHub.state()
        val conversations = ConversationHub.conversations().associateBy(HubConversation::id)
        val entries =
            conversations.values.map { c ->
                SableHubPeek.Entry(
                    id = c.id,
                    name = c.name,
                    preview = c.latestMessage?.text.orEmpty(),
                    at = c.latestNotification.postedAt,
                    unread = state.unreadCount(c),
                    snoozed = state.isSnoozed(c.id, now),
                )
            }
        val rows = SableHubPeek.rows(entries)
        val labels = rows.map(SableHubPeek::label) + OPEN_HUB
        dialog =
            AlertDialog
                .Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(SableHubPeek.title(entries))
                .setItems(labels.toTypedArray()) { _, which ->
                    finish()
                    val row = rows.getOrNull(which)
                    val conversation = row?.let { conversations[it.id] }
                    if (conversation == null || !ConversationHub.open(applicationContext, conversation.openTarget)) {
                        openFullHub()
                    }
                }.setOnCancelListener { finish() }
                .create()
                .also { it.show() }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    private fun openFullHub() {
        runCatching {
            startActivity(Intent(ACTION_SHOW_HUB).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private companion object {
        const val OPEN_HUB = "Open Hub"
        const val ACTION_SHOW_HUB = "org.sableos.start.action.SHOW_HUB"
    }
}
