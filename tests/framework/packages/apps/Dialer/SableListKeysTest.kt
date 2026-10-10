package com.android.dialer.sable

import org.junit.Assert.assertEquals
import org.junit.Test
import com.android.dialer.sable.SableListKeys.Action

/** Pure tests for the contact-list key policy (phone-contacts patches 0701/0751). */
class SableListKeysTest {
    private fun decide(
        c: Int,
        mod: Boolean = false,
        repeat: Boolean = false,
        search: Boolean = false,
        selection: Boolean = false,
        canSearch: Boolean = true,
    ) = SableListKeys.decide(c, mod, repeat, search, selection, canSearch)

    @Test fun lettersJumpOnlyWhileSearchIsClosed() {
        assertEquals(Action.JUMP, decide('a'.code))
        assertEquals(Action.JUMP, decide('Z'.code))
        assertEquals(Action.JUMP, decide(0x0431))
        assertEquals(Action.UPSTREAM, decide('a'.code, search = true))
        assertEquals(Action.UPSTREAM, decide('a'.code, selection = true))
    }

    @Test fun modifiedAndHeldKeysNeverJump() {
        assertEquals(Action.UPSTREAM, decide('a'.code, mod = true))
        assertEquals(Action.IGNORE, decide('a'.code, repeat = true))
        assertEquals(Action.IGNORE, decide('/'.code, repeat = true))
    }

    @Test fun slashOpensSearchWhereThereIsOne() {
        assertEquals(Action.OPEN_SEARCH, decide('/'.code))
        assertEquals(Action.UPSTREAM, decide('/'.code, canSearch = false))
        assertEquals(Action.UPSTREAM, decide('/'.code, search = true))
    }

    @Test fun everythingElseKeepsItsUpstreamMeaning() {
        assertEquals(Action.UPSTREAM, decide('5'.code))
        assertEquals(Action.UPSTREAM, decide('+'.code))
        assertEquals(Action.UPSTREAM, decide('*'.code))
        assertEquals(Action.UPSTREAM, decide(0))
        assertEquals(Action.UPSTREAM, decide(-1))
    }
}
