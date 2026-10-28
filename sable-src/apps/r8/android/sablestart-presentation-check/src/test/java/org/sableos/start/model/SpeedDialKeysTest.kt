package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** BlackBerry speed dial on the home screen: hold calls, a short press still searches. */
class SpeedDialKeysTest {
    private val keyK = 39
    private val keyA = 29
    private val keyB = 30
    private val assigned = setOf('k')

    private fun SpeedDialKeys.down(
        code: Int,
        seed: Char?,
        repeat: Int = 0,
        meta: Int = 0,
    ) = onKey(code, true, repeat, meta, seed, assigned)

    private fun SpeedDialKeys.up(code: Int) = onKey(code, false, 0, 0, null, assigned)

    @Test
    fun holdingAnAssignedLetterCallsOnce() {
        val keys = SpeedDialKeys()
        assertEquals(SpeedDialKeys.Step.Consume, keys.down(keyK, 'k'))
        assertEquals(SpeedDialKeys.Step.Call('k'), keys.down(keyK, null, repeat = 1))
        assertEquals(SpeedDialKeys.Step.Consume, keys.down(keyK, null, repeat = 2))
        assertEquals(SpeedDialKeys.Step.Consume, keys.up(keyK))
        assertEquals("next press starts fresh", SpeedDialKeys.Step.Consume, keys.down(keyK, 'k'))
    }

    @Test
    fun aShortPressOfAnAssignedLetterSearchesOnRelease() {
        val keys = SpeedDialKeys()
        keys.down(keyK, 'k')
        assertEquals(SpeedDialKeys.Step.Search("k"), keys.up(keyK))
    }

    @Test
    fun otherKeysAndChordsSearchAtOnce() {
        val keys = SpeedDialKeys()
        assertEquals(SpeedDialKeys.Step.Pass, keys.down(keyA, 'a'))
        assertEquals(SpeedDialKeys.Step.Pass, keys.up(keyA))
        assertEquals("Shift+K", SpeedDialKeys.Step.Pass, keys.down(keyK, 'K', meta = 0x41))
        assertEquals("Alt+K", SpeedDialKeys.Step.Pass, keys.down(keyK, '\'', meta = 0x12))
        assertEquals("no search seed", SpeedDialKeys.Step.Pass, keys.down(keyK, null))
    }

    @Test
    fun fastTypingKeepsBothLetters() {
        val keys = SpeedDialKeys()
        keys.down(keyK, 'k')
        assertEquals(SpeedDialKeys.Step.Search("kb"), keys.down(keyB, 'b'))
        assertEquals("the K release is the search's now", SpeedDialKeys.Step.Pass, keys.up(keyK))
    }

    @Test
    fun lettersFollowKeyCodes() {
        assertEquals('a', SpeedDialKeys.letterFor(29))
        assertEquals('z', SpeedDialKeys.letterFor(54))
        assertEquals(null, SpeedDialKeys.letterFor(62))
    }
}
