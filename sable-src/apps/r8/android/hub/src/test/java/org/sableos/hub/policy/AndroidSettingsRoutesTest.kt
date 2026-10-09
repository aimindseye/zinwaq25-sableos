package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSettingsRoutesTest {
    @Test
    fun deliveryFieldsOpenTheAppNotificationPageWithUid() {
        val route =
            AndroidSettingsRoutes.routeFor(
                NotificationConcept.Sound,
                SettingsTarget(packageName = "org.example.chat", appUid = 1010123),
            ) as SettingsRoute.Android
        assertEquals(AndroidSettingsRoutes.ACTION_APP_NOTIFICATION_SETTINGS, route.actions.first())
        assertEquals("org.example.chat", route.stringExtras[AndroidSettingsRoutes.EXTRA_APP_PACKAGE])
        assertEquals(1010123, route.intExtras[AndroidSettingsRoutes.EXTRA_APP_UID])
    }

    @Test
    fun channelAndConversationTargetsOpenTheChannelPage() {
        val route =
            AndroidSettingsRoutes.routeFor(
                NotificationConcept.ConversationPriority,
                SettingsTarget(packageName = "org.example.chat", channelId = "dm", conversationId = "alex"),
            ) as SettingsRoute.Android
        assertEquals(AndroidSettingsRoutes.ACTION_CHANNEL_NOTIFICATION_SETTINGS, route.actions.first())
        assertEquals("alex", route.stringExtras[AndroidSettingsRoutes.EXTRA_CONVERSATION_ID])
    }

    @Test
    fun dndAndHistoryAndAccessHaveTheirOwnAndroidPages() {
        assertEquals(
            AndroidSettingsRoutes.ACTION_ZEN_MODE_SETTINGS,
            (AndroidSettingsRoutes.routeFor(NotificationConcept.DoNotDisturb) as SettingsRoute.Android).actions.first(),
        )
        assertEquals(
            AndroidSettingsRoutes.ACTION_NOTIFICATION_HISTORY,
            (AndroidSettingsRoutes.routeFor(NotificationConcept.NotificationHistory) as SettingsRoute.Android)
                .actions
                .first(),
        )
        val access =
            AndroidSettingsRoutes.routeFor(
                NotificationConcept.NotificationAccess,
                SettingsTarget(listenerComponent = "org.sableos.hub/.notifications.SableNotificationListenerService"),
            ) as SettingsRoute.Android
        assertEquals(AndroidSettingsRoutes.ACTION_LISTENER_DETAIL, access.actions.first())
        assertEquals(AndroidSettingsRoutes.ACTION_LISTENER_LIST, access.actions.last())
    }

    @Test
    fun everyRouteEndsInAPageThatAlwaysExists() {
        NotificationConcept.entries.filter(NotificationOwnership::isAndroidOwned).forEach { concept ->
            val route = AndroidSettingsRoutes.routeFor(concept) as SettingsRoute.Android
            val last = route.actions.last()
            val stable =
                setOf(
                    AndroidSettingsRoutes.ACTION_NOTIFICATION_SETTINGS,
                    AndroidSettingsRoutes.ACTION_LISTENER_LIST,
                    AndroidSettingsRoutes.ACTION_DISPLAY_SETTINGS,
                )
            assertEquals("$concept falls back to $last", true, last in stable)
        }
    }
}
