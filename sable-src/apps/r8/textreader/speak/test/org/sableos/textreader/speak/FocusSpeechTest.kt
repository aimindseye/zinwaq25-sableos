package org.sableos.textreader.speak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSpeechTest {
    @Test
    fun `button speaks its label and role`() {
        val facts = FocusFacts(text = "Send", role = FocusSpeech.roleOf("android.widget.Button", false, false))
        assertEquals("Send, button", FocusSpeech.utterance(facts))
    }

    @Test
    fun `switch speaks on or off and description wins over text`() {
        val role = FocusSpeech.roleOf("android.widget.Switch", false, true)
        assertEquals(FocusRole.Switch, role)
        val facts = FocusFacts(text = "ON", description = "Wi-Fi", role = role, checked = true)
        assertEquals("Wi-Fi, switch, on", FocusSpeech.utterance(facts))
    }

    @Test
    fun `edit box speaks its hint and what is typed`() {
        val role = FocusSpeech.roleOf("android.widget.EditText", true, false)
        assertEquals("Search, edit box, cafe", FocusSpeech.utterance(FocusFacts(text = "cafe", hint = "Search", role = role)))
        assertEquals("Search, edit box", FocusSpeech.utterance(FocusFacts(hint = "Search", role = role)))
    }

    @Test
    fun `password field is named but never read`() {
        val facts = FocusFacts(text = "hunter2", hint = "PIN", role = FocusRole.Edit, password = true)
        val spoken = FocusSpeech.utterance(facts)
        assertEquals("PIN, Password field", spoken)
        assertFalse(spoken!!.contains("hunter2"))
    }

    @Test
    fun `nothing is spoken while locked`() {
        assertNull(FocusSpeech.utterance(FocusFacts(text = "Mum: see you at 6", locked = true)))
    }

    @Test
    fun `an empty item is silent and long text is capped`() {
        assertNull(FocusSpeech.utterance(FocusFacts(text = "  ")))
        val long = FocusSpeech.utterance(FocusFacts(text = "word ".repeat(200)))!!
        assertEquals(FocusSpeech.MAX_CHARS, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `an echo of the same item is dropped`() {
        assertFalse(FocusSpeech.shouldSpeak("Send, button", 1_000, "Send, button", 1_200))
        assertTrue(FocusSpeech.shouldSpeak("Send, button", 1_000, "Send, button", 2_000))
        assertTrue(FocusSpeech.shouldSpeak("Send, button", 1_000, "Back, button", 1_100))
    }
}
