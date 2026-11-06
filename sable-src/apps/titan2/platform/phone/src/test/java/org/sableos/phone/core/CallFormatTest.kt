package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallFormatTest {
    @Test
    fun durations() {
        assertEquals("0:07", CallFormat.duration(7))
        assertEquals("12:45", CallFormat.duration(765))
        assertEquals("1:02:03", CallFormat.duration(3723))
        assertEquals("0:00", CallFormat.duration(-5))
    }

    @Test
    fun dialableKeepsOnlyWhatIsDialled() {
        assertEquals("+15550101", CallFormat.dialable("+1 (555) 01-01"))
        assertEquals("*21*123#", CallFormat.dialable("*21*123#"))
        assertEquals("12,34", CallFormat.dialable("1+2,34"))
    }

    @Test
    fun sameNumberAcrossFormats() {
        assertTrue(CallFormat.sameNumber("+44 7700 900123", "07700 900123"))
        assertFalse(CallFormat.sameNumber("", ""))
        assertFalse(CallFormat.sameNumber("112", "911"))
    }

    @Test
    fun specialCodes() {
        assertEquals(SpecialCodes.Code.ShowImei, SpecialCodes.parse("*#06#"))
        assertEquals(SpecialCodes.Code.Secret("4636"), SpecialCodes.parse("*#*#4636#*#*"))
        assertNull(SpecialCodes.parse("*#*#46"))
        assertNull(SpecialCodes.parse("*21#"))
    }
}
