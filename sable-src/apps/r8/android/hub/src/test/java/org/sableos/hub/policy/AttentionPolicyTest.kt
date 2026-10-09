package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ATTENTION_CAPABILITY_GATING, ATTENTION_LOCKSCREEN_REDACTION, HUB_DND_BYPASS=PASS_ABSENT. */
class AttentionPolicyTest {
    private val profile =
        AttentionDeviceProfile.parse(
            "titan2",
            "titan2:keyboard_backlight=validated,secondary_display=validated,status_led=validated,aod=absent",
            PlatformAttentionFacts(hasAudioOutput = true, hasVibrator = true),
        )
    private val both = setOf(AttentionOutput.KeyboardBacklight, AttentionOutput.SecondaryDisplay)
    private val alerting = AndroidAlertState(AndroidAlertState.IMPORTANCE_HIGH, matchesInterruptionFilter = true)
    private val unlocked = PrivacyContext(deviceLocked = false)

    private fun decision(
        output: AttentionOutput,
        selection: Set<AttentionOutput> = both,
        android: AndroidAlertState = alerting,
        privacy: PrivacyContext = unlocked,
    ): AttentionDecision? =
        AttentionPolicy
            .decide(profile, selection, android, privacy)
            .firstOrNull { it.output == output }

    @Test
    fun androidOwnedOutputsAreDelegatedNeverDrivenBySable() {
        listOf(AttentionOutput.Audio, AttentionOutput.Haptic, AttentionOutput.StatusLed).forEach {
            assertEquals(AttentionDecision.DelegatedToAndroid(it), decision(it))
        }
    }

    @Test
    fun unsupportedOutputsGetNoDecision() {
        assertEquals(null, decision(AttentionOutput.AlwaysOnDisplay))
        val bare = AttentionDeviceProfile.parse("zinwa-q25", null, PlatformAttentionFacts(true, true))
        assertTrue(
            AttentionPolicy.decide(bare, both, alerting, unlocked).none { it is AttentionDecision.Emit },
        )
    }

    @Test
    fun keyboardBacklightIsOffUnlessSelected() {
        assertEquals(
            AttentionDecision.Suppressed(AttentionOutput.KeyboardBacklight, SuppressionReason.NotSelected),
            decision(AttentionOutput.KeyboardBacklight, selection = AttentionDefaults.defaultSelection(profile)),
        )
    }

    @Test
    fun doNotDisturbIsNeverBypassed() {
        val intercepted = alerting.copy(matchesInterruptionFilter = false)
        both.forEach {
            assertEquals(
                AttentionDecision.Suppressed(it, SuppressionReason.DoNotDisturb),
                decision(it, android = intercepted),
            )
        }
    }

    @Test
    fun quietOrBlockedAndroidImportanceNeverAlerts() {
        listOf(
            AndroidAlertState.IMPORTANCE_NONE,
            AndroidAlertState.IMPORTANCE_MIN,
            AndroidAlertState.IMPORTANCE_LOW,
        ).forEach { importance ->
            assertEquals(
                AttentionDecision.Suppressed(
                    AttentionOutput.SecondaryDisplay,
                    SuppressionReason.QuietOrBlockedByAndroid,
                ),
                decision(AttentionOutput.SecondaryDisplay, android = alerting.copy(importance = importance)),
            )
        }
    }

    @Test
    fun alertOnceUpdatesDoNotReAlert() {
        assertEquals(
            AttentionDecision.Suppressed(AttentionOutput.KeyboardBacklight, SuppressionReason.AlertOnce),
            decision(AttentionOutput.KeyboardBacklight, android = alerting.copy(alertOnlyOnceUpdate = true)),
        )
    }

    @Test
    fun keyboardBacklightNeverCarriesContent() {
        val emit = decision(AttentionOutput.KeyboardBacklight) as AttentionDecision.Emit
        assertEquals(AttentionContent.Generic, emit.content)
    }

    @Test
    fun lockedSecondaryDisplayShowsCategoryCountByDefault() {
        val locked = PrivacyContext(deviceLocked = true)
        val emit = decision(AttentionOutput.SecondaryDisplay, privacy = locked) as AttentionDecision.Emit
        assertEquals(AttentionContent.CategoryCount, emit.content)
    }

    @Test
    fun lockscreenSecretOrHiddenNotificationsProduceNoHardwareAttention() {
        val secret = PrivacyContext(deviceLocked = true, visibility = AndroidVisibility.Secret)
        val hidden =
            PrivacyContext(
                deviceLocked = true,
                lockscreen = LockscreenNotificationPolicy(showNotifications = false, allowPrivateContent = false),
            )
        listOf(secret, hidden).forEach { privacy ->
            both.forEach {
                assertEquals(
                    AttentionDecision.Suppressed(it, SuppressionReason.Privacy),
                    decision(it, privacy = privacy),
                )
            }
        }
    }

    @Test
    fun lockedWorkProfileOnlyGetsAGenericIndicator() {
        val work = PrivacyContext(deviceLocked = false, profile = ProfileKind.Work, profileLocked = true)
        val emit = decision(AttentionOutput.SecondaryDisplay, privacy = work) as AttentionDecision.Emit
        assertEquals(AttentionContent.Generic, emit.content)
    }

    @Test
    fun lockedPrivateSpaceProducesNothing() {
        val privateSpace = PrivacyContext(deviceLocked = false, profile = ProfileKind.Private, profileLocked = true)
        assertEquals(
            AttentionDecision.Suppressed(AttentionOutput.SecondaryDisplay, SuppressionReason.Privacy),
            decision(AttentionOutput.SecondaryDisplay, privacy = privateSpace),
        )
    }

    @Test
    fun everyEmittedPatternIsBounded() {
        AttentionPolicy.decide(profile, both, alerting, unlocked).filterIsInstance<AttentionDecision.Emit>().forEach {
            assertTrue(it.pattern.totalMillis() <= BoundedPattern.MAX_TOTAL_MILLIS)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun continuousPatternsCannotBeBuilt() {
        BoundedPattern(pulses = 100, onMillis = 1_000L, offMillis = 1_000L)
    }
}
