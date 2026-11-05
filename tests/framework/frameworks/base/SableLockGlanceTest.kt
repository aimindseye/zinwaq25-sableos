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
        // The peek shows, with its "Unlock to read message details" footer instead of the card.
        assertTrue(glance.countRow)
        assertFalse(glance.privateCard)
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

    @Test
    fun peekGroupsByAppMostFirstThenNewest() {
        val peek = SableLockGlance.Peek()
        peek.add("org.sableos.messages", SableLockGlance.BUCKET_MESSAGES, 10L, null)
        peek.add("org.sableos.messages", SableLockGlance.BUCKET_MESSAGES, 30L, null)
        peek.add("com.whatsapp", SableLockGlance.BUCKET_MESSAGES, 50L, "Maya")
        peek.add("org.thoughtcrime.securesms", SableLockGlance.BUCKET_MESSAGES, 40L, " ")
        peek.add("com.android.calendar", SableLockGlance.BUCKET_NONE, 60L, "Lunch")
        val rows = peek.rows()
        assertEquals(listOf("org.sableos.messages", "com.whatsapp", "org.thoughtcrime.securesms"), rows.map { it.packageName })
        assertEquals(2, rows[0].count)
        assertEquals("Maya", rows[1].sender)
        assertEquals(null, rows[2].sender)
        assertEquals(0, peek.moreApps())
    }

    @Test
    fun peekShowsTheNewestSenderAndCapsRows() {
        val peek = SableLockGlance.Peek()
        peek.add("com.whatsapp", SableLockGlance.BUCKET_MESSAGES, 5L, "Old")
        peek.add("com.whatsapp", SableLockGlance.BUCKET_MESSAGES, 9L, "New")
        peek.add("com.whatsapp", SableLockGlance.BUCKET_MESSAGES, 7L, "Middle")
        assertEquals("New", peek.rows()[0].sender)
        (1..6).forEach { peek.add("app.$it", SableLockGlance.BUCKET_MAIL, it.toLong(), null) }
        assertEquals(SableLockGlance.Peek.MAX_ROWS, peek.rows().size)
        assertEquals(3, peek.moreApps())
        assertTrue(SableLockGlance.Peek().isEmpty())
    }

    @Test
    fun nextEventShowsTodayTomorrowOrNowAndHidesItsName() {
        val zone = java.time.ZoneId.of("America/Toronto")
        val now = java.time.ZonedDateTime.of(2026, 11, 5, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val hour = 3_600_000L
        assertEquals(SableLockGlance.EVENT_NOW, SableLockGlance.eventDay(now - hour, false, now, zone))
        assertEquals(0, SableLockGlance.eventDay(now + hour, false, now, zone))
        assertEquals(1, SableLockGlance.eventDay(now + 20 * hour, false, now, zone))
        assertEquals(SableLockGlance.EVENT_LATER, SableLockGlance.eventDay(now + 48 * hour, false, now, zone))
        // All-day events are stored at UTC midnight.
        val todayUtc = java.time.LocalDate.of(2026, 11, 5).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(0, SableLockGlance.eventDay(todayUtc, true, now, zone))
        assertEquals(1, SableLockGlance.eventDay(todayUtc + 24 * hour, true, now, zone))
        assertEquals("Today 9:30 · Standup", SableLockGlance.eventLine("Today 9:30", " Standup ", false, "Event"))
        assertEquals("Today 9:30 · Event", SableLockGlance.eventLine("Today 9:30", "Standup", true, "Event"))
        assertEquals("Now · Event", SableLockGlance.eventLine("Now", " ", false, "Event"))
    }
}
