package org.sableos.test.frameworks.base

import com.android.keyguard.sable.SableKeyguardKeys
import com.android.keyguard.sable.SableLockGlance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableLockGlanceTest {
    private val keySpace = 62
    private val keyE = 33
    private val altLeft = 0x02 or 0x10
    private val altRight = 0x02 or 0x20

    @Test
    fun categoriesPickTheHubBucket() {
        assertEquals(SableLockGlance.BUCKET_MESSAGES, SableLockGlance.bucketFor("msg", "any.app", false, false))
        assertEquals(SableLockGlance.BUCKET_MAIL, SableLockGlance.bucketFor("email", "any.app", false, false))
        assertEquals(
            SableLockGlance.BUCKET_MISSED_CALL,
            SableLockGlance.bucketFor("missed_call", "com.android.dialer", false, false),
        )
    }

    @Test
    fun knownAppsWithoutCategoryAreCounted() {
        assertEquals(SableLockGlance.BUCKET_MAIL, SableLockGlance.bucketFor(null, "org.sableos.mail", false, false))
        assertEquals(SableLockGlance.BUCKET_MAIL, SableLockGlance.bucketFor("", "com.fsck.k9", false, false))
        assertEquals(
            SableLockGlance.BUCKET_MESSAGES,
            SableLockGlance.bucketFor(null, "org.sableos.messages", false, false),
        )
        assertEquals(SableLockGlance.BUCKET_NONE, SableLockGlance.bucketFor(null, "org.example.game", false, false))
    }

    @Test
    fun anotherCategoryWinsOverThePackage() {
        // A mail app's calendar reminder is not mail.
        assertEquals(SableLockGlance.BUCKET_NONE, SableLockGlance.bucketFor("event", "com.fsck.k9", false, false))
    }

    @Test
    fun summariesAndOngoingAreNeverCounted() {
        assertEquals(SableLockGlance.BUCKET_NONE, SableLockGlance.bucketFor("msg", "a.b", true, false))
        assertEquals(SableLockGlance.BUCKET_NONE, SableLockGlance.bucketFor("msg", "a.b", false, true))
    }

    @Test
    fun countsMatchTheDesignRow() {
        val counts = SableLockGlance.Counts()
        counts.add(SableLockGlance.BUCKET_MESSAGES)
        counts.add(SableLockGlance.BUCKET_MESSAGES)
        counts.add(SableLockGlance.BUCKET_MAIL)
        counts.add(SableLockGlance.BUCKET_MISSED_CALL)
        counts.add(SableLockGlance.BUCKET_NONE)
        assertEquals(2, counts.messages)
        assertEquals(1, counts.mail)
        assertEquals(1, counts.missedCalls)
        assertEquals(5, counts.total)
        assertFalse(counts.isEmpty())
    }

    @Test
    fun countRowHiddenWhenZero() {
        val counts = SableLockGlance.Counts()
        counts.add(SableLockGlance.BUCKET_NONE)
        val glance = SableLockGlance.Glance(false, true, false, counts)
        assertTrue(counts.isEmpty())
        assertFalse(glance.countRow)
        assertTrue(glance.lockedLabel)
    }

    @Test
    fun privateModeShowsCardAndUnlockLine() {
        val counts = SableLockGlance.Counts()
        counts.add(SableLockGlance.BUCKET_MAIL)
        val glance = SableLockGlance.Glance(false, true, true, counts)
        assertTrue(glance.privateCard)
        assertTrue(glance.requiresUnlockLine)
        // No notifications: the card stays, the unlock line goes.
        val empty = SableLockGlance.Glance(false, true, true, SableLockGlance.Counts())
        assertTrue(empty.privateCard)
        assertFalse(empty.requiresUnlockLine)
    }

    @Test
    fun nothingWhileDozing() {
        val counts = SableLockGlance.Counts()
        counts.add(SableLockGlance.BUCKET_MESSAGES)
        assertFalse(SableLockGlance.Glance(true, true, true, counts).any())
    }

    @Test
    fun newCountUsesNumberThenMessages() {
        assertEquals(3, SableLockGlance.newCount(3, 7))
        assertEquals(2, SableLockGlance.newCount(0, 2))
        assertEquals(1, SableLockGlance.newCount(0, 0))
    }

    @Test
    fun emergencyChordIsAltSpaceOnly() {
        assertTrue(SableKeyguardKeys.isEmergencyChord(keySpace, altLeft))
        // Sym is Right Alt on the Q25.
        assertTrue(SableKeyguardKeys.isEmergencyChord(keySpace, altRight))
        assertFalse(SableKeyguardKeys.isEmergencyChord(keySpace, 0))
        assertFalse(SableKeyguardKeys.isEmergencyChord(keySpace, altLeft or 0x01))
        // Alt+E types 2 and Sym+E pages: never the emergency chord.
        assertFalse(SableKeyguardKeys.isEmergencyChord(keyE, altLeft))
        assertFalse(SableKeyguardKeys.isEmergencyChord(keyE, altRight))
    }

    @Test
    fun emergencyChordNeverTypesAPinDigit() {
        // Alt+Space: the Q25 key map gives a private-use character, not a digit.
        assertEquals(-1, SableKeyguardKeys.pinDigitFor(keySpace, 0xef01, ' '.code, altLeft))
        assertFalse(SableKeyguardKeys.isTypeAheadKey(keySpace, altLeft, 0, 0xef01, ' '.code))
    }
}
