package org.sableos.hub.policy

/**
 * Settings IA handoff (DESIGN-KF-A "Settings information architecture").
 *
 * Hub and Sable Attention never keep a toggle for something Android owns; they deep-link to the
 * owning Android Settings page. [actions] is tried in order by the Android layer, which uses the
 * first one that resolves. Hub- and Attention-owned concepts resolve to Hub's own screens.
 */
data class SettingsTarget(
    val packageName: String? = null,
    val appUid: Int? = null,
    val channelId: String? = null,
    val conversationId: String? = null,
    val listenerComponent: String? = null,
)

sealed interface SettingsRoute {
    data class Android(
        val actions: List<String>,
        val stringExtras: Map<String, String> = emptyMap(),
        val intExtras: Map<String, Int> = emptyMap(),
    ) : SettingsRoute {
        init {
            require(actions.isNotEmpty())
        }
    }

    data object HubConnectedApp : SettingsRoute

    data object SableAttention : SettingsRoute
}

object AndroidSettingsRoutes {
    const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
    const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    const val ACTION_CHANNEL_NOTIFICATION_SETTINGS = "android.settings.CHANNEL_NOTIFICATION_SETTINGS"
    const val ACTION_CONVERSATION_SETTINGS = "android.settings.CONVERSATION_SETTINGS"
    const val ACTION_NOTIFICATION_HISTORY = "android.settings.NOTIFICATION_HISTORY"
    const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
    const val ACTION_LISTENER_DETAIL = "android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS"
    const val ACTION_LISTENER_LIST = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    const val ACTION_LOCK_SCREEN_SETTINGS = "android.settings.LOCK_SCREEN_SETTINGS"
    const val ACTION_DISPLAY_SETTINGS = "android.settings.DISPLAY_SETTINGS"

    const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
    const val EXTRA_CHANNEL_ID = "android.provider.extra.CHANNEL_ID"
    const val EXTRA_CONVERSATION_ID = "android.provider.extra.CONVERSATION_ID"
    const val EXTRA_LISTENER_COMPONENT = "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME"

    /** Settings' own (non-SDK) uid extra; lets Settings open a work-profile app's page. */
    const val EXTRA_APP_UID = "app_uid"

    fun routeFor(
        concept: NotificationConcept,
        target: SettingsTarget = SettingsTarget(),
    ): SettingsRoute =
        when (concept.owner) {
            PolicyOwner.SableHub -> SettingsRoute.HubConnectedApp
            PolicyOwner.SableAttention -> SettingsRoute.SableAttention
            PolicyOwner.Android -> androidRoute(concept, target)
        }

    private fun androidRoute(
        concept: NotificationConcept,
        target: SettingsTarget,
    ): SettingsRoute.Android =
        when (concept) {
            NotificationConcept.DoNotDisturb -> {
                SettingsRoute.Android(listOf(ACTION_ZEN_MODE_SETTINGS, ACTION_NOTIFICATION_SETTINGS))
            }

            NotificationConcept.NotificationHistory -> {
                SettingsRoute.Android(listOf(ACTION_NOTIFICATION_HISTORY, ACTION_NOTIFICATION_SETTINGS))
            }

            NotificationConcept.NotificationAccess -> {
                listenerRoute(target)
            }

            NotificationConcept.AlwaysOnDisplay -> {
                SettingsRoute.Android(listOf(ACTION_LOCK_SCREEN_SETTINGS, ACTION_DISPLAY_SETTINGS))
            }

            NotificationConcept.ConversationPriority -> {
                if (target.packageName != null && target.channelId != null) {
                    channelRoute(target)
                } else {
                    SettingsRoute.Android(listOf(ACTION_CONVERSATION_SETTINGS, ACTION_NOTIFICATION_SETTINGS))
                }
            }

            else -> {
                // Per-app delivery fields (importance, sound, vibration, heads-up, badge,
                // lockscreen visibility, lights, channels, snooze availability) live on Android's
                // app or channel notification page; without a package, the top-level page.
                when {
                    target.packageName == null -> {
                        SettingsRoute.Android(listOf(ACTION_NOTIFICATION_SETTINGS))
                    }

                    target.channelId != null -> {
                        channelRoute(target)
                    }

                    else -> {
                        appRoute(target)
                    }
                }
            }
        }

    private fun appRoute(target: SettingsTarget): SettingsRoute.Android =
        SettingsRoute.Android(
            actions = listOf(ACTION_APP_NOTIFICATION_SETTINGS, ACTION_NOTIFICATION_SETTINGS),
            stringExtras = mapOf(EXTRA_APP_PACKAGE to requireNotNull(target.packageName)),
            intExtras = uidExtra(target),
        )

    private fun channelRoute(target: SettingsTarget): SettingsRoute.Android {
        val extras =
            buildMap {
                put(EXTRA_APP_PACKAGE, requireNotNull(target.packageName))
                put(EXTRA_CHANNEL_ID, requireNotNull(target.channelId))
                target.conversationId?.let { put(EXTRA_CONVERSATION_ID, it) }
            }
        return SettingsRoute.Android(
            actions =
                listOf(
                    ACTION_CHANNEL_NOTIFICATION_SETTINGS,
                    ACTION_APP_NOTIFICATION_SETTINGS,
                    ACTION_NOTIFICATION_SETTINGS,
                ),
            stringExtras = extras,
            intExtras = uidExtra(target),
        )
    }

    private fun listenerRoute(target: SettingsTarget): SettingsRoute.Android =
        if (target.listenerComponent != null) {
            SettingsRoute.Android(
                actions = listOf(ACTION_LISTENER_DETAIL, ACTION_LISTENER_LIST),
                stringExtras = mapOf(EXTRA_LISTENER_COMPONENT to target.listenerComponent),
            )
        } else {
            SettingsRoute.Android(listOf(ACTION_LISTENER_LIST))
        }

    private fun uidExtra(target: SettingsTarget): Map<String, Int> =
        target.appUid?.let { mapOf(EXTRA_APP_UID to it) }.orEmpty()
}
