package org.sableos.test.frameworks.base

import com.android.systemui.shade.sable.SableShadeSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableShadeSearchTest {
    private val ctrl = 0x1000
    private val meta = 0x10000
    private val alt = 0x02 or 0x10
    private val slashKey = 76
    private val back = 4
    private val esc = 111
    private val del = 67
    private val enter = 66
    private val down = 20
    private val tab = 61
    private val letterA = 29

    private fun decide(
        keyCode: Int,
        char: Int,
        typing: Boolean,
        filter: Boolean = false,
        meta: Int = 0,
        repeat: Int = 0,
    ) = SableShadeSearch.decide(keyCode, repeat, char, meta, typing, filter)

    @Test
    fun slashStartsWhateverKeyMakesIt() {
        assertEquals(SableShadeSearch.START, decide(slashKey, '/'.code, typing = false))
        // Q25: "/" comes from an Alt or Sym key; the character decides.
        assertEquals(SableShadeSearch.START, decide(letterA, '/'.code, typing = false, meta = alt))
        assertEquals(SableShadeSearch.START, decide(84, 0, typing = false))
        // A key that is not "/" with its modifiers does not start.
        assertEquals(SableShadeSearch.NONE, decide(slashKey, '?'.code, typing = false))
        assertEquals(SableShadeSearch.NONE, decide(letterA, 'a'.code, typing = false))
    }

    @Test
    fun ctrlAndMetaChordsAreNeverTaken() {
        assertEquals(SableShadeSearch.NONE, decide(slashKey, '/'.code, false, meta = ctrl))
        assertEquals(SableShadeSearch.NONE, decide(letterA, 'a'.code, true, meta = meta))
    }

    @Test
    fun typingTakesPrintableKeysBeforeTheRows() {
        assertEquals(SableShadeSearch.APPEND, decide(letterA, 'a'.code, typing = true))
        assertEquals(SableShadeSearch.APPEND, decide(62, ' '.code, typing = true))
        assertEquals(SableShadeSearch.APPEND, decide(51, '2'.code, typing = true, meta = alt))
        assertEquals(SableShadeSearch.CONSUME, decide(letterA, 'a'.code, typing = true, repeat = 1))
        assertEquals(SableShadeSearch.DELETE, decide(del, 0, typing = true))
        assertEquals(SableShadeSearch.DELETE, decide(del, 0, typing = true, repeat = 3))
    }

    @Test
    fun enterKeepsDownMovesEscClears() {
        assertEquals(SableShadeSearch.COMMIT, decide(enter, '\n'.code, typing = true))
        assertEquals(SableShadeSearch.COMMIT_PASS, decide(down, 0, typing = true))
        assertEquals(SableShadeSearch.COMMIT_PASS, decide(tab, '\t'.code, typing = true))
        assertEquals(SableShadeSearch.CLEAR, decide(esc, 0x1b, typing = true))
        assertEquals(SableShadeSearch.CLEAR, decide(back, 0, typing = true))
    }

    @Test
    fun aKeptFilterIsClearedByEscOrBackAndEditedBySlash() {
        assertEquals(SableShadeSearch.CLEAR, decide(esc, 0, typing = false, filter = true))
        assertEquals(SableShadeSearch.CLEAR, decide(back, 0, typing = false, filter = true))
        assertEquals(SableShadeSearch.START, decide(slashKey, '/'.code, typing = false, filter = true))
        // Without a filter Back keeps closing the shade.
        assertEquals(SableShadeSearch.NONE, decide(back, 0, typing = false))
        // Row commands (0301) keep their letters once the filter is kept.
        assertEquals(SableShadeSearch.NONE, decide(32, 'd'.code, typing = false, filter = true))
    }

    @Test
    fun queryEditing() {
        assertEquals("", SableShadeSearch.append("", ' '.code))
        assertEquals("m", SableShadeSearch.append("", 'm'.code))
        assertEquals("mo", SableShadeSearch.append("m", 'o'.code))
        assertEquals("ab", SableShadeSearch.append("ab", 0x1b))
        val full = "x".repeat(SableShadeSearch.MAX_QUERY_LENGTH)
        assertEquals(full, SableShadeSearch.append(full, 'y'.code))
        assertEquals("m", SableShadeSearch.deleteLast("mo"))
        assertEquals("", SableShadeSearch.deleteLast(""))
        assertEquals("a", SableShadeSearch.deleteLast("a😀"))
    }

    @Test
    fun matchingIgnoresCaseAndAccentsAndNeedsEveryWord() {
        assertTrue(SableShadeSearch.matches("", "anything"))
        assertTrue(SableShadeSearch.matches("  ", "anything"))
        assertTrue(SableShadeSearch.matches("rene", "Messages", "René: dinner at 8"))
        assertTrue(SableShadeSearch.matches("MESS din", "Messages", "René: dinner at 8"))
        assertFalse(SableShadeSearch.matches("mess lunch", "Messages", "René: dinner at 8"))
        assertFalse(SableShadeSearch.matches("mail", null, null))
        assertTrue(SableShadeSearch.matches("gmail", "Gmail", null))
    }
}
