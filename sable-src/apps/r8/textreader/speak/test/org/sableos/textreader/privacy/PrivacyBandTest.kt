package org.sableos.textreader.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyBandTest {
    @Test
    fun `band is centred on the focused item and at least the minimum height`() {
        assertEquals(Band(260, 460), PrivacyBand.bandFor(340, 380, 720, 200, 16))
        // A tall item gets its own height plus padding.
        assertEquals(Band(84, 516), PrivacyBand.bandFor(100, 500, 720, 200, 16))
    }

    @Test
    fun `band stays inside the screen`() {
        assertEquals(Band(0, 200), PrivacyBand.bandFor(0, 40, 720, 200, 16))
        assertEquals(Band(520, 720), PrivacyBand.bandFor(690, 720, 720, 200, 16))
        assertEquals(Band(0, 720), PrivacyBand.bandFor(0, 2000, 720, 200, 16))
    }

    @Test
    fun `no focus puts the band in the middle`() {
        assertEquals(Band(260, 460), PrivacyBand.bandFor(null, null, 720, 200, 16))
        assertEquals(Band(260, 460), PrivacyBand.bandFor(300, 300, 720, 200, 16))
    }

    @Test
    fun `never shown while locked, with the screen off or over a call`() {
        assertTrue(PrivacyBand.mayShow(true, false, true, "org.sableos.messages"))
        assertFalse(PrivacyBand.mayShow(false, false, true, "org.sableos.messages"))
        assertFalse(PrivacyBand.mayShow(true, true, true, "org.sableos.messages"))
        assertFalse(PrivacyBand.mayShow(true, false, false, "org.sableos.messages"))
        assertFalse(PrivacyBand.mayShow(true, false, true, "org.sableos.phone"))
        assertFalse(PrivacyBand.mayShow(true, false, true, "com.android.server.telecom"))
    }
}
