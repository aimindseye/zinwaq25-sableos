package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertEquals
import org.junit.Test

class SableHubPeekTest {
    private fun entry(
        id: String,
        at: Long,
        unread: Int = 0,
        snoozed: Boolean = false,
        preview: String = "hi",
    ) = SableHubPeek.Entry(id, id, preview, at, unread, snoozed)

    @Test
    fun unreadFirstThenNewestWithoutSnoozed() {
        val rows =
            SableHubPeek.rows(
                listOf(entry("old", 1), entry("new", 5), entry("unread", 2, unread = 1), entry("later", 9, snoozed = true)),
            )
        assertEquals(listOf("unread", "new", "old"), rows.map { it.id })
    }

    @Test
    fun atMostEightRows() {
        assertEquals(SableHubPeek.MAX_ROWS, SableHubPeek.rows((1..20).map { entry("c$it", it.toLong()) }).size)
    }

    @Test
    fun labelsShowCountAndShortPreview() {
        assertEquals("Anna (2): See you", SableHubPeek.label(entry("Anna", 1, unread = 2, preview = "See you\n")))
        assertEquals("Bo", SableHubPeek.label(entry("Bo", 1, preview = " ")))
        assertEquals(60, SableHubPeek.label(entry("C", 1, preview = "x".repeat(100))).length - "C: ".length)
    }

    @Test
    fun titleCountsNewMessages() {
        assertEquals("Hub · 3 new", SableHubPeek.title(listOf(entry("a", 1, unread = 1), entry("b", 1, unread = 2))))
        assertEquals("Hub", SableHubPeek.title(listOf(entry("a", 1, unread = 4, snoozed = true))))
    }
}
