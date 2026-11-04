package org.sableos.hub.policy

import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/** One message row on Sable Start's Hub page: what the Hub inbox row shows, already redacted. */
data class HubHomeRow(
    val threadId: Long,
    val title: String,
    val preview: String,
    val dateMillis: Long,
    val unread: Int,
    val sms: Boolean,
    val packageName: String?,
)

/**
 * The newest Hub rows for Sable Start's Hub page (owner decision 2026-11-04: the home Hub page
 * lists each message, as BB10 and TenSeven do). Rows are the inbox's own conversations after
 * HubRedaction, so a source whose content Hub hides stays hidden here. Nothing while the device
 * is locked or Sable private mode is on, and nothing from a work or private profile: the home
 * screen shows the personal profile only.
 */
object HubHomeRows {
    const val MAX_ROWS = 30
    const val MAX_PREVIEW = 140
    const val MAX_TITLE = 80

    fun rows(
        conversations: List<HubConversation>,
        privacy: HubPrivacyState,
        personalSerial: Long,
    ): List<HubHomeRow> {
        if (privacy.countsOnly || personalSerial < 0L) return emptyList()
        return conversations
            .asSequence()
            .filter { it.sourceUserSerial == null || it.sourceUserSerial == personalSerial }
            .filter { it.displayName.isNotBlank() }
            .sortedByDescending { it.lastDateMillis }
            .take(MAX_ROWS)
            .map { conversation ->
                HubHomeRow(
                    threadId = conversation.threadId,
                    title = conversation.displayName.trim().take(MAX_TITLE),
                    preview = conversation.lastBody.replace('\n', ' ').trim().take(MAX_PREVIEW),
                    dateMillis = conversation.lastDateMillis,
                    unread = conversation.unreadCount.coerceAtLeast(0),
                    sms = conversation.source == HubConversationSource.Sms,
                    packageName = conversation.sourcePackage,
                )
            }.toList()
    }
}
