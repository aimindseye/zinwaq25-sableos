package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertEquals
import org.junit.Test

class SableMailFeedTest {
    private fun note(
        key: String,
        pkg: String,
        at: Long,
    ) = HubNotification(
        key = key,
        conversationId = key,
        conversationName = key,
        packageName = pkg,
        appName = pkg,
        postedAt = at,
        isOngoing = false,
        messages = emptyList(),
        contentIntent = null,
        replyAction = null,
    )

    @Test
    fun mailTextJoinsSubjectAndPreview() {
        assertEquals("Lunch · See you at noon", SableMailFeed.mailText("Lunch", "See you\n at  noon"))
        assertEquals("Lunch", SableMailFeed.mailText(" Lunch ", ""))
        assertEquals("Hi", SableMailFeed.mailText("", "Hi"))
    }

    @Test
    fun feedReplacesSableMailNotificationsOnlyWhileItAnswers() {
        val shade = listOf(note("a", "org.sableos.mail", 1), note("b", "org.sableos.messages", 2))
        val feed = listOf(note("sablemail|1", "org.sableos.mail", 3))
        assertEquals(listOf("sablemail|1", "b"), SableMailFeed.merge(shade, true, feed).map { it.key })
        assertEquals(listOf("a", "b"), SableMailFeed.merge(shade, false, feed).map { it.key })
    }
}
