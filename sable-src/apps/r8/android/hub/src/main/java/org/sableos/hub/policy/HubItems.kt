package org.sableos.hub.policy

/**
 * What Hub lists besides conversations, as BlackBerry 10's Hub did (owner request 2026-11-06):
 * every notification the user can clear and read, from any app in Hub, such as missed calls,
 * voicemail, reminders, invitations and app alerts. Ongoing work (music, downloads, navigation,
 * calls in progress, foreground services), group summaries and the system's own status lines stay
 * out, as they never were Hub items.
 */
object HubItems {
    /** Categories for status, never messages: Android's own names, so no platform class is needed here. */
    val NOT_ITEMS =
        setOf(
            "progress",
            "transport",
            "service",
            "sys",
            "status",
            "navigation",
            "stopwatch",
            "call",
        )

    fun shows(facts: HubItemFacts): Boolean =
        !facts.ongoing &&
            !facts.foregroundService &&
            facts.clearable &&
            !facts.groupSummary &&
            facts.hasText &&
            facts.category !in NOT_ITEMS
}

/** What [HubItems] looks at in one posted notification. */
data class HubItemFacts(
    val ongoing: Boolean,
    val foregroundService: Boolean,
    val clearable: Boolean,
    val groupSummary: Boolean,
    val category: String?,
    val hasText: Boolean,
)
