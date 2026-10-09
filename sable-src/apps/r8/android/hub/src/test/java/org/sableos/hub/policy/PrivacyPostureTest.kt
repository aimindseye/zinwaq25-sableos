package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubPreviewPolicy

/** DESIGN-KF-A privacy table; ATTENTION_LOCKSCREEN_REDACTION and WORK_PROFILE_SEPARATION. */
class PrivacyPostureTest {
    private val show = HubPreviewPolicy.ShowContent

    @Test
    fun unlockedHubShowsAllowedPreviews() {
        val unlocked = PrivacyContext(deviceLocked = false)
        assertEquals(HubContentLevel.Full, PrivacyPosture.hubLevel(unlocked, show))
        assertEquals(HubContentLevel.SenderOnly, PrivacyPosture.hubLevel(unlocked, HubPreviewPolicy.SenderOnly))
        assertEquals(HubContentLevel.SourceAndCount, PrivacyPosture.hubLevel(unlocked, HubPreviewPolicy.SourceOnly))
    }

    @Test
    fun lockedPrivateAndRestrictedRedactToSourceAndCount() {
        listOf(
            PrivacyContext(deviceLocked = true),
            PrivacyContext(deviceLocked = false, privateMode = true),
            PrivacyContext(deviceLocked = false, sourceRestricted = true),
        ).forEach { context ->
            assertEquals(HubContentLevel.SourceAndCount, PrivacyPosture.hubLevel(context, show))
        }
    }

    @Test
    fun previewPreferenceNeverWidensPrivacy() {
        val locked = PrivacyContext(deviceLocked = true)
        HubPreviewPolicy.entries.forEach { preview ->
            assertTrue(PrivacyPosture.hubLevel(locked, preview) >= HubContentLevel.SourceAndCount)
        }
    }

    @Test
    fun lockedWorkProfileHidesWorkContent() {
        val work = PrivacyContext(deviceLocked = false, profile = ProfileKind.Work, profileLocked = true)
        assertEquals(HubContentLevel.GenericProfile, PrivacyPosture.hubLevel(work, show))
        assertEquals(AttentionContent.Generic, PrivacyPosture.attentionContent(work))
        assertTrue(PrivacyPosture.requiresUnlockForActions(work))
    }

    @Test
    fun lockedPrivateSpaceIsHiddenEntirely() {
        val privateSpace = PrivacyContext(deviceLocked = false, profile = ProfileKind.Private, profileLocked = true)
        assertEquals(HubContentLevel.Hidden, PrivacyPosture.hubLevel(privateSpace, show))
        assertEquals(AttentionContent.None, PrivacyPosture.attentionContent(privateSpace))
    }

    @Test
    fun lockedAttentionFollowsAndroidLockscreenSettings() {
        val base = PrivacyContext(deviceLocked = true)
        assertEquals(AttentionContent.CategoryCount, PrivacyPosture.attentionContent(base))
        assertEquals(
            AttentionContent.Full,
            PrivacyPosture.attentionContent(base.copy(visibility = AndroidVisibility.Public)),
        )
        assertEquals(
            AttentionContent.Full,
            PrivacyPosture.attentionContent(
                base.copy(lockscreen = LockscreenNotificationPolicy(showNotifications = true, allowPrivateContent = true)),
            ),
        )
        assertEquals(
            AttentionContent.None,
            PrivacyPosture.attentionContent(base.copy(visibility = AndroidVisibility.Secret)),
        )
        // Private mode still limits a Public notification to a non-content indicator.
        assertEquals(
            AttentionContent.Generic,
            PrivacyPosture.attentionContent(base.copy(visibility = AndroidVisibility.Public, privateMode = true)),
        )
    }

    @Test
    fun unlockedActionsNeedNoExtraAuth() {
        assertFalse(PrivacyPosture.requiresUnlockForActions(PrivacyContext(deviceLocked = false)))
        assertTrue(PrivacyPosture.requiresUnlockForActions(PrivacyContext(deviceLocked = true)))
    }

    @Test
    fun effectiveVisibilityTakesTheMoreRestrictiveOfNotificationAndChannel() {
        val pub = AndroidVisibility.VISIBILITY_PUBLIC
        val priv = AndroidVisibility.VISIBILITY_PRIVATE
        val secret = AndroidVisibility.VISIBILITY_SECRET
        val none = AndroidVisibility.VISIBILITY_NO_OVERRIDE
        assertEquals(AndroidVisibility.Public, AndroidVisibility.effective(pub, none))
        assertEquals(AndroidVisibility.Secret, AndroidVisibility.effective(pub, secret))
        assertEquals(AndroidVisibility.Private, AndroidVisibility.effective(priv, pub))
        assertEquals(AndroidVisibility.Secret, AndroidVisibility.effective(secret, pub))
        assertEquals(AndroidVisibility.Private, AndroidVisibility.effective(42, none))
    }
}
