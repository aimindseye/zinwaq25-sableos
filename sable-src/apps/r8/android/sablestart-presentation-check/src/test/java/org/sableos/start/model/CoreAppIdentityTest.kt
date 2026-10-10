package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9 daily-driver core apps keep their Sable identity on LineageOS (docs/implementation/daily-driver.md). */
class CoreAppIdentityTest {
    @Test
    fun lineageOsCoreAppsMapToTheR9Roles() {
        assertEquals(CoreAppIdentity.PHOTOS, CoreAppIdentity.canonicalPackage("org.lineageos.glimpse"))
        assertEquals(CoreAppIdentity.CAMERA, CoreAppIdentity.canonicalPackage("org.lineageos.aperture"))
        assertEquals(CoreAppIdentity.BROWSER, CoreAppIdentity.canonicalPackage("org.lineageos.jelly"))
    }

    @Test
    fun appsSharedByBothBasesAndCanonicalPackagesAreUnchanged() {
        for (pkg in listOf(
            CoreAppIdentity.PHOTOS,
            CoreAppIdentity.CAMERA,
            CoreAppIdentity.BROWSER,
            CoreAppIdentity.FILES,
            CoreAppIdentity.CLOCK,
        )) {
            assertEquals(pkg, CoreAppIdentity.canonicalPackage(pkg))
        }
    }

    @Test
    fun unrelatedAndSableAppsAreNotRemapped() {
        for (pkg in listOf(
            "org.sableos.titan2.camera",
            "org.sableos.media",
            "org.example.gallery",
            "",
        )) {
            assertEquals(pkg, CoreAppIdentity.canonicalPackage(pkg))
        }
        assertFalse(CoreAppIdentity.isRole("org.sableos.titan2.camera", CoreAppIdentity.CAMERA))
        assertFalse(CoreAppIdentity.isRole("org.lineageos.glimpse", CoreAppIdentity.CAMERA))
    }

    @Test
    fun roleMembership() {
        assertTrue(CoreAppIdentity.isRole("org.lineageos.glimpse", CoreAppIdentity.PHOTOS))
        assertTrue(CoreAppIdentity.isRole("org.lineageos.aperture", CoreAppIdentity.CAMERA))
        assertTrue(CoreAppIdentity.isRole(CoreAppIdentity.PHOTOS, CoreAppIdentity.PHOTOS))
        assertFalse(CoreAppIdentity.isRole("com.android.gallery2", CoreAppIdentity.PHOTOS))
    }

    @Test
    fun cameraTilePrefersSableCameraThenTheBaseCamera() {
        val expected = listOf(CoreAppIdentity.SABLE_CAMERA, CoreAppIdentity.CAMERA)
        assertEquals(expected, CoreAppIdentity.startTileCandidates(CoreAppIdentity.CAMERA))
        assertEquals(expected, CoreAppIdentity.startTileCandidates(CoreAppIdentity.SABLE_CAMERA))
        assertEquals("org.sableos.titan2.camera", CoreAppIdentity.SABLE_CAMERA)
    }

    @Test
    fun otherTilesHaveOneCandidate() {
        assertEquals(listOf(CoreAppIdentity.PHOTOS), CoreAppIdentity.startTileCandidates(CoreAppIdentity.PHOTOS))
        assertEquals(listOf("org.sableos.hub"), CoreAppIdentity.startTileCandidates("org.sableos.hub"))
    }
}
