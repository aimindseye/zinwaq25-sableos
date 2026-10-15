package org.sableos.hub.policy

import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import java.util.Locale

/** A run of adjacent inbox rows from one person; [parent] is the newest, [children] follow it. */
data class HubInboxCluster(
    val parent: HubInboxItem,
    val children: List<HubInboxItem> = emptyList(),
) {
    /** Stable while the newest row stays the newest: the parent row's key. */
    val id: String
        get() = parent.key

    val rolledUp: Boolean
        get() = children.isNotEmpty()

    /** Distinct source labels of the children, newest first, for the parent row's "+N" line. */
    val childSourceLabels: List<String>
        get() =
            children
                .mapNotNull { child -> (child as? HubInboxItem.Conversation)?.conversation?.let(::sourceLabelOf) }
                .distinct()
}

enum class HubInboxRowRole {
    Single,
    Parent,
    Child,
}

/** The line drawn under a row: a double line closes a rolled-up conversation. */
enum class HubInboxDivider {
    Single,
    Double,
}

/** One visible inbox row with its place in a cluster. */
data class HubInboxRow(
    val item: HubInboxItem,
    val cluster: HubInboxCluster,
    val role: HubInboxRowRole,
    val expanded: Boolean,
    val divider: HubInboxDivider,
)

/**
 * "Combine conversations": adjacent conversation rows from the same person roll up under the
 * newest one. Same person means the same whole display name (ignoring case and whitespace), or,
 * for two SMS rows, the same address. Whole names only, so a group chat never merges with a 1:1
 * chat; this reaches across sources (an SMS thread and a chat app with the same contact). Calls
 * never combine, and a row from anyone else in between breaks the run. Rows whose identity is
 * redacted (only an app, "SMS" or a profile label is shown) never combine, so rolling up cannot
 * reveal more than the rows do.
 */
object HubConversationRollup {
    /** [items] newest first, already filtered; with [enabled] false every row stands alone. */
    fun clusters(
        items: List<HubInboxItem>,
        enabled: Boolean,
    ): List<HubInboxCluster> {
        if (!enabled) return items.map { item -> HubInboxCluster(item) }
        val clusters = mutableListOf<HubInboxCluster>()
        items.forEach { item ->
            val last = clusters.lastOrNull()
            if (last != null && samePerson(last.parent, item)) {
                clusters[clusters.lastIndex] = last.copy(children = last.children + item)
            } else {
                clusters += HubInboxCluster(item)
            }
        }
        return clusters
    }

    /** Visible rows: a collapsed cluster shows only its parent; [expanded] holds cluster ids. */
    fun rows(
        clusters: List<HubInboxCluster>,
        expanded: Set<String>,
    ): List<HubInboxRow> =
        clusters.flatMap { cluster ->
            if (!cluster.rolledUp) {
                listOf(HubInboxRow(cluster.parent, cluster, HubInboxRowRole.Single, false, HubInboxDivider.Single))
            } else if (cluster.id !in expanded) {
                listOf(HubInboxRow(cluster.parent, cluster, HubInboxRowRole.Parent, false, HubInboxDivider.Double))
            } else {
                val parent = HubInboxRow(cluster.parent, cluster, HubInboxRowRole.Parent, true, HubInboxDivider.Single)
                val children =
                    cluster.children.mapIndexed { index, child ->
                        val last = index == cluster.children.lastIndex
                        HubInboxRow(
                            item = child,
                            cluster = cluster,
                            role = HubInboxRowRole.Child,
                            expanded = true,
                            divider = if (last) HubInboxDivider.Double else HubInboxDivider.Single,
                        )
                    }
                listOf(parent) + children
            }
        }

    fun samePerson(
        first: HubInboxItem,
        second: HubInboxItem,
    ): Boolean {
        val a = (first as? HubInboxItem.Conversation)?.conversation
        val b = (second as? HubInboxItem.Conversation)?.conversation
        return a != null && b != null && !identityHidden(a) && !identityHidden(b) && samePerson(a, b)
    }

    private fun samePerson(
        a: HubConversation,
        b: HubConversation,
    ): Boolean {
        val bothSms = a.source == HubConversationSource.Sms && b.source == HubConversationSource.Sms
        return (bothSms && HubAddress.sameAddress(a.address, b.address)) ||
            normalizedName(a).let { name -> name.isNotEmpty() && name == normalizedName(b) }
    }

    private fun identityHidden(conversation: HubConversation): Boolean {
        val name = conversation.displayName.trim()
        return name.isEmpty() ||
            (name == HubRedaction.SMS_LABEL && conversation.source == HubConversationSource.Sms) ||
            name == HubRedaction.WORK_PROFILE_LABEL ||
            (conversation.source == HubConversationSource.ConnectedApp && name == conversation.sourceLabel?.trim())
    }

    private fun normalizedName(conversation: HubConversation): String =
        conversation.displayName.filterNot(Char::isWhitespace).lowercase(Locale.ROOT)
}

private fun sourceLabelOf(conversation: HubConversation): String =
    if (conversation.source == HubConversationSource.Sms) {
        HubRedaction.SMS_LABEL
    } else {
        conversation.sourceLabel?.takeIf(String::isNotBlank) ?: CONNECTED_APP_LABEL
    }

private const val CONNECTED_APP_LABEL = "connected app"
