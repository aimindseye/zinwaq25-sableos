package org.sableos.hub.policy

import org.sableos.hub.HubCall
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import java.util.Locale

/** Where an inbox row came from, as the source dock groups it. */
sealed interface HubDockSource {
    /** Hub's own SMS conversations. */
    data object Sms : HubDockSource

    /** The call log (missed calls are the count). */
    data object Calls : HubDockSource

    /** One connected app in one Android user/profile. */
    data class App(
        val packageName: String,
        val userSerial: Long,
    ) : HubDockSource

    /**
     * Connected apps of a profile whose rows are generic ("Work profile"): one entry per profile,
     * so the dock never reveals which apps the rows hide.
     */
    data class Profile(
        val userSerial: Long,
    ) : HubDockSource
}

/** One dock entry: a source, its label as rows show it, and how many of its rows need attention. */
data class HubDockEntry(
    val source: HubDockSource,
    val label: String,
    val count: Int,
    val mail: Boolean = false,
)

/**
 * The source dock at the bottom of the Hub inbox: one entry per source with the same count the
 * filter chips use ([HubInbox.attentionCount]): unread conversations, and missed calls. Entries are
 * built from already-redacted rows, so the dock never shows more than the row list.
 */
object HubSourceDock {
    fun entries(
        conversations: List<HubConversation>,
        calls: List<HubCall>,
    ): List<HubDockEntry> {
        val conversationEntries =
            conversations
                .groupBy(::sourceOf)
                .map { (source, group) ->
                    val newest = group.maxBy(HubConversation::lastDateMillis)
                    HubDockEntry(
                        source = source,
                        label = labelFor(source, newest),
                        count = group.count { conversation -> conversation.unreadCount > 0 },
                        mail = group.any(HubMailSources::isMail),
                    )
                }
        val callEntry =
            if (calls.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    HubDockEntry(
                        source = HubDockSource.Calls,
                        label = CALLS_LABEL,
                        count = calls.count { call -> call.kind.needsAttention },
                    ),
                )
            }
        return (conversationEntries + callEntry).sortedWith(ORDER)
    }

    /** Collapsed, the dock shows sources with a count (and the [selected] one); expanded, every source. */
    fun visible(
        entries: List<HubDockEntry>,
        expanded: Boolean,
        selected: HubDockSource? = null,
    ): List<HubDockEntry> =
        if (expanded) entries else entries.filter { entry -> entry.count > 0 || entry.source == selected }

    /** Inbox rows narrowed to [source]; null keeps every row. */
    fun narrow(
        items: List<HubInboxItem>,
        source: HubDockSource?,
    ): List<HubInboxItem> = if (source == null) items else items.filter { item -> sourceOf(item) == source }

    /** Selecting the selected source again clears the selection. */
    fun toggle(
        current: HubDockSource?,
        selected: HubDockSource,
    ): HubDockSource? = if (current == selected) null else selected

    fun sourceOf(item: HubInboxItem): HubDockSource =
        when (item) {
            is HubInboxItem.Call -> HubDockSource.Calls
            is HubInboxItem.Conversation -> sourceOf(item.conversation)
        }

    fun sourceOf(conversation: HubConversation): HubDockSource {
        val packageName = conversation.sourcePackage
        val serial = conversation.sourceUserSerial
        return when {
            conversation.source == HubConversationSource.Sms || packageName == null || serial == null -> {
                HubDockSource.Sms
            }

            conversation.sourceLabel == HubRedaction.WORK_PROFILE_LABEL -> {
                HubDockSource.Profile(serial)
            }

            else -> {
                HubDockSource.App(packageName, serial)
            }
        }
    }

    private fun labelFor(
        source: HubDockSource,
        newest: HubConversation,
    ): String =
        when (source) {
            HubDockSource.Sms -> {
                HubRedaction.SMS_LABEL
            }

            HubDockSource.Calls -> {
                CALLS_LABEL
            }

            is HubDockSource.Profile -> {
                HubRedaction.WORK_PROFILE_LABEL
            }

            is HubDockSource.App -> {
                listOfNotNull(
                    newest.sourceLabel?.takeIf(String::isNotBlank) ?: source.packageName,
                    newest.profileBadge,
                ).joinToString(" · ")
            }
        }

    const val CALLS_LABEL = "Calls"

    /** Moves focus to the dock from a filter chip or a row (never while typing). */
    const val SHORTCUT = 'd'

    private val ORDER =
        compareByDescending<HubDockEntry> { entry -> entry.count }
            .thenBy { entry -> entry.label.lowercase(Locale.ROOT) }
            .thenBy { entry -> entry.source.toString() }
}
