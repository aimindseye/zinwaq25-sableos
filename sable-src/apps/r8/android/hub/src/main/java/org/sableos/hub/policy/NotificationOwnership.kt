package org.sableos.hub.policy

/**
 * DESIGN-KF-A ownership model, as data.
 *
 * Android NotificationManager / NotificationChannel own delivery; SystemUI and Settings present
 * and edit it. Sable Hub owns only aggregation preferences, and Sable Attention owns only the
 * selection of profile-supported Sable hardware outputs. Everything Android owns is reached by a
 * Settings route ([AndroidSettingsRoutes]); Hub never stores a copy of it.
 */
enum class PolicyOwner {
    Android,
    SableHub,
    SableAttention,
}

enum class NotificationConcept(
    val owner: PolicyOwner,
) {
    // Delivery: Android NotificationManager / NotificationChannel.
    DeliveryImportance(PolicyOwner.Android),
    Sound(PolicyOwner.Android),
    Vibration(PolicyOwner.Android),
    HeadsUp(PolicyOwner.Android),
    Badge(PolicyOwner.Android),
    LockscreenVisibility(PolicyOwner.Android),
    Snooze(PolicyOwner.Android),
    ConversationPriority(PolicyOwner.Android),
    Channels(PolicyOwner.Android),
    DoNotDisturb(PolicyOwner.Android),
    NotificationHistory(PolicyOwner.Android),
    NotificationAccess(PolicyOwner.Android),
    StatusLight(PolicyOwner.Android),
    AlwaysOnDisplay(PolicyOwner.Android),

    // Hub: aggregation state, stored per Android user/profile + package.
    IncludeInHub(PolicyOwner.SableHub),
    HubPriority(PolicyOwner.SableHub),
    HubPreview(PolicyOwner.SableHub),
    HubHistory(PolicyOwner.SableHub),
    HubQuickReply(PolicyOwner.SableHub),
    HubLauncherHiding(PolicyOwner.SableHub),

    // Attention: Sable-driven hardware outputs that Android has no channel field for.
    AttentionKeyboardBacklight(PolicyOwner.SableAttention),
    AttentionSecondaryDisplay(PolicyOwner.SableAttention),
}

object NotificationOwnership {
    /**
     * The concept behind each persisted field of [org.sableos.hub.ConnectedAppPolicy] (except its
     * key). A new field must be added here, and the ownership test fails if it maps to an
     * Android-owned concept: that is the `DUPLICATE_NOTIFICATION_POLICY_STORE=PASS_ABSENT` gate.
     */
    val HUB_POLICY_FIELDS: Map<String, NotificationConcept> =
        mapOf(
            "includeInMessages" to NotificationConcept.IncludeInHub,
            "favorite" to NotificationConcept.HubPriority,
            "previewPolicy" to NotificationConcept.HubPreview,
            "retention" to NotificationConcept.HubHistory,
            "allowQuickReply" to NotificationConcept.HubQuickReply,
            "hideFromLauncher" to NotificationConcept.HubLauncherHiding,
        )

    /** Field names that would mean Hub stores Android delivery policy. Never allowed in Hub state. */
    val FORBIDDEN_HUB_FIELD_FRAGMENTS: List<String> =
        listOf(
            "importance",
            "sound",
            "vibrat",
            "headsup",
            "heads_up",
            "badge",
            "lockscreen",
            "snooze",
            "dnd",
            "interruption",
            "bypass",
            "channel",
        )

    fun ownerOf(concept: NotificationConcept): PolicyOwner = concept.owner

    fun hubMayStore(concept: NotificationConcept): Boolean = concept.owner == PolicyOwner.SableHub

    fun attentionMayStore(concept: NotificationConcept): Boolean = concept.owner == PolicyOwner.SableAttention

    fun isAndroidOwned(concept: NotificationConcept): Boolean = concept.owner == PolicyOwner.Android
}
