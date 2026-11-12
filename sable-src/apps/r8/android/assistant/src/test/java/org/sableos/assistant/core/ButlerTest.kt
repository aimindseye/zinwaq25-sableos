package org.sableos.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButlerTest {
    @Test
    fun aTypedTextNeedsTheWordAndAColon() {
        assertEquals(ButlerAction.SendSms("Anna", "running late"), Butler.typed("text Anna: running late"))
        assertEquals(ButlerAction.SendSms("+1 555 0100", "on my way"), Butler.typed("SMS +1 555 0100: on my way"))
        assertEquals(ButlerAction.SendSms("Anna Lee", "10:30 works"), Butler.typed("text Anna Lee: 10:30 works"))
        // Questions stay questions.
        assertNull(Butler.typed("text me a poem"))
        assertNull(Butler.typed("what is a text: message?"))
        assertNull(Butler.typed("text Anna:"))
        assertNull(Butler.typed("text : hello"))
    }

    @Test
    fun theModelProposesOnALineOfItsOwn() {
        assertEquals(
            ButlerAction.SendSms("Anna", "Running ten minutes late."),
            Butler.proposed("SEND_SMS Anna :: Running ten minutes late."),
        )
        assertEquals(
            ButlerAction.SendSms("Mum", "Home by six"),
            Butler.proposed("Certainly.\n`SEND_SMS \"Mum\" :: \"Home by six\"`"),
        )
        assertNull(Butler.proposed("I would write SEND_SMS but I won't."))
        assertNull(Butler.proposed("SEND_SMS Anna running late"))
        assertNull(Butler.proposed("Here is your answer."))
    }

    @Test
    fun longTextsAreCutAndLongNamesRefused() {
        val long = "x".repeat(Butler.MAX_BODY + 50)
        assertEquals(Butler.MAX_BODY, Butler.typed("text Anna: $long")!!.body.length)
        assertNull(Butler.typed("text ${"n".repeat(Butler.MAX_TO + 1)}: hi"))
    }

    @Test
    fun numbersAreTheirOwnRecipient() {
        assertTrue(Butler.isNumber("+1 (555) 010-0100"))
        assertTrue(Butler.isNumber("911"))
        assertFalse(Butler.isNumber("Anna"))
        assertFalse(Butler.isNumber("12"))
    }

    @Test
    fun thePersonaSaysHowToProposeAText() {
        assertTrue(Persona.system("Cosgrove").contains(Butler.SMS_TAG))
    }
}
