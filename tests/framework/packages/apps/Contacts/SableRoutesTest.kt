package com.android.contacts.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the route subtitle, Hub route and privacy (Contacts patches 0753, 0754). */
class SableRoutesTest {
    private val words = SableRoutes.Words("Phone", "Mail", "Hub")
    private val lri = '\u2066'
    private val pdi = '\u2069'

    private fun sub(
        number: String? = "+1 555 0101",
        mail: Boolean = false,
        hub: Boolean = false,
        hide: Boolean = false,
    ) = SableRoutes.subtitle("Mobile", number, mail, hub, hide, words)

    @Test fun numberThenOtherRoutes() {
        assertEquals("Mobile · ${lri}+1 555 0101$pdi", sub())
        assertEquals("Mobile · ${lri}+1 555 0101$pdi · Mail · Hub", sub(mail = true, hub = true))
        // No number: no Hub route either.
        assertEquals("Mail", sub(number = null, mail = true, hub = true))
        assertNull(sub(number = "  ", hub = true))
    }

    @Test fun hiddenDetailsLeaveOnlyRoutes() {
        assertEquals("Phone · Mail · Hub", sub(mail = true, hub = true, hide = true))
        assertEquals("Phone", sub(hide = true))
        assertFalse(sub(hide = true, mail = true)!!.contains("555"))
    }

    @Test fun hubAddressNeedsADialableShortNumber() {
        assertTrue(SableRoutes.hubAddressUsable(" +1 (555) 0101 "))
        assertFalse(SableRoutes.hubAddressUsable("---"))
        assertFalse(SableRoutes.hubAddressUsable(null))
        assertFalse(SableRoutes.hubAddressUsable("1".repeat(SableRoutes.MAX_HUB_ADDRESS_LENGTH + 1)))
    }

    @Test fun privateModeFromHubFailsClosed() {
        assertFalse(SableRoutes.privateMode(null))
        assertFalse(SableRoutes.privateMode(0))
        assertTrue(SableRoutes.privateMode(1))
        assertTrue(SableRoutes.privateMode(-1))
        assertTrue(SableRoutes.hideDetails(true, false))
        assertTrue(SableRoutes.hideDetails(false, true))
        assertFalse(SableRoutes.hideDetails(false, false))
    }

    @Test fun hubNamesMatchSableHub() {
        assertEquals("org.sableos.hub", SableRoutes.HUB_PACKAGE)
        assertEquals("org.sableos.hub.action.OPEN_CONVERSATION", SableRoutes.ACTION_OPEN_CONVERSATION)
        assertEquals("org.sableos.hub.extra.ADDRESS", SableRoutes.EXTRA_ADDRESS)
        assertEquals("org.sableos.hub.privacy", SableRoutes.PRIVACY_AUTHORITY)
        assertEquals(64, SableRoutes.MAX_HUB_ADDRESS_LENGTH)
    }
}
