package org.sableos.messages.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressesTest {
    @Test
    fun normalizeDropsSeparatorsAndKeepsLeadingPlus() {
        assertEquals("+15550101010", Addresses.normalize(" +1 (555) 010-1010 "))
        assertEquals("07700900123", Addresses.normalize("07700 900.123"))
        assertEquals("15550101010", Addresses.normalize("1+555 010 1010"))
        assertEquals("+447700900123", Addresses.normalize("+447700900123/TYPE=PLMN"))
    }

    @Test
    fun alphanumericSendersStayAsTheyAre() {
        assertEquals("BANK", Addresses.normalize("BANK"))
        assertEquals("ana@example.org", Addresses.normalize(" ana@example.org "))
    }

    @Test
    fun sameMatchesAcrossCountryPrefixAndFormatting() {
        assertTrue(Addresses.same("+44 7700 900123", "07700900123"))
        assertTrue(Addresses.same("(555) 010-1010", "+15550101010"))
        assertFalse(Addresses.same("+15550101010", "+15550101011"))
        assertFalse(Addresses.same("", ""))
        assertTrue(Addresses.same("Vodafone", "VODAFONE"))
    }

    @Test
    fun shortCodesMatchOnlyExactly() {
        assertTrue(Addresses.same("12345", "12345"))
        assertFalse(Addresses.same("12345", "112345"))
    }

    @Test
    fun sendableNeedsDigits() {
        assertTrue(Addresses.isSendable("+1 555 010 1010"))
        assertTrue(Addresses.isSendable("611"))
        assertFalse(Addresses.isSendable("Maya"))
        assertFalse(Addresses.isSendable("12"))
        assertFalse(Addresses.isSendable(""))
    }

    @Test
    fun splitRecipientsDropsDuplicatesAndBlanks() {
        assertEquals(
            listOf("+15550101010", "5550202020"),
            Addresses.splitRecipients("+1 555 010 1010; 555-020-2020, ,(555) 010-1010"),
        )
    }

    @Test
    fun smsLinkRecipientsAndBody() {
        val request = SmsLinks.parse("+15550101010,+15550202020?body=On my way", smsBodyExtra = null)
        assertEquals(listOf("+15550101010", "+15550202020"), request.recipients)
        assertEquals("On my way", request.body)
    }

    @Test
    fun smsBodyExtraWinsOverQueryAndText() {
        assertEquals("extra", SmsLinks.parse("5550101010?body=query", "extra", "text").body)
        assertEquals("text", SmsLinks.parse("5550101010", null, "text").body)
        assertEquals("", SmsLinks.parse(null, null).body)
        assertTrue(SmsLinks.parse("", null).recipients.isEmpty())
    }

    @Test
    fun messagingSchemes() {
        listOf("sms", "SMSTO", "mms", "mmsto").forEach { assertTrue(it, SmsLinks.isMessagingScheme(it)) }
        assertFalse(SmsLinks.isMessagingScheme("tel"))
        assertFalse(SmsLinks.isMessagingScheme(null))
    }
}
