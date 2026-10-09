package org.sableos.hub.policy

/**
 * Facts about one notification action, captured from the source app's own Notification.Action.
 * Hub only ever uses an action the source published (`HUB_INVENTS_PROVIDER_ACTIONS=NO`).
 */
data class ReplyActionFacts(
    val hasPendingIntent: Boolean,
    /** A RemoteInput with allowFreeFormInput=true. Choice-only and data-only inputs do not count. */
    val hasFreeFormRemoteInput: Boolean,
    /** Notification.Action.isContextual(): system-generated (smart) action, not source-owned. */
    val isContextual: Boolean,
    val semanticReply: Boolean,
)

enum class ReplyAvailability {
    /** Hub may show an inline reply that fills the source's RemoteInput. */
    Inline,

    /** The source supports reply, but the device or profile must be unlocked first. */
    RequiresUnlock,

    /** No safe reply path: hand off to the source app (Open-app fallback). */
    OpenSourceApp,
}

object ReplyEligibility {
    /** Whether a captured action is a source-owned, free-form RemoteInput reply candidate. */
    fun isSourceReplyAction(facts: ReplyActionFacts): Boolean =
        facts.hasPendingIntent && facts.hasFreeFormRemoteInput && !facts.isContextual

    /** Picks the source's reply action: semantic REPLY first, then the first free-form one. */
    fun <T> chooseReplyAction(candidates: List<Pair<ReplyActionFacts, T>>): Pair<ReplyActionFacts, T>? {
        val eligible = candidates.filter { isSourceReplyAction(it.first) }
        return eligible.firstOrNull { it.first.semanticReply } ?: eligible.firstOrNull()
    }

    /**
     * Reply from Hub is allowed only when the source published a safe reply path, the user allows
     * quick reply for this source, and the privacy state permits actions. Hub is not a lock-screen
     * surface, so a locked device or a locked/paused profile always needs authentication first,
     * even for actions whose source would allow them without unlocking (DESIGN-KF-A "Privacy
     * states" permits that only where Android/source policy explicitly supports it; SystemUI's
     * own inline reply keeps that path).
     */
    fun availability(
        action: ReplyActionFacts?,
        hubAllowsQuickReply: Boolean,
        privacy: PrivacyContext,
    ): ReplyAvailability =
        when {
            action == null || !isSourceReplyAction(action) || !hubAllowsQuickReply -> {
                ReplyAvailability.OpenSourceApp
            }

            PrivacyPosture.requiresUnlockForActions(privacy) -> {
                ReplyAvailability.RequiresUnlock
            }

            else -> {
                ReplyAvailability.Inline
            }
        }
}
