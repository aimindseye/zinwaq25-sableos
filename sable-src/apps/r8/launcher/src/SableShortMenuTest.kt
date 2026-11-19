package io.github.aimindseye.tenseven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SableShortMenuTest {
    @Test
    fun theGridHoldsExactlyNineDistinctCells() {
        assertEquals(SableShortMenu.COLUMNS * SableShortMenu.ROWS, SableShortMenu.TITLES.size)
        assertEquals(SableShortMenu.TITLES.size, SableShortMenu.TITLES.toSet().size)
    }

    @Test
    fun theListKeepsItsItems() {
        val items = SableShortMenu.TITLES.map { it to {} }
        val menu = SableShortMenu(items)
        assertEquals(9, menu.size)
        assertEquals("Phone", menu[2].first)
    }
}
