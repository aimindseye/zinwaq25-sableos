package io.github.aimindseye.tenseven.frames

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableFrameCoversTest {
    @After
    fun reset() = SableFrameCovers.clearAll()

    @Test
    fun aCoverBelongsToItsPackage() {
        assertTrue(SableFrameCovers.set("org.sableos.messages", "Messages", "Anna: see you\nat six", 10))
        val cover = SableFrameCovers.snapshot(11).single()
        assertEquals("org.sableos.messages", cover.packageName)
        assertEquals("Anna: see you at six", cover.detail)
        assertEquals(SummarySource.Cover, cover.source)
    }

    @Test
    fun emptyCoversAreRefusedAndSettingAgainReplaces() {
        assertFalse(SableFrameCovers.set("a", "A", "  ", 1))
        SableFrameCovers.set("a", "A", "one", 1)
        SableFrameCovers.set("a", "A", "two", 2)
        assertEquals(listOf("two"), SableFrameCovers.snapshot(3).map { it.detail })
    }

    @Test
    fun coversLapseAndCanBeCleared() {
        SableFrameCovers.set("a", "A", "one", 0)
        SableFrameCovers.set("b", "B", "two", 0)
        SableFrameCovers.clear("b")
        assertEquals(1, SableFrameCovers.snapshot(SableFrameCovers.MAX_AGE_MS).size)
        assertTrue(SableFrameCovers.snapshot(SableFrameCovers.MAX_AGE_MS + 1).isEmpty())
    }

    @Test
    fun textIsBounded() {
        SableFrameCovers.set("a", "T".repeat(200), "x".repeat(500), 0)
        val cover = SableFrameCovers.snapshot(0).single()
        assertEquals(60, cover.title.length)
        assertEquals(240, cover.detail.length)
    }
}
