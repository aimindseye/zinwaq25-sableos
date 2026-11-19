package org.sableos.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RowMotionTest {
    @Test
    fun bb7FadesFasterThanBb10Slides() {
        assertEquals(150, RowMotion.millis(true))
        assertEquals(250, RowMotion.millis(false))
    }
}
