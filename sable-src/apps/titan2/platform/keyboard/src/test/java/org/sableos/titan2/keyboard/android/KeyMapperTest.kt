package org.sableos.titan2.keyboard.android

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.titan2.keyboard.core.Key

class KeyMapperTest {
    @Test
    fun q25CurrencyKeyTypesTheKcmCharacter() {
        assertEquals(Key.Punct('$'), KeyMapper.map(KeyEvent.KEYCODE_GRAVE, '$'.code))
    }

    @Test
    fun punctuationWithoutAKcmCharacterKeepsTheStandardOne() {
        assertEquals(Key.Punct('`'), KeyMapper.map(KeyEvent.KEYCODE_GRAVE, 0))
        assertEquals(Key.Punct(','), KeyMapper.map(KeyEvent.KEYCODE_COMMA, ','.code))
    }

    @Test
    fun combiningAccentsSpacesAndOtherKeysAreUnchanged() {
        assertEquals(Key.Punct('`'), KeyMapper.map(KeyEvent.KEYCODE_GRAVE, 0x80000060.toInt()))
        assertEquals(Key.Punct('`'), KeyMapper.map(KeyEvent.KEYCODE_GRAVE, ' '.code))
        assertEquals(Key.Letter('a'), KeyMapper.map(KeyEvent.KEYCODE_A, '$'.code))
        assertEquals(Key.Space, KeyMapper.map(KeyEvent.KEYCODE_SPACE, ' '.code))
    }
}
