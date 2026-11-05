package org.sableos.hub.policy

import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/** What Remind me hands Sable Tasks: an app, a stable key and the app's name. Never the message. */
data class HubFollowUp(
    val packageName: String,
    val key: String,
    val appName: String,
)

/**
 * Remind me (row menu, off unless turned on): asks Sable Tasks for a follow-up tomorrow morning
 * through its FOLLOW_UP entry. The key is the Hub row (thread, and message when each message has
 * its own row), so asking twice moves the same task instead of adding another. An SMS row names
 * Hub itself, where the thread lives.
 */
object HubRemindMe {
    const val TASKS_PACKAGE = "org.sableos.tasks"
    const val ACTION_FOLLOW_UP = "org.sableos.tasks.action.FOLLOW_UP"
    const val EXTRA_SOURCE_PACKAGE = "org.sableos.tasks.extra.SOURCE_PACKAGE"
    const val EXTRA_SOURCE_KEY = "org.sableos.tasks.extra.SOURCE_KEY"
    const val EXTRA_SOURCE_APP = "org.sableos.tasks.extra.SOURCE_APP"
    const val HUB_NAME = "Sable Hub"

    fun followUp(
        conversation: HubConversation,
        hubPackage: String,
    ): HubFollowUp? {
        val key = "hub:" + conversation.threadId + (conversation.messageId?.let { ":$it" } ?: "")
        val sms = conversation.source == HubConversationSource.Sms
        val pkg = if (sms) hubPackage else conversation.sourcePackage
        val name = if (sms) HUB_NAME else conversation.sourceLabel?.takeIf(String::isNotBlank) ?: pkg
        return pkg?.let { HubFollowUp(it, key, name ?: it) }
    }
}
