package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** BB7 home style: the saved choice, the tray grid and its panels. */
class Bb7HomeTest {
    @Test
    fun homeStyleRoundTripsAndDefaultsToBb10() {
        HomeStyle.entries.forEach { assertEquals(it, HomeStyle.fromStableValue(it.stableValue)) }
        assertEquals(HomeStyle.Bb10, HomeStyle.fromStableValue(null))
        assertEquals(HomeStyle.Bb10, HomeStyle.fromStableValue("bb5"))
    }

    @Test
    fun trayHasSixColumnsOnTheQ25AndStaysInRange() {
        assertEquals(6, Bb7Home.columns(411))
        assertEquals(Bb7Home.MIN_COLUMNS, Bb7Home.columns(200))
        assertEquals(Bb7Home.MAX_COLUMNS, Bb7Home.columns(1200))
    }

    @Test
    fun panelsWrapBothWays() {
        assertEquals(Bb7Panel.All, Bb7Home.step(Bb7Panel.Favorites, 1))
        assertEquals(Bb7Panel.Downloads, Bb7Home.step(Bb7Panel.Favorites, -1))
        assertEquals(Bb7Panel.Favorites, Bb7Home.step(Bb7Panel.Downloads, 1))
    }

    @Test
    fun selectionMovesByCellAndRowWithinTheItems() {
        assertEquals(1, Bb7Home.move(0, 10, 6, 1, 0))
        assertEquals(7, Bb7Home.move(1, 10, 6, 0, 1))
        assertEquals(9, Bb7Home.move(5, 10, 6, 0, 1))
        assertEquals(0, Bb7Home.move(3, 10, 6, 0, -1))
        assertEquals(0, Bb7Home.move(4, 0, 6, 1, 0))
    }
}
