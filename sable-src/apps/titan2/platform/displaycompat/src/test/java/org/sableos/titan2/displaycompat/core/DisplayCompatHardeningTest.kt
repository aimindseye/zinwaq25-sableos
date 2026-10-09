package org.sableos.titan2.displaycompat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingBackend(
    private val onApply: (String) -> ApplyResult = { ApplyResult.NoOp },
    private val onClear: (String) -> ApplyResult = { ApplyResult.NoOp },
    override val availability: BackendAvailability = BackendAvailability.CLOSED
) : DisplayBackend {
    override val name = "recording"
    val applied = mutableListOf<String>()
    val cleared = mutableListOf<String>()
    override fun apply(pkg: String, plan: CanvasPlan, p: DisplayProfile): ApplyResult {
        applied.add(pkg)
        return onApply(pkg)
    }
    override fun clear(pkg: String): ApplyResult {
        cleared.add(pkg)
        return onClear(pkg)
    }
}

/** A store that accepts a write and then forgets it, as a failing disk would. */
private class ForgetfulStore : ProfileStore {
    override fun get(pkg: String) = DisplayProfile.NATIVE
    override fun put(pkg: String, p: DisplayProfile) = Unit
    override fun reset(pkg: String) = Unit
    override fun resetAll() = Unit
    override fun packages(): Set<String> = emptySet()
}

private class ThrowingStore : ProfileStore {
    override fun get(pkg: String) = DisplayProfile.NATIVE
    override fun put(pkg: String, p: DisplayProfile): Unit = error("disk full")
    override fun reset(pkg: String) = Unit
    override fun resetAll() = Unit
    override fun packages(): Set<String> = emptySet()
}

class DisplayCompatHardeningTest {
    private val display = SizePx(1440, 1440)
    private val app = AppTraits("com.example.app")
    private val letterbox = DisplayProfile(AspectProfile.Ratio4x3Letterbox)

    @Test fun closedBackendNeverClaimsEnforcement() {
        val c = ProfileController(InMemoryProfileStore(), NoOpBackend)
        val o = c.save(app, letterbox, display, false, true)
        assertTrue(o.saved)
        assertEquals(EnforcementState.NOT_ENFORCED_BACKEND_CLOSED, o.enforcement)
        assertTrue(o.message().contains("NOT enforced"))
        assertFalse(o.message().contains("Applied"))
        assertEquals(BackendAvailability.CLOSED, c.backendAvailability)
    }

    @Test fun acceptedPlatformCallIsReportedAsUnverifiedNeverAsEnforced() {
        val b =
            RecordingBackend({
                ApplyResult.Applied("min aspect ratio=4")
            }, availability = BackendAvailability.OPEN_UNVERIFIED)
        val o = ProfileController(
            InMemoryProfileStore(),
            b
        ).save(app, letterbox, display, false, true)
        assertEquals(EnforcementState.PLATFORM_CALL_ACCEPTED_UNVERIFIED, o.enforcement)
        assertTrue(o.message().contains("not verified"))
        assertFalse(o.message().lowercase().contains("enforced"))
        assertFalse(o.message().contains("Applied"))
    }

    @Test fun unsupportedProfileIsStoredButReportedNotEnforcedWithReason() {
        val b = RecordingBackend({ ApplyResult.Unsupported("no platform equivalent") })
        val o = ProfileController(
            InMemoryProfileStore(),
            b
        ).save(app, letterbox, display, false, true)
        assertEquals(EnforcementState.NOT_ENFORCED_UNSUPPORTED, o.enforcement)
        assertTrue(o.message().contains("NOT enforced: no platform equivalent"))
    }

    @Test fun nativeSaveIsNotApplicableAndClearsThePlatform() {
        val b = RecordingBackend()
        val c = ProfileController(InMemoryProfileStore(), b)
        val o = c.save(app, DisplayProfile.NATIVE, display, false, true)
        assertEquals(EnforcementState.NOT_APPLICABLE_NATIVE, o.enforcement)
        assertEquals(listOf(app.packageName), b.cleared)
        assertTrue(b.applied.isEmpty())
    }

    @Test fun blockedAndUnacknowledgedSavesNeverReachTheBackend() {
        val b = RecordingBackend()
        val c = ProfileController(InMemoryProfileStore(), b)
        val blocked = c.save(AppTraits("x", isInputMethod = true), letterbox, display, false, true)
        assertEquals(SaveStatus.BLOCKED, blocked.status)
        assertTrue(blocked.message().startsWith("Not saved: blocked."))
        val noAck = c.save(app, DisplayProfile(AspectProfile.Ratio16x9Fill), display, false, false)
        assertEquals(SaveStatus.NEEDS_ACK, noAck.status)
        assertTrue(b.applied.isEmpty() && b.cleared.isEmpty())
    }

    @Test fun invalidPackageNamesAreRefusedBeforeAnythingIsStored() {
        val store = InMemoryProfileStore()
        val b = RecordingBackend()
        val c = ProfileController(store, b)
        for (bad in listOf(
            "",
            " ",
            "a b",
            "1abc.def",
            "a;b=c",
            "com..app",
            "com.app.",
            "x".repeat(256)
        )) {
            val o = c.save(AppTraits(bad), letterbox, display, false, true)
            assertEquals("package '$bad'", SaveStatus.BLOCKED, o.status)
            assertEquals("PACKAGE_INVALID", o.validation.issues.single().code)
        }
        assertTrue(store.packages().isEmpty())
        assertTrue(b.applied.isEmpty())
    }

    @Test fun packageNameValidityCoversNormalNames() {
        assertTrue(PackageNames.isValid("android"))
        assertTrue(PackageNames.isValid("com.example.app_2"))
        assertTrue(PackageNames.isValid("org.sableos.titan2.displaycompat"))
        assertFalse(PackageNames.isValid("com.example.app!"))
    }

    @Test fun aStoreThatForgetsAWriteIsReportedAndNothingIsApplied() {
        val b = RecordingBackend()
        val o = ProfileController(ForgetfulStore(), b).save(app, letterbox, display, false, true)
        assertFalse(o.saved)
        assertEquals(SaveStatus.STORAGE_FAILED, o.status)
        assertTrue(o.message().contains("could not be stored"))
        assertTrue(b.applied.isEmpty())
    }

    @Test fun aThrowingStoreIsReportedNotPropagated() {
        val o = ProfileController(
            ThrowingStore(),
            RecordingBackend()
        ).save(app, letterbox, display, false, true)
        assertEquals(SaveStatus.STORAGE_FAILED, o.status)
    }

    @Test fun aThrowingBackendIsReportedAsNotEnforced() {
        val b = RecordingBackend({ throw IllegalStateException("boom") })
        val o = ProfileController(
            InMemoryProfileStore(),
            b
        ).save(app, letterbox, display, false, true)
        assertTrue(o.saved)
        assertEquals(EnforcementState.NOT_ENFORCED_UNSUPPORTED, o.enforcement)
        assertTrue(o.message().contains("backend error: boom"))
    }

    @Test fun resetReportsAPlatformOverrideThatCouldNotBeCleared() {
        val store = InMemoryProfileStore()
        val b = RecordingBackend(onClear = { ApplyResult.Unsupported("SecurityException: denied") })
        val c = ProfileController(store, b)
        c.save(app, letterbox, display, false, true)
        val r = c.resetChecked(app.packageName)
        assertTrue(r.storeCleared)
        assertTrue(r.message().contains("could not be cleared: SecurityException: denied"))
        assertTrue(store.packages().isEmpty())
    }

    @Test fun resetWithClosedBackendIsPlainAndHonest() {
        val c = ProfileController(InMemoryProfileStore(), NoOpBackend)
        c.save(app, letterbox, display, false, true)
        assertEquals(
            "Reset to Native. The app was not launched.",
            c.resetChecked(app.packageName).message()
        )
    }

    @Test fun resetReportsAStoreThatCouldNotRemoveTheProfile() {
        val sticky = object : ProfileStore {
            override fun get(pkg: String) = letterbox
            override fun put(pkg: String, p: DisplayProfile) = Unit
            override fun reset(pkg: String) = Unit
            override fun resetAll() = Unit
            override fun packages(): Set<String> = setOf("a")
        }
        val r = ProfileController(sticky, NoOpBackend).resetChecked("a")
        assertFalse(r.storeCleared)
        assertTrue(r.message().startsWith("Reset failed"))
    }

    @Test fun resetAllListsPlatformFailuresSortedAndStillClearsTheStore() {
        val store = InMemoryProfileStore()
        val b = RecordingBackend(onClear = {
            if (it ==
                "b.app"
            ) {
                ApplyResult.Unsupported("denied")
            } else {
                ApplyResult.NoOp
            }
        })
        val c = ProfileController(store, b)
        c.save(AppTraits("c.app"), letterbox, display, false, true)
        c.save(AppTraits("b.app"), letterbox, display, false, true)
        c.save(AppTraits("a.app"), letterbox, display, false, true)
        b.cleared.clear()
        val r = c.resetAllChecked()
        assertEquals(3, r.count)
        assertEquals(listOf("b.app"), r.platformFailures)
        assertEquals(listOf("a.app", "b.app", "c.app"), b.cleared)
        assertTrue(store.packages().isEmpty())
        assertTrue(r.message().contains("1 app(s): b.app"))
    }

    @Test fun resetAllWithNothingStoredIsZero() {
        val r = ProfileController(InMemoryProfileStore(), NoOpBackend).resetAllChecked()
        assertEquals(0, r.count)
        assertEquals("Reset 0 profile(s) to Native.", r.message())
    }

    @Test fun aStoredProfileThatBecameForbiddenIsNotEffective() {
        val store = InMemoryProfileStore()
        val c = ProfileController(store, NoOpBackend)
        c.save(app, letterbox, display, false, true)
        val later = AppTraits(app.packageName, isInputMethod = true)
        val loaded = c.load(later)
        assertEquals(StoredState.STORED_BLOCKED_NOT_EFFECTIVE, loaded.state)
        assertEquals(DisplayProfile.NATIVE, loaded.profile)
        assertTrue(loaded.message()!!.contains("ignored"))
        assertEquals(letterbox, store.get(app.packageName))
    }

    @Test fun anAllowedStoredProfileLoadsAsStored() {
        val c = ProfileController(InMemoryProfileStore(), NoOpBackend)
        c.save(app, letterbox, display, false, true)
        val loaded = c.load(app)
        assertEquals(StoredState.STORED, loaded.state)
        assertEquals(letterbox, loaded.profile)
        assertNull(loaded.message())
    }

    @Test fun noStoredProfileLoadsAsNone() {
        val loaded = ProfileController(InMemoryProfileStore(), NoOpBackend).load(app)
        assertEquals(StoredState.NONE, loaded.state)
        assertEquals(DisplayProfile.NATIVE, loaded.profile)
    }

    @Test fun aStoredCustomProfileIsNotBlockedJustBecauseAdvancedIsOffAtLoad() {
        val store = InMemoryProfileStore()
        val c = ProfileController(store, NoOpBackend)
        val custom = DisplayProfile(AspectProfile.Custom, customRatio = Ratio(5, 4))
        c.save(app, custom, display, true, true)
        assertEquals(StoredState.STORED, c.load(app).state)
    }

    @Test fun codecDistrustsAnUnknownOrMissingProfileId() {
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode("v=1;p=bogus;o=Portrait;x=1"))
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode("v=1;o=Portrait;x=1"))
    }

    @Test fun codecDropsAnOutOfRangeRatioAndRejectsACustomProfileWithoutOne() {
        assertNull(ProfileCodec.decode("v=1;p=16_9_letterbox;r=100:1").customRatio)
        assertNull(ProfileCodec.decode("v=1;p=16_9_letterbox;r=1:100").customRatio)
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode("v=1;p=custom;r=100:1"))
        assertEquals(Ratio(16, 9), ProfileCodec.decode("v=1;p=custom;r=16:9").customRatio)
    }

    @Test fun codecToleratesEmptyAndNullPayloads() {
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode(null))
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode(""))
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode(";;;"))
    }

    @Test fun enforcementHasNoEnforcedStateAndWordingNeverClaimsOne() {
        assertFalse(
            EnforcementState.entries.any {
                it.name == "ENFORCED" ||
                    it.name.endsWith("_ENFORCED")
            }
        )
        for (s in EnforcementState.entries) {
            val t = EnforcementText.describe(
                s,
                ApplyResult.Applied("x")
            ).lowercase().replace("not enforced", "")
            assertFalse("state $s", t.contains("enforced") || t.contains("applied"))
        }
    }

    @Test fun backendBannersDistinguishClosedFromOpen() {
        assertTrue(BackendAvailability.CLOSED.banner.contains("nothing is enforced"))
        assertTrue(BackendAvailability.OPEN_UNVERIFIED.banner.contains("not verified"))
    }

    @Test fun miniModeIsStoredButNeverReportedAsApplied() {
        val b =
            RecordingBackend({
                ApplyResult.Unsupported("mini: no platform equivalent; stored only")
            })
        val o = ProfileController(
            InMemoryProfileStore(),
            b
        ).save(app, DisplayProfile(AspectProfile.MiniMode), display, false, true)
        assertEquals(EnforcementState.NOT_ENFORCED_UNSUPPORTED, o.enforcement)
        assertTrue(o.validation.issues.any { it.code == "MINI_SIZE" })
    }

    @Test fun savingNativeRemovesAStoredProfile() {
        val store = InMemoryProfileStore()
        val c = ProfileController(store, NoOpBackend)
        c.save(app, letterbox, display, false, true)
        assertEquals(setOf(app.packageName), store.packages())
        c.save(app, DisplayProfile.NATIVE, display, false, true)
        assertTrue(store.packages().isEmpty())
    }

    @Test fun repeatedSavesKeepTheLatestProfileOnly() {
        val store = InMemoryProfileStore()
        val c = ProfileController(store, NoOpBackend)
        c.save(app, letterbox, display, false, true)
        c.save(app, DisplayProfile(AspectProfile.SquareSafe), display, false, true)
        assertEquals(AspectProfile.SquareSafe, store.get(app.packageName).profile)
        assertEquals(1, store.packages().size)
    }
}
