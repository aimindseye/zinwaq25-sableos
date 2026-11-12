package org.sableos.test.frameworks.base

import com.android.server.compat.sable.SableCodeLoading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableCodeLoadingTest {
    @Test
    fun plutoIsOnTheListUntilAppCompatibilitySetsOne() {
        assertTrue(SableCodeLoading.allows(SableCodeLoading.DEFAULT_LIST, "tv.pluto.android"))
        assertFalse(SableCodeLoading.allows(SableCodeLoading.DEFAULT_LIST, "com.example"))
        assertEquals(218865702L, SableCodeLoading.CHANGE_ID)
    }

    @Test
    fun onlyWholeNamesOnTheListCount() {
        val list = "tv.pluto.android, com.example.ads"
        assertTrue(SableCodeLoading.allows(list, "com.example.ads"))
        assertFalse(SableCodeLoading.allows(list, "com.example"))
        assertFalse(SableCodeLoading.allows(list, "tv.pluto"))
        assertFalse(SableCodeLoading.allows("-", "tv.pluto.android"))
        assertFalse(SableCodeLoading.allows("", "tv.pluto.android"))
        assertFalse(SableCodeLoading.allows(null, "tv.pluto.android"))
        assertFalse(SableCodeLoading.allows(list, ""))
    }
}
