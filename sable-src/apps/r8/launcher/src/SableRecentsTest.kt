package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SableRecentsTest {
    @Test
    fun theLastOpenedAppComesFirstAndIsNotListedTwice() {
        val opened = SableRecents.record(SableRecents.record(emptyList(), "a"), "b")
        assertEquals(listOf("b", "a"), opened)
        assertEquals(listOf("a", "b"), SableRecents.record(opened, "a"))
    }

    @Test
    fun theListKeepsTheTwelveLastAndIgnoresBadKeys() {
        val many = (1..20).fold(emptyList<String>()) { acc, i -> SableRecents.record(acc, "k$i") }
        assertEquals(SableRecents.MAX, many.size)
        assertEquals("k20", many.first())
        assertEquals(many, SableRecents.record(many, " "))
        assertEquals(many, SableRecents.record(many, "x\ty"))
    }

    @Test
    fun savingAndReadingBackKeepsTheOrderAndDropsDamage() {
        val saved = SableRecents.encode(listOf("b", "a"))
        assertEquals(listOf("b", "a"), SableRecents.decode(saved))
        assertEquals(listOf("b", "a"), SableRecents.decode("b\n\na\nb"))
        assertEquals(emptyList<String>(), SableRecents.decode(null))
    }
}
