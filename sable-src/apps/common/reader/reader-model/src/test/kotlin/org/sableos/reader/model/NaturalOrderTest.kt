package org.sableos.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {
    private fun sorted(vararg names: String) = names.toList().sortedWith(NaturalOrder)

    @Test
    fun numbersSortByValueNotByCharacter() {
        val expected = listOf("p1.jpg", "p2.jpg", "p10.jpg", "p100.jpg")
        assertEquals(expected, sorted("p10.jpg", "p100.jpg", "p2.jpg", "p1.jpg"))
    }

    @Test
    fun caseAndLeadingZerosDoNotReorderPages() {
        assertEquals(listOf("a1", "A2", "a03", "a10"), sorted("a10", "a03", "A2", "a1"))
    }

    @Test
    fun foldersGroupTheirPagesInOrder() {
        assertEquals(
            listOf("ch1/1.png", "ch1/2.png", "ch2/1.png", "ch10/1.png"),
            sorted("ch10/1.png", "ch2/1.png", "ch1/2.png", "ch1/1.png"),
        )
    }

    @Test
    fun enormousDigitRunsNeitherOverflowNorStall() {
        val huge = "9".repeat(100_000)
        val larger = "1" + "0".repeat(100_000)
        assertTrue(NaturalOrder.compare("p$huge", "p$larger") < 0)
        assertEquals(0, NaturalOrder.compare("p$huge", "p$huge"))
    }

    @Test
    fun theOrderIsTotalAndStableForNumericallyEqualNames() {
        val a = NaturalOrder.compare("p01", "p1")
        val b = NaturalOrder.compare("p1", "p01")
        assertTrue(a != 0 && a == -b)
    }
}
