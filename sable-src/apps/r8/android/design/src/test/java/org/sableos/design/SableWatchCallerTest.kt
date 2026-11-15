package org.sableos.design

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableWatchCallerTest {
    private val pinned = SableWatchCaller.certificate()

    @Test
    fun certificateIsTheSha256Bytes() {
        assertEquals(32, pinned.size)
        assertEquals(0x9b.toByte(), pinned.first())
        assertEquals(0x92.toByte(), pinned.last())
    }

    @Test
    fun sableWatchWithItsKeyIsAllowed() {
        assertTrue(SableWatchCaller.matches(listOf(SableWatchCaller.PACKAGE)) { _, cert -> cert.contentEquals(pinned) })
    }

    @Test
    fun theSamePackageWithAnotherKeyIsRefused() {
        assertFalse(SableWatchCaller.matches(listOf(SableWatchCaller.PACKAGE)) { _, _ -> false })
    }

    @Test
    fun otherPackagesAreRefusedWithoutAskingForTheirKey() {
        var asked = false
        assertFalse(
            SableWatchCaller.matches(listOf("com.example.watch")) { _, _ ->
                asked = true
                true
            },
        )
        assertFalse(asked)
    }

    @Test
    fun theCheckAsksAboutSableWatchsOwnPackage() {
        SableWatchCaller.matches(listOf("other", SableWatchCaller.PACKAGE)) { pkg, cert ->
            assertEquals(SableWatchCaller.PACKAGE, pkg)
            assertArrayEquals(pinned, cert)
            true
        }
    }
}
