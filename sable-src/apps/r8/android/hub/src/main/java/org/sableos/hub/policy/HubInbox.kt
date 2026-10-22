package org.sableos.hub.policy

import org.sableos.hub.HubCall
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import org.sableos.hub.HubPerson

/**
 * Hub inbox filters (SABLE_HUB_KEYBOARD_FIRST_UX "Filters"): one list, narrowed by source or by
 * what needs attention. Each filter has a single-letter key that works while focus is on a filter
 * chip or a row (never while typing in a text field, where letters stay text). Sable Start opens
 * Hub on one of them with [HubIntents.EXTRA_FILTER].
 */
enum class HubInboxFilter(
    val label: String,
    val shortcut: Char,
) {
    All("All", 'a'),

    /** Unread or Hub-priority conversations, answerable threads and missed calls. */
    Priority("Priority", 'p'),
    Messages("Messages", 'm'),

    /** Missed calls and voicemail: the calls the user did not take. */
    Missed("Missed", 'x'),
    Calls("Calls", 'c'),

    /** Connected mail apps (known mail packages or notifications in the email category). */
    Mail("Mail", 'e'),
    Notifications("Notifications", 'n'),

    /** Conversations and calls with someone in the address book. */
    People("People", 'o'),
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
    /**
     * Rows for [filter] matching [query], newest first. The query is a [HubSearch] query: words
     * match name, number, text or source; `unread`, `missed`, `today`, `from:` and `app:` narrow.
     * [people] (address-book numbers) serve the People filter; [todayStartMillis] the `today` word.
     */
    fun items(
        conversations: List<HubConversation>,
        calls: List<HubCall>,
        filter: HubInboxFilter,
        query: String = "",
        people: List<HubPerson> = emptyList(),
        todayStartMillis: Long = 0L,
    ): List<HubInboxItem> {
        val search = HubSearch.parse(query)
        val known = knownNumbers(people, filter)
        val conversationItems =
            conversations
                .filter { conversation -> filter.includes(conversation, known) }
                .filter { conversation -> search.matches(conversation, todayStartMillis) }
                .map(HubInboxItem::Conversation)
        val callItems =
            calls
                .filter { call -> filter.includes(call, known) }
                .filter { call -> search.matches(call, todayStartMillis) }
                .map(HubInboxItem::Call)
        return (conversationItems + callItems).sortedByDescending(HubInboxItem::dateMillis)
    }

    /** Count of rows that need attention per filter, for chip badges. */
    fun attentionCount(
        conversations: List<HubConversation>,
        calls: List<HubCall>,
        filter: HubInboxFilter,
        people: List<HubPerson> = emptyList(),
    ): Int {
        val known = knownNumbers(people, filter)
        val unread =
            conversations
                .filter { conversation -> filter.includes(conversation, known) }
                .count { conversation -> conversation.unreadCount > 0 }
        val missed =
            calls
                .filter { call -> filter.includes(call, known) }
                .count { call -> call.kind.needsAttention }
        return unread + missed
    }

    /** Hub priority: ordering and attention only, never a Do Not Disturb exception. */
    fun isPriority(conversation: HubConversation): Boolean =
        conversation.unreadCount > 0 || conversation.canQuickReply || conversation.hubPriority

    private fun knownNumbers(
        people: List<HubPerson>,
        filter: HubInboxFilter,
    ): Set<String> =
        if (filter == HubInboxFilter.People) {
            people
                .map(HubPerson::phoneNumber)
                .filter(String::isNotBlank)
                .map(HubAddress::normalize)
                .toSet()
        } else {
            emptySet()
        }

    private fun HubInboxFilter.includes(
        conversation: HubConversation,
        known: Set<String>,
    ): Boolean =
        when (this) {
            HubInboxFilter.All -> true
            HubInboxFilter.Priority -> isPriority(conversation)
            HubInboxFilter.Messages -> conversation.source == HubConversationSource.Sms
            HubInboxFilter.Notifications -> conversation.source == HubConversationSource.ConnectedApp
            HubInboxFilter.Mail -> HubMailSources.isMail(conversation)
            HubInboxFilter.People -> isKnown(conversation, known)
            HubInboxFilter.Calls, HubInboxFilter.Missed -> false
        }

    private fun HubInboxFilter.includes(
        call: HubCall,
        known: Set<String>,
    ): Boolean =
        when (this) {
            HubInboxFilter.All, HubInboxFilter.Calls -> true
            HubInboxFilter.Priority, HubInboxFilter.Missed -> call.kind.needsAttention
            HubInboxFilter.People -> call.number.isNotBlank() && HubAddress.normalize(call.number) in known
            HubInboxFilter.Messages, HubInboxFilter.Notifications, HubInboxFilter.Mail -> false
        }

    /** SMS threads with an address-book number; connected apps have no phone number to match. */
    private fun isKnown(
        conversation: HubConversation,
        known: Set<String>,
    ): Boolean =
        conversation.source == HubConversationSource.Sms &&
            conversation.address.isNotBlank() &&
            HubAddress.normalize(conversation.address) in known
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
