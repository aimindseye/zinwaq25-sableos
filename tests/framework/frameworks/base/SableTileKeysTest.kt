package org.sableos.test.frameworks.base

import com.android.systemui.qs.sable.SableTileKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class SableTileKeysTest {
    private val down = 0
    private val up = 1
    private val alt = 0x02 or 0x10
    private val space = 62
    private val enter = 66

    @Test
    fun spaceTogglesOnTheFirstKeyDown() {
        assertEquals(SableTileKeys.TOGGLE, SableTileKeys.actionFor(space, 0, down, 0))
    }

    @Test
    fun repeatsAndKeyUpOfAHandledKeyAreConsumed() {
        assertEquals(SableTileKeys.CONSUME, SableTileKeys.actionFor(space, 0, down, 1))
        assertEquals(SableTileKeys.CONSUME, SableTileKeys.actionFor(space, 0, up, 0))
        assertEquals(SableTileKeys.CONSUME, SableTileKeys.actionFor(enter, alt, up, 0))
    }

    @Test
    fun altEnterOpensDetails() {
        assertEquals(SableTileKeys.DETAILS, SableTileKeys.actionFor(enter, alt, down, 0))
        assertEquals(SableTileKeys.DETAILS, SableTileKeys.actionFor(160, alt, down, 0))
        assertEquals(SableTileKeys.DETAILS, SableTileKeys.actionFor(23, alt, down, 0))
    }

    @Test
    fun arrowsAndPlainEnterStayWithCompose() {
        listOf(19, 20, 21, 22, enter, 23, 61).forEach { key ->
            assertEquals(SableTileKeys.PASS, SableTileKeys.actionFor(key, 0, down, 0))
            assertEquals(SableTileKeys.PASS, SableTileKeys.actionFor(key, 0, up, 0))
        }
    }

    @Test
    fun otherModifiersAndAltSpaceAreNotOurs() {
        assertEquals(SableTileKeys.PASS, SableTileKeys.actionFor(space, alt, down, 0))
        listOf(0x01, 0x04, 0x08, 0x1000, 0x10000).forEach { other ->
            assertEquals(SableTileKeys.PASS, SableTileKeys.actionFor(space, other, down, 0))
            assertEquals(SableTileKeys.PASS, SableTileKeys.actionFor(enter, alt or other, down, 0))
        }
    }
}
