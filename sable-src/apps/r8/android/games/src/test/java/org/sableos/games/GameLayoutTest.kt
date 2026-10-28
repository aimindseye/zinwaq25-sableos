package org.sableos.games

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Q25's square window puts the board beside the controls; a tall phone stacks them. */
class GameLayoutTest {
    @Test
    fun squareAndTallWindows() {
        assertTrue(GameLayout.isSquare(widthDp = 411f, heightDp = 387f))
        assertTrue(GameLayout.isSquare(widthDp = 411f, heightDp = 411f))
        assertFalse(GameLayout.isSquare(widthDp = 411f, heightDp = 860f))
    }

    @Test
    fun theSquareBoardFillsTheWidthBesideTheRailAndNeverOverflowsTheHeight() {
        // Q25: 411 dp wide, 387 dp under the status bar.
        assertEquals(299.dp, GameLayout.squareBoardSide(width = 411.dp, height = 387.dp))
        // Short window: the height limits the board.
        assertEquals(184.dp, GameLayout.squareBoardSide(width = 411.dp, height = 200.dp))
    }

    @Test
    fun theTallBoardKeepsRoomForTheControls() {
        assertEquals(363.dp, GameLayout.tallBoardSide(width = 411.dp, height = 900.dp))
        assertEquals(300.dp, GameLayout.tallBoardSide(width = 411.dp, height = 500.dp))
    }
}
