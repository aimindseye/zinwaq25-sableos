package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SableReplyChoiceTest {
    private val targets = listOf("sms", "chat", "mail")

    @Test
    fun noChoiceRepliesThroughTheNewest() {
        assertEquals("sms", SableReplyChoice.pick(targets, null) { it })
    }

    @Test
    fun aChoiceRepliesThroughThatApp() {
        assertEquals("mail", SableReplyChoice.pick(targets, "mail") { it })
    }

    @Test
    fun aChoiceThatLeftFallsBackToTheNewest() {
        assertEquals("sms", SableReplyChoice.pick(targets, "gone") { it })
    }

    @Test
    fun noTargetsMeansNoReply() {
        assertNull(SableReplyChoice.pick(emptyList<String>(), "sms") { it })
    }

    @Test
    fun theChoiceIsPerConversation() {
        SableReplyChoice.choose("c1", "chat")
        assertEquals("chat", SableReplyChoice.packageFor("c1"))
        assertNull(SableReplyChoice.packageFor("c2"))
    }
}
