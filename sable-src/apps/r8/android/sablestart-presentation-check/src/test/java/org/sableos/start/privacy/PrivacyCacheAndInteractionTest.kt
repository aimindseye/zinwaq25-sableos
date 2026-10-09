package org.sableos.start.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DESIGN-KF-D: user-scoped ephemeral cache, keyboard behaviour and app actions. */
class PrivacyCacheAndInteractionTest {
    private val camera = AppPrivacy(mapOf(PrivacyGroup.Camera to GroupState.Allowed))
    private val none = AppPrivacy(emptyMap())

    @Test
    fun samePackageInPersonalAndWorkProfileIsCachedSeparately() {
        val cache = PrivacySnapshotCache(clock = { 0L })
        val personal = AppInstanceKey(0, "org.example.maps")
        val work = AppInstanceKey(10, "org.example.maps")
        assertEquals(camera, cache.getOrCompute(personal) { camera })
        assertEquals(none, cache.getOrCompute(work) { none })
        assertEquals(camera, cache.peek(personal))
        assertEquals(none, cache.peek(work))
        assertEquals(2, cache.size)
    }

    @Test
    fun cachedValueIsReusedUntilInvalidatedOrStale() {
        var now = 0L
        var computations = 0
        val cache = PrivacySnapshotCache(maxAgeMs = 1_000, clock = { now })
        val key = AppInstanceKey(0, "a")
        val compute = {
            computations++
            camera
        }
        cache.getOrCompute(key, compute)
        cache.getOrCompute(key, compute)
        assertEquals(1, computations)
        cache.invalidate(0, "a")
        cache.getOrCompute(key, compute)
        assertEquals(2, computations)
        now = 2_000
        cache.getOrCompute(key, compute)
        assertEquals(3, computations)
        // A clock that went backwards never serves a "future" entry.
        now = 10
        cache.getOrCompute(key, compute)
        assertEquals(4, computations)
    }

    @Test
    fun invalidationIsScopedToUserAndPackages() {
        val cache = PrivacySnapshotCache(clock = { 0L })
        listOf(AppInstanceKey(0, "a"), AppInstanceKey(0, "b"), AppInstanceKey(10, "a")).forEach { k ->
            cache.getOrCompute(k) { none }
        }
        cache.invalidate(10, "a")
        assertTrue(cache.contains(AppInstanceKey(0, "a")))
        assertFalse(cache.contains(AppInstanceKey(10, "a")))
        cache.invalidatePackages(0, listOf("a", "b"))
        assertEquals(0, cache.size)

        listOf(AppInstanceKey(0, "a"), AppInstanceKey(10, "a"), AppInstanceKey(10, "b")).forEach { k ->
            cache.getOrCompute(k) { none }
        }
        cache.invalidateUser(10)
        assertEquals(1, cache.size)
        cache.retainOnly(emptySet())
        assertEquals(0, cache.size)
    }

    @Test
    fun unreadableResultIsCachedAsUnavailableNotRetriedEveryBind() {
        var computations = 0
        val cache = PrivacySnapshotCache(clock = { 0L })
        val key = AppInstanceKey(0, "x")
        assertNull(
            cache.getOrCompute(key) {
                computations++
                null
            },
        )
        assertNull(cache.getOrCompute(key) { error("must not recompute") })
        assertEquals(1, computations)
    }

    @Test
    fun uidMapsToUser() {
        assertEquals(0, PrivacySnapshotCache.userIdOfUid(10_123))
        assertEquals(10, PrivacySnapshotCache.userIdOfUid(1_010_123))
    }

    @Test
    fun keyboardMapping() {
        val k = AllAppsKeyPolicy
        assertEquals(AllAppsKeyAction.Open, k.decide(k.KEYCODE_ENTER, 0, 0).action)
        assertEquals(AllAppsKeyAction.Actions, k.decide(k.KEYCODE_ENTER, 0, k.META_FUNCTION_ON).action)
        assertEquals(AllAppsKeyAction.Actions, k.decide(k.KEYCODE_MENU, 0, 0).action)
        assertEquals(AllAppsKeyAction.ToggleDetail, k.decide(k.KEYCODE_SPACE, ' '.code, 0).action)
        assertEquals(AllAppsKeyAction.Search, k.decide(k.KEYCODE_SEARCH, 0, 0).action)
        assertEquals(AllAppsKeyAction.Search, k.decide(k.KEYCODE_SLASH, '/'.code, 0).action)
        val jump = k.decide(29, 'm'.code, 0)
        assertEquals(AllAppsKeyAction.TypeToJump, jump.action)
        assertEquals('m', jump.char)
        // Up/Down stay with platform focus traversal; Ctrl shortcuts and text fields pass through.
        assertFalse(k.decide(19, 0, 0).consumed)
        assertFalse(k.decide(29, 'a'.code, k.META_CTRL_ON).consumed)
        assertFalse(k.decide(k.KEYCODE_ENTER, 0, 0, editableFocused = true).consumed)
        assertFalse(k.decide(56, '.'.code, 0).consumed)
    }

    @Test
    fun heldKeysDoNotRepeatOneShotActions() {
        val k = AllAppsKeyPolicy
        val held = k.decide(k.KEYCODE_ENTER, 0, 0, repeat = true)
        assertEquals(AllAppsKeyAction.IgnoreRepeat, held.action)
        assertTrue(held.consumed)
        assertEquals(AllAppsKeyAction.IgnoreRepeat, k.decide(29, 'a'.code, 0, repeat = true).action)
    }

    @Test
    fun appActionsAreCompleteAndDestructiveOnesNeedConfirmation() {
        val actions =
            AppActionPolicy.actions(
                AppActionContext(pinnedToStart = false, inBaseBar = false, launcherUser = true, canUninstall = true),
            )
        assertEquals(
            listOf(
                AppAction.Open,
                AppAction.AppInfo,
                AppAction.NotificationSettings,
                AppAction.PinToStart,
                AppAction.AddToBaseBar,
                AppAction.Uninstall,
            ),
            actions,
        )
        assertTrue(AppActionPolicy.requiresConfirmation(AppAction.Uninstall))
        assertTrue(AppActionPolicy.requiresConfirmation(AppAction.Disable))
        assertFalse(AppActionPolicy.requiresConfirmation(AppAction.Open))
        assertEquals("confirm uninstall", AppActionPolicy.confirmationLabel(AppAction.Uninstall))
    }

    @Test
    fun workProfileAndPinnedVariants() {
        val actions =
            AppActionPolicy.actions(
                AppActionContext(pinnedToStart = true, inBaseBar = null, launcherUser = false, canUninstall = false),
            )
        assertEquals(listOf(AppAction.Open, AppAction.AppInfo, AppAction.RemoveFromStart), actions)
    }
}
