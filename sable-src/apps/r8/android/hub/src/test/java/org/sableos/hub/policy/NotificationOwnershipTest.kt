package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.ConnectedAppPolicy
import java.lang.reflect.Modifier

/**
 * DESIGN-KF-A implementation gates expressed as tests:
 * NOTIFICATION_POLICY_ANDROID_OWNED, DUPLICATE_NOTIFICATION_POLICY_STORE=PASS_ABSENT,
 * HUB_DELIVERY_POLICY_OWNERSHIP=PASS_ABSENT, SHADE_IS_NOTIFICATION_CENTER (Android owns history).
 * The source-level half of these gates is tests/kf-a-notification-policy-check.sh.
 */
class NotificationOwnershipTest {
    private fun persistedFields(type: Class<*>): List<String> =
        type.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }

    @Test
    fun everyHubPolicyFieldIsHubOwned() {
        val fields = persistedFields(ConnectedAppPolicy::class.java) - "key"
        assertEquals(fields.toSet(), NotificationOwnership.HUB_POLICY_FIELDS.keys)
        NotificationOwnership.HUB_POLICY_FIELDS.values.forEach { concept ->
            assertTrue("$concept must be Hub-owned", NotificationOwnership.hubMayStore(concept))
        }
    }

    @Test
    fun hubPolicyStoresNoAndroidDeliveryField() {
        val fields = persistedFields(ConnectedAppPolicy::class.java) + persistedFields(AttentionSelection::class.java)
        fields.forEach { field ->
            NotificationOwnership.FORBIDDEN_HUB_FIELD_FRAGMENTS.forEach { fragment ->
                assertFalse("$field looks like Android delivery state", field.lowercase().contains(fragment))
            }
        }
    }

    @Test
    fun androidOwnedConceptsRouteToAndroidSettings() {
        val androidOwned = NotificationConcept.entries.filter(NotificationOwnership::isAndroidOwned)
        assertTrue(androidOwned.contains(NotificationConcept.DoNotDisturb))
        assertTrue(androidOwned.contains(NotificationConcept.NotificationHistory))
        assertTrue(androidOwned.contains(NotificationConcept.LockscreenVisibility))
        androidOwned.forEach { concept ->
            val route = AndroidSettingsRoutes.routeFor(concept, SettingsTarget(packageName = "org.example.chat"))
            assertTrue("$concept must deep-link into Android Settings", route is SettingsRoute.Android)
        }
    }

    @Test
    fun hubAndAttentionConceptsStayInSable() {
        NotificationConcept.entries
            .filter { it.owner == PolicyOwner.SableHub }
            .forEach { assertEquals(SettingsRoute.HubConnectedApp, AndroidSettingsRoutes.routeFor(it)) }
        NotificationConcept.entries
            .filter { it.owner == PolicyOwner.SableAttention }
            .forEach { assertEquals(SettingsRoute.SableAttention, AndroidSettingsRoutes.routeFor(it)) }
    }

    @Test
    fun attentionOutputsWithAndroidChannelFieldsAreNotSableSelectable() {
        AttentionOutput.entries.forEach { output ->
            val sableOwned = output.owner == OutputOwner.Sable
            assertEquals(sableOwned, NotificationOwnership.attentionMayStore(output.concept))
        }
    }
}
