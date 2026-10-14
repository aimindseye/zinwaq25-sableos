package org.sableos.hub.policy

import org.sableos.hub.HubCall
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/**
 * Hub inbox filters (SABLE_HUB_KEYBOARD_FIRST_UX "Filters"): one list, narrowed by source.
 * Each filter has a single-letter key that works while focus is on a filter chip or a row
 * (never while typing in a text field, where letters stay text).
 */
enum class HubInboxFilter(
    val label: String,
    val shortcut: Char,
) {
    All("All", 'a'),
    Messages("Messages", 'm'),
    Calls("Calls", 'c'),
    Notifications("Notifications", 'n'),
    ;

    companion object {
        fun forShortcut(typed: Char): HubInboxFilter? = entries.firstOrNull { it.shortcut == typed.lowercaseChar() }
    }
}

/** One inbox row: a conversation (SMS or connected app) or a call-log entry. */
sealed interface HubInboxItem {
    val key: String
    val dateMillis: Long

    data class Conversation(
        val conversation: HubConversation,
    ) : HubInboxItem {
        override val key: String
            get() = "conversation:" + conversation.threadId + ":" + conversation.address
        override val dateMillis: Long
            get() = conversation.lastDateMillis
    }

    data class Call(
        val call: HubCall,
    ) : HubInboxItem {
        override val key: String
            get() = "call:" + call.id
        override val dateMillis: Long
            get() = call.dateMillis
    }
}

object HubInbox {
    /** Rows for [filter] matching [query] (name, number or text), newest first. */
    fun items(
        conversations: List<HubConversation>,
        calls: List<HubCall>,
        filter: HubInboxFilter,
        query: String = "",
    ): List<HubInboxItem> {
        val term = query.trim()
        val conversationItems =
            conversations
                .filter { conversation -> filter.includes(conversation.source) }
                .filter { conversation -> term.isEmpty() || conversation.matches(term) }
                .map(HubInboxItem::Conversation)
        val callItems =
            if (filter == HubInboxFilter.All || filter == HubInboxFilter.Calls) {
                calls
                    .filter { call -> term.isEmpty() || call.matches(term) }
                    .map(HubInboxItem::Call)
            } else {
                emptyList()
            }
        return (conversationItems + callItems).sortedByDescending(HubInboxItem::dateMillis)
    }

    /** Count of rows that need attention per filter, for chip badges. */
    fun attentionCount(
        conversations: List<HubConversation>,
        calls: List<HubCall>,
        filter: HubInboxFilter,
    ): Int {
        val unread =
            conversations
                .filter { conversation -> filter.includes(conversation.source) }
                .count { conversation -> conversation.unreadCount > 0 }
        val missed =
            if (filter == HubInboxFilter.All || filter == HubInboxFilter.Calls) {
                calls.count { call -> call.kind.needsAttention }
            } else {
                0
            }
        return unread + missed
    }

    private fun HubInboxFilter.includes(source: HubConversationSource): Boolean =
        when (this) {
            HubInboxFilter.All -> true
            HubInboxFilter.Messages -> source == HubConversationSource.Sms
            HubInboxFilter.Notifications -> source == HubConversationSource.ConnectedApp
            HubInboxFilter.Calls -> false
        }

    private fun HubConversation.matches(term: String): Boolean =
        displayName.contains(term, ignoreCase = true) ||
            address.contains(term, ignoreCase = true) ||
            lastBody.contains(term, ignoreCase = true) ||
            sourceLabel.orEmpty().contains(term, ignoreCase = true)

    private fun HubCall.matches(term: String): Boolean =
        displayName.contains(term, ignoreCase = true) ||
            number.contains(term, ignoreCase = true)
}

/** Which conversation a Hub route shows: by thread id, else the SMS thread for an address. */
object HubThreadResolver {
    /** Thread id for an SMS conversation that has no provider thread yet (first message unsent). */
    const val NEW_THREAD_ID = 0L

    fun resolve(
        conversations: List<HubConversation>,
        threadId: Long,
        address: String?,
    ): HubConversation? =
        conversations.firstOrNull { conversation ->
            threadId != NEW_THREAD_ID && conversation.threadId == threadId
        } ?: address?.let { wanted ->
            conversations
                .filter { conversation ->
                    conversation.source == HubConversationSource.Sms &&
                        HubAddress.sameAddress(conversation.address, wanted)
                }.maxByOrNull(HubConversation::lastDateMillis)
        }
}

/** Call-log rows under Hub privacy: the same level that governs Hub's own SMS. */
object HubCallRedaction {
    const val REDACTED_LABEL = "Call"

    fun apply(
        calls: List<HubCall>,
        level: HubContentLevel,
    ): List<HubCall> =
        when (level) {
            HubContentLevel.Full,
            HubContentLevel.SenderOnly,
            -> calls

            // Who called is the sender; hide it and the number (and so callback) fail-closed.
            HubContentLevel.SourceAndCount -> calls.map { call -> call.copy(number = "", displayName = REDACTED_LABEL) }

            HubContentLevel.GenericProfile,
            HubContentLevel.Hidden,
            -> emptyList()
        }
}
