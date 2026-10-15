package org.sableos.test.frameworks.base

import com.android.keyguard.sable.SableKeyguardKeys
import com.android.keyguard.sable.SableKeyguardTypeAhead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableKeyguardKeysTest {
    private val alt = 0x02 or 0x10
    private val keyW = 51
    private val keyA = 29

    @Test
    fun altLetterDigitsTypePin() {
        // Alt+W gives '1' through the key map; the PIN view takes it as the digit 1.
        assertEquals(1, SableKeyguardKeys.digitFor('1'.code, 0, alt))
        assertEquals(0, SableKeyguardKeys.digitFor('0'.code, 0, 0))
    }

    @Test
    fun phonePadNumbersOnlyForPlainPresses() {
        assertEquals(2, SableKeyguardKeys.digitFor('e'.code, '2'.code, 0))
        assertEquals(-1, SableKeyguardKeys.digitFor('E'.code, '2'.code, 0x01))
        assertEquals(-1, SableKeyguardKeys.digitFor('a'.code, 0, 0))
    }

    @Test
    fun shortcutsNeverTypeDigitsOrText() {
        listOf(0x1000, 0x10000, 0x08).forEach { mod ->
            assertEquals(-1, SableKeyguardKeys.digitFor('1'.code, '1'.code, mod))
            assertEquals(0, SableKeyguardKeys.textCharFor('a'.code, mod))
        }
    }

    @Test
    fun textCharsArePrintableOnly() {
        assertEquals('a'.code, SableKeyguardKeys.textCharFor('a'.code, 0))
        assertEquals('#'.code, SableKeyguardKeys.textCharFor('#'.code, alt))
        assertEquals(0, SableKeyguardKeys.textCharFor(' '.code, 0))
        assertEquals(0, SableKeyguardKeys.textCharFor('\n'.code, 0))
        assertEquals(0, SableKeyguardKeys.textCharFor(0x7f, 0))
        assertEquals(0, SableKeyguardKeys.textCharFor(0x80000000.toInt() or 0x301, 0))
    }

    @Test
    fun typeAheadKeysOpenTheField() {
        assertTrue(SableKeyguardKeys.isTypeAheadKey(keyW, alt, 0, '1'.code, 0))
        assertTrue(SableKeyguardKeys.isTypeAheadKey(keyA, 0, 0, 'a'.code, 0))
        assertTrue(SableKeyguardKeys.isTypeAheadKey(keyW, 0, 0, 'w'.code, '1'.code))
    }

    @Test
    fun confirmBackDeleteAndRepeatsKeepTheirMeaning() {
        listOf(4, 23, 61, 62, 66, 67, 111, 112, 160).forEach { key ->
            assertFalse(SableKeyguardKeys.isTypeAheadKey(key, 0, 0, 'x'.code, 0))
        }
        assertFalse(SableKeyguardKeys.isTypeAheadKey(keyA, 0, 1, 'a'.code, 0))
        assertFalse(SableKeyguardKeys.isTypeAheadKey(keyA, 0x1000, 0, 'a'.code, 0))
        // Menu, volume and other keys with no character.
        assertFalse(SableKeyguardKeys.isTypeAheadKey(82, 0, 0, 0, 0))
    }

    @Test
    fun hintFollowsTheCredentialType() {
        assertEquals(SableKeyguardKeys.HINT_PIN, SableKeyguardKeys.hintFor(3))
        assertEquals(SableKeyguardKeys.HINT_PASSWORD, SableKeyguardKeys.hintFor(4))
        assertEquals(SableKeyguardKeys.HINT_ENTER, SableKeyguardKeys.hintFor(-1))
        assertEquals(SableKeyguardKeys.HINT_NONE, SableKeyguardKeys.hintFor(1))
        assertEquals(SableKeyguardKeys.HINT_NONE, SableKeyguardKeys.hintFor(2))
    }

    @Test
    fun typeAheadHandsKeysOverOnceInOrder() {
        val buffer = SableKeyguardTypeAhead.get()
        buffer.clear()
        buffer.offer(1, '1'.code, 1000)
        buffer.offer(-1, 'a'.code, 1100)
        buffer.offer(2, '2'.code, 1200)
        assertTrue(buffer.isPending(1300))
        val digits = buffer.takeDigits(1300)
        assertEquals(listOf(1, 2), digits.toList())
        assertFalse(buffer.isPending(1300))
        assertEquals("", buffer.takeText(1300))
    }

    @Test
    fun typeAheadTextForPasswords() {
        val buffer = SableKeyguardTypeAhead.get()
        buffer.clear()
        buffer.offer(-1, 'p'.code, 0)
        buffer.offer(1, '1'.code, 10)
        buffer.offer(-1, 0, 20)
        assertEquals("p1", buffer.takeText(30))
        assertFalse(buffer.isPending(30))
    }

    @Test
    fun typeAheadExpires() {
        val buffer = SableKeyguardTypeAhead.get()
        buffer.clear()
        buffer.offer(5, '5'.code, 1000)
        assertTrue(buffer.isPending(1000 + SableKeyguardTypeAhead.TIMEOUT_MS))
        assertFalse(buffer.isPending(1001 + SableKeyguardTypeAhead.TIMEOUT_MS))
        assertEquals(0, buffer.takeDigits(1001 + SableKeyguardTypeAhead.TIMEOUT_MS).size)
        // A clock that went backwards drops everything too.
        buffer.offer(5, '5'.code, 5000)
        assertFalse(buffer.isPending(4000))
    }

    @Test
    fun typeAheadIsBounded() {
        val buffer = SableKeyguardTypeAhead.get()
        buffer.clear()
        repeat(SableKeyguardTypeAhead.MAX_KEYS + 10) { buffer.offer(7, '7'.code, 0) }
        assertEquals(SableKeyguardTypeAhead.MAX_KEYS, buffer.takeDigits(0).size)
    }
}
