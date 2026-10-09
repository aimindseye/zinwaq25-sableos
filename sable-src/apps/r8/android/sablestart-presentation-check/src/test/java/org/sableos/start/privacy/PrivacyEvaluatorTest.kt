package org.sableos.start.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.start.privacy.PrivacyPermissions as P

/** DESIGN-KF-D truthfulness rule: effective, current-user access only; unknown is omitted. */
class PrivacyEvaluatorTest {
    private fun facts(
        vararg permissions: Pair<String, PermissionFact>,
        targetSdk: Int = 36,
        deviceSdk: Int = 36,
    ) = PrivacyFacts(permissions.toMap(), targetSdk = targetSdk, deviceSdk = deviceSdk)

    private val granted = PermissionFact(granted = true)
    private val denied = PermissionFact(granted = false)

    private fun grantedOp(mode: OpMode) = PermissionFact(granted = true, opMode = mode)

    @Test
    fun requestedButNotGrantedShowsNothing() {
        val privacy = PrivacyEvaluator.evaluate(facts(P.CAMERA to denied, P.RECORD_AUDIO to denied))
        assertTrue(privacy.effective.isEmpty())
        assertEquals(GroupState.NotAllowed, privacy.groups[PrivacyGroup.Camera])
        assertFalse(privacy.hasUnknown)
    }

    @Test
    fun grantedRuntimePermissionIsEffective() {
        val privacy = PrivacyEvaluator.evaluate(facts(P.CAMERA to granted, P.READ_CONTACTS to granted))
        assertEquals(listOf(PrivacyGroup.Camera, PrivacyGroup.Contacts), privacy.effective)
    }

    @Test
    fun ignoredAppOpCancelsGrantAndForegroundCountsAsAllowed() {
        val privacy =
            PrivacyEvaluator.evaluate(
                facts(
                    P.CAMERA to grantedOp(OpMode.Ignored),
                    P.RECORD_AUDIO to grantedOp(OpMode.Foreground),
                    P.READ_CALENDAR to grantedOp(OpMode.Errored),
                    P.READ_SMS to grantedOp(OpMode.Default),
                ),
            )
        assertEquals(listOf(PrivacyGroup.Microphone, PrivacyGroup.Messages), privacy.effective)
    }

    @Test
    fun unreadableAppOpIsUnknownNotGuessed() {
        val privacy = PrivacyEvaluator.evaluate(facts(P.CAMERA to grantedOp(OpMode.Unknown)))
        assertTrue(privacy.effective.isEmpty())
        assertEquals(GroupState.Unknown, privacy.groups[PrivacyGroup.Camera])
        assertTrue(privacy.hasUnknown)
        // An unknown sibling does not hide a known grant in the same group.
        val mixed =
            PrivacyEvaluator.evaluate(
                facts(P.READ_CONTACTS to grantedOp(OpMode.Unknown), P.WRITE_CONTACTS to granted),
            )
        assertEquals(listOf(PrivacyGroup.Contacts), mixed.effective)
    }

    @Test
    fun locationApproximateOnlyWhenPreciseIsKnownOff() {
        val approx =
            PrivacyEvaluator.evaluate(facts(P.ACCESS_FINE_LOCATION to denied, P.ACCESS_COARSE_LOCATION to granted))
        assertEquals("Location approximate", approx.labelFor(PrivacyGroup.Location))
        assertEquals(listOf(PrivacyGroup.Location), approx.effective)

        val precise =
            PrivacyEvaluator.evaluate(facts(P.ACCESS_FINE_LOCATION to granted, P.ACCESS_COARSE_LOCATION to granted))
        assertEquals("Location", precise.labelFor(PrivacyGroup.Location))

        val precisionUnknown =
            PrivacyEvaluator.evaluate(
                facts(P.ACCESS_FINE_LOCATION to grantedOp(OpMode.Unknown), P.ACCESS_COARSE_LOCATION to granted),
            )
        assertEquals("Location", precisionUnknown.labelFor(PrivacyGroup.Location))

        val coarseOnlyApp = PrivacyEvaluator.evaluate(facts(P.ACCESS_COARSE_LOCATION to granted))
        assertEquals("Location approximate", coarseOnlyApp.labelFor(PrivacyGroup.Location))

        val unknown = PrivacyEvaluator.evaluate(facts(P.ACCESS_FINE_LOCATION to grantedOp(OpMode.Unknown)))
        assertEquals(GroupState.Unknown, unknown.groups[PrivacyGroup.Location])
    }

    @Test
    fun photosLimitedOnlyOnAndroid14WithSelectedPhotos() {
        val limited =
            PrivacyEvaluator.evaluate(
                facts(
                    P.READ_MEDIA_IMAGES to denied,
                    P.READ_MEDIA_VIDEO to denied,
                    P.READ_MEDIA_VISUAL_USER_SELECTED to granted,
                ),
            )
        assertEquals("Photos limited", limited.labelFor(PrivacyGroup.Photos))

        val full = PrivacyEvaluator.evaluate(facts(P.READ_MEDIA_IMAGES to granted))
        assertEquals("Photos", full.labelFor(PrivacyGroup.Photos))

        val android13 =
            PrivacyEvaluator.evaluate(
                facts(P.READ_MEDIA_VISUAL_USER_SELECTED to granted, deviceSdk = 33),
            )
        assertTrue(android13.effective.isEmpty())
    }

    @Test
    fun legacyStorageMeansMediaOnlyForOldTargets() {
        val legacy = PrivacyEvaluator.evaluate(facts(P.READ_EXTERNAL_STORAGE to granted, targetSdk = 29))
        assertEquals(listOf(PrivacyGroup.Photos, PrivacyGroup.Audio), legacy.effective)
        val modern = PrivacyEvaluator.evaluate(facts(P.READ_EXTERNAL_STORAGE to granted, targetSdk = 34))
        assertTrue(modern.effective.isEmpty())
    }

    @Test
    fun notificationsNeedTheEffectiveGrantNotTheManifestEntry() {
        val off = PrivacyEvaluator.evaluate(facts(P.POST_NOTIFICATIONS to denied))
        assertFalse(PrivacyGroup.Notifications in off.effective)
        val on = PrivacyEvaluator.evaluate(facts(P.POST_NOTIFICATIONS to granted))
        assertEquals(listOf(PrivacyGroup.Notifications), on.effective)
        // Before Android 13 the launcher cannot read another app's switch: omitted entirely.
        val old = PrivacyEvaluator.evaluate(facts(P.POST_NOTIFICATIONS to granted, deviceSdk = 32))
        assertNull(old.groups[PrivacyGroup.Notifications])
    }

    @Test
    fun specialAccessUsesAppOpsAndKnownFlagsOnly() {
        val f =
            PrivacyFacts(
                permissions =
                    mapOf(
                        P.SYSTEM_ALERT_WINDOW to denied,
                        P.REQUEST_INSTALL_PACKAGES to denied,
                        P.PACKAGE_USAGE_STATS to granted,
                    ),
                targetSdk = 36,
                deviceSdk = 36,
                accessibilityServiceEnabled = true,
                activeDeviceAdmin = null,
                overlayOp = OpMode.Allowed,
                installAppsOp = OpMode.Default,
                usageAccessOp = OpMode.Ignored,
            )
        val privacy = PrivacyEvaluator.evaluate(f)
        assertEquals(listOf(PrivacyGroup.Accessibility, PrivacyGroup.Overlay), privacy.effective)
        assertNull(privacy.groups[PrivacyGroup.DeviceAdmin])
        assertEquals(GroupState.NotAllowed, privacy.groups[PrivacyGroup.InstallApps])
        assertEquals(GroupState.NotAllowed, privacy.groups[PrivacyGroup.UsageAccess])
    }

    @Test
    fun specialOpWithoutReadableModeIsUnknown() {
        val privacy =
            PrivacyEvaluator.evaluate(
                PrivacyFacts(
                    permissions = mapOf(P.SYSTEM_ALERT_WINDOW to granted),
                    targetSdk = 36,
                    deviceSdk = 36,
                    overlayOp = null,
                ),
            )
        assertEquals(GroupState.Unknown, privacy.groups[PrivacyGroup.Overlay])
        assertTrue(privacy.effective.isEmpty())
    }

    @Test
    fun groupListMatchesDesign() {
        assertEquals(11, PrivacyGroup.everyday.size)
        assertEquals(
            listOf("Accessibility", "Device admin", "Install apps", "Overlay", "Usage access"),
            PrivacyGroup.specialAccess.map { it.label },
        )
        PrivacyGroup.entries.forEach { group -> assertFalse(group.label.contains("android.permission")) }
    }
}
