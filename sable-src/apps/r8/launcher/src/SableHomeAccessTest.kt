package io.github.aimindseye.tenseven.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableHomeAccessTest {
    @Test
    fun theHomeAsksForWhatItsPagesUseButNotLocation() {
        val items = SableHomeAccess.items(36)
        assertTrue("android.permission.READ_CONTACTS" in items)
        assertTrue("android.permission.READ_MEDIA_IMAGES" in items)
        assertTrue("android.permission.POST_NOTIFICATIONS" in items)
        assertTrue(items.containsAll(listOf("listener", "usage", "overlay")))
        assertFalse(items.any { "LOCATION" in it })
    }

    @Test
    fun whatWasGrantedOnceIsNeverGrantedAgain() {
        val all = SableHomeAccess.items(36)
        assertEquals(all, SableHomeAccess.pending(all, emptySet()))
        assertEquals(listOf("overlay"), SableHomeAccess.pending(all, (all - "overlay").toSet()))
    }
}
