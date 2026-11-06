package org.sableos.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAndPlanTest {
    @Test
    fun recognizerResultsBecomeOneLine() {
        assertEquals("good morning", Speech.textOf("""{"text" : " good morning "}"""))
        assertEquals("", Speech.textOf("""{"partial" : "go"}"""))
        assertEquals("", Speech.textOf("not json"))
        assertEquals("what time is it", Speech.join(listOf("what time", "", " is it ")))
    }

    @Test
    fun heardWordsJoinTheDraft() {
        assertEquals("hello", Speech.appendToDraft("", "hello"))
        assertEquals("note: hello", Speech.appendToDraft("note: ", "hello"))
    }

    @Test
    fun modelArchiveStaysInsideItsFolder() {
        assertEquals("am/final.mdl", Speech.entryPath("vosk-model-small-en-us-0.15/am/final.mdl"))
        assertNull(Speech.entryPath("vosk-model-small-en-us-0.15/"))
        assertNull(Speech.entryPath("vosk-model-small-en-us-0.15/../../evil"))
        assertNull(Speech.entryPath("model/./conf"))
    }

    @Test
    fun askingNeedsProviderAddressKeyAndModel() {
        val turns = listOf(ChatTurn(fromOwner = true, text = "hi"))
        assertEquals(AskPlan.Blocked("error_provider"), AskPlan.chat(AssistantSettings(), turns))
        val ollama = AssistantSettings(provider = AssistantProvider.Ollama)
        assertEquals(AskPlan.Blocked("error_address"), AskPlan.chat(ollama, turns))
        assertEquals(
            AskPlan.Blocked("error_plain_http"),
            AskPlan.chat(ollama.copy(address = "http://example.com"), turns),
        )
        assertEquals(AskPlan.Blocked("error_model"), AskPlan.chat(ollama.copy(address = "192.168.1.20"), turns))
        val groq = AssistantSettings(provider = AssistantProvider.Groq, model = "llama")
        assertEquals(AskPlan.Blocked("error_key"), AskPlan.chat(groq, turns))
        val send = AskPlan.chat(ollama.copy(address = "192.168.1.20", model = "llama3.2"), turns) as AskPlan.Send
        assertEquals("http://192.168.1.20:11434/v1", send.base)
        assertTrue(send.body.contains("\"llama3.2\""))
        assertEquals(AskPlan.Send("https://api.groq.com/openai/v1", ""), AskPlan.target(groq.copy(apiKey = "k")))
    }
}
