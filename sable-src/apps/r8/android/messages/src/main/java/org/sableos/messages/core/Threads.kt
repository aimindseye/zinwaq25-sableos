package org.sableos.messages.core

import org.sableos.messages.core.MessagesText.t

/** Builds the conversation list and a conversation's timeline from provider rows. */
object ThreadGrouping {
    private const val MAX_TITLE_NAMES = 3

    /**
     * Conversation list, newest first. [canonicalAddresses] maps the provider's recipient ids to
     * numbers, [unreadByThread] counts unread messages and [nameFor] returns a contact name for a
     * number, or null.
     */
    fun summaries(
        threads: List<ThreadRow>,
        canonicalAddresses: Map<Long, String>,
        unreadByThread: Map<Long, Int>,
        nameFor: (String) -> String?,
    ): List<ThreadSummary> =
        threads
            .filter { it.messageCount > 0 }
            .map { row ->
                val addresses = row.recipientIds.mapNotNull { canonicalAddresses[it] }
                val unread = unreadByThread[row.threadId] ?: if (row.read) 0 else 1
                ThreadSummary(
                    threadId = row.threadId,
                    addresses = addresses,
                    title = title(addresses, nameFor),
                    snippet = row.snippet,
                    dateMillis = row.dateMillis,
                    unreadCount = unread,
                    hasError = row.hasError,
                )
            }.sortedWith(compareByDescending<ThreadSummary> { it.dateMillis }.thenByDescending { it.threadId })

    /** "Maya Patel", "Maya, Alex, +15550100 +2" or "Unknown sender". */
    fun title(
        addresses: List<String>,
        nameFor: (String) -> String?,
    ): String {
        val labels = addresses.map { nameFor(it) ?: it }
        val shown = labels.take(MAX_TITLE_NAMES).joinToString(", ") { it.substringBefore(' ') }
        val more = labels.size - MAX_TITLE_NAMES
        return when {
            labels.isEmpty() -> t("unknown_sender")
            labels.size == 1 -> labels.single()
            more > 0 -> "$shown +$more"
            else -> shown
        }
    }

    /**
     * One timeline from SMS and MMS rows, oldest first so the newest message is last (the
     * latest-anchored layout). Ties keep a stable order by kind and id.
     */
    fun timeline(messages: List<ConversationMessage>): List<ConversationMessage> =
        messages
            .distinctBy { it.key }
            .sortedWith(compareBy<ConversationMessage> { it.dateMillis }.thenBy { it.kind }.thenBy { it.id })

    /** Filters the conversation list by name, number or snippet ("type to filter"). */
    fun filter(
        threads: List<ThreadSummary>,
        query: String,
    ): List<ThreadSummary> {
        val needle = query.trim()
        if (needle.isEmpty()) return threads
        val numberKey = Addresses.normalize(needle).trimStart('+')
        return threads.filter { thread ->
            thread.title.contains(needle, ignoreCase = true) ||
                thread.snippet.contains(needle, ignoreCase = true) ||
                (numberKey.length >= 2 && thread.addresses.any { Addresses.normalize(it).contains(numberKey) })
        }
    }
}

/** Which numbers an incoming MMS belongs to, so 1:1 MMS lands in the same thread as SMS. */
object MmsRecipients {
    fun threadRecipients(
        from: String,
        to: List<String>,
        cc: List<String>,
        selfNumbers: List<String>,
    ): List<String> {
        val others = (to + cc).map { Addresses.normalize(it) }.filter { it.isNotEmpty() }
        // A message addressed only to us: we may not know our own number, so never add it.
        if (others.size <= 1) return listOf(Addresses.normalize(from)).filter { it.isNotEmpty() }
        val notSelf = others.filterNot { other -> selfNumbers.any { Addresses.same(it, other) } }
        return (listOf(Addresses.normalize(from)) + notSelf)
            .filter { it.isNotEmpty() }
            .distinctBy { Addresses.matchKey(it) }
    }
}
