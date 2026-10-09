package org.sableos.reference.typetofind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMapperTest {
    private fun m(code: Int, uni: Int = 0, meta: Int = 0, repeat: Boolean = false) =
        KeyMapper.map(RawKey(code, uni, meta, repeat))

    @Test fun dpadAndEditingKeysMapToSemanticKinds() {
        assertEquals(KeyKind.Up, m(19).kind)
        assertEquals(KeyKind.Down, m(20).kind)
        assertEquals(KeyKind.Left, m(21).kind)
        assertEquals(KeyKind.Right, m(22).kind)
        assertEquals(KeyKind.Tab, m(61).kind)
        assertEquals(KeyKind.Backspace, m(67).kind)
        assertEquals(KeyKind.Back, m(4).kind)
        assertEquals(KeyKind.Escape, m(111).kind)
    }

    @Test fun everyActivationKeyIsActivate() {
        listOf(23, 66, 160).forEach { assertEquals(KeyKind.Activate, m(it).kind) }
        assertEquals(KeyKind.Space, m(62, ' '.code).kind)
    }

    @Test fun moveHomeAndMoveEndMapToCursorMovement() {
        assertEquals(KeyKind.MoveHome, m(KeyMapper.KEYCODE_MOVE_HOME).kind)
        assertEquals(KeyKind.MoveEnd, m(KeyMapper.KEYCODE_MOVE_END).kind)
        assertEquals(122, KeyMapper.KEYCODE_MOVE_HOME)
        assertEquals(123, KeyMapper.KEYCODE_MOVE_END)
    }

    @Test fun pageUpAndPageDownMapToPaging() {
        assertEquals(92, KeyMapper.KEYCODE_PAGE_UP)
        assertEquals(93, KeyMapper.KEYCODE_PAGE_DOWN)
        assertEquals(KeyKind.PageUp, m(KeyMapper.KEYCODE_PAGE_UP).kind)
        assertEquals(KeyKind.PageDown, m(KeyMapper.KEYCODE_PAGE_DOWN).kind)
    }

    @Test fun theVocabularyIsExactlyTheCanonicalKinds() {
        val expected =
            setOf(
                "Up", "Down", "Left", "Right", "Activate", "Space", "Back", "Escape", "Tab",
                "Backspace", "PageUp", "PageDown", "MoveHome", "MoveEnd", "Printable", "Other"
            )
        assertEquals(expected, KeyKind.values().map { it.name }.toSet())
        assertEquals(expected.size, KeyKind.values().size)
    }

    @Test fun systemHomeIsNeverMappedToAppNavigation() {
        assertEquals(3, KeyMapper.KEYCODE_HOME)
        listOf(0, 'h'.code).forEach {
            val k = m(KeyMapper.KEYCODE_HOME, it)
            assertEquals(KeyKind.Other, k.kind)
            assertFalse(k.kind == KeyKind.MoveHome || k.kind == KeyKind.MoveEnd)
        }
    }

    @Test fun systemHomeAndMoveHomeNeverAlias() {
        assertFalse(m(KeyMapper.KEYCODE_HOME).kind == m(KeyMapper.KEYCODE_MOVE_HOME).kind)
        assertFalse(m(KeyMapper.KEYCODE_MOVE_HOME).kind == m(KeyMapper.KEYCODE_MOVE_END).kind)
    }

    @Test fun movementKeysCarryModifiersAndRepeat() {
        val k = m(KeyMapper.KEYCODE_MOVE_END, 0, KeyMapper.META_SHIFT_ON, repeat = true)
        assertEquals(KeyKind.MoveEnd, k.kind)
        assertTrue(k.shift)
        assertTrue(k.repeat)
    }

    @Test fun printableUsesTheCharacterTheKeyMapAlreadyProduced() {
        val k = m(54, 'z'.code)
        assertEquals(KeyKind.Printable, k.kind)
        assertEquals('z', k.char)
        assertEquals('@', m(54, '@'.code, KeyMapper.META_ALT_ON).char)
    }

    @Test fun nonCharacterKeysAreOther() {
        assertEquals(KeyKind.Other, m(224, 0).kind)
        assertEquals(KeyKind.Other, m(54, 0).kind)
        assertEquals(KeyKind.Other, m(54, 0x80000000.toInt() or '`'.code).kind)
        assertEquals(KeyKind.Other, m(54, 7).kind)
    }

    @Test fun modifiersAndRepeatAreCarried() {
        val k = m(54, 'f'.code, KeyMapper.META_CTRL_ON or KeyMapper.META_SHIFT_ON, repeat = true)
        assertTrue(k.ctrl && k.shift && k.repeat)
        assertFalse(k.alt || k.meta)
        assertTrue(m(54, 'f'.code, KeyMapper.META_META_ON).hasCommandModifier)
    }

    @Test fun editableFlagIsPassedThrough() {
        assertTrue(
            KeyMapper.map(RawKey(54, 'a'.code, 0, false), editableFocused = true).editableFocused
        )
    }

    @Test fun mapperIsDeviceIndependent() {
        val a = m(54, 'q'.code)
        val b = m(54, 'q'.code)
        assertEquals(a, b)
    }
}
