package org.sableos.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Addresses, providers, the chat protocol and the persona. */
class CoreTest {
    @Test
    fun addressesAreNormalizedTheWayPeopleTypeThem() {
        assertEquals("http://192.168.1.20:11434", ServerAddress.normalize("192.168.1.20", ServerAddress.OLLAMA_PORT))
        assertEquals("http://192.168.1.20:11434", ServerAddress.normalize(" http://192.168.1.20:11434/v1/ ", 1))
        assertEquals("http://pc.local:1234", ServerAddress.normalize("pc.local:1234"))
        assertEquals("https://llm.example.org/api", ServerAddress.normalize("https://llm.example.org/api/v1"))
        assertNull(ServerAddress.normalize(""))
        assertNull(ServerAddress.normalize("ftp://x"))
        assertNull(ServerAddress.normalize("192.168 .1.2"))
    }

    @Test
    fun plainHttpOnlyReachesTheOwnersNetwork() {
        listOf("192.168.0.4", "10.1.2.3", "172.20.0.1", "100.101.1.1", "localhost", "pc.local", "box.tail1.ts.net")
            .forEach { assertTrue(it, ServerAddress.allowed("http://$it:11434")) }
        listOf("8.8.8.8", "172.32.0.1", "example.org")
            .forEach { assertFalse(it, ServerAddress.allowed("http://$it")) }
        assertTrue(ServerAddress.allowed("https://example.org"))
    }

    @Test
    fun settingsGiveTheApiBaseOrTheProblem() {
        assertEquals(ApiBase.Problem(SetupProblem.NoProvider), AssistantSettings().base())
        val ollama = AssistantSettings(provider = AssistantProvider.Ollama, address = "192.168.1.20")
        assertEquals(ApiBase.Ready("http://192.168.1.20:11434/v1"), ollama.base())
        assertEquals(ApiBase.Problem(SetupProblem.NoAddress), ollama.copy(address = " ").base())
        assertEquals(ApiBase.Problem(SetupProblem.PlainHttpOutside), ollama.copy(address = "8.8.8.8").base())
        val groq = AssistantSettings(provider = AssistantProvider.Groq)
        assertEquals(ApiBase.Ready("https://api.groq.com/openai/v1"), groq.base())
        assertTrue(groq.missingKey())
        assertFalse(groq.copy(apiKey = "k").missingKey())
        assertFalse(ollama.missingKey())
    }

    @Test
    fun theChatBodyCarriesThePersonaAndTheLatestTurns() {
        val turns = (1..25).map { ChatTurn(fromOwner = it % 2 == 1, text = "line \"$it\"\n") }
        val body = Json.parse(ChatProtocol.chatBody("llama3.2", "be brief", turns))
        assertEquals("llama3.2", Json.at(body, "model"))
        assertEquals(false, Json.at(body, "stream"))
        val messages = Json.at(body, "messages") as List<*>
        assertEquals(ChatProtocol.MAX_TURNS + 1, messages.size)
        assertEquals("system", Json.at(messages, 0, "role"))
        assertEquals("line \"25\"\n", Json.at(messages, ChatProtocol.MAX_TURNS, "content"))
        assertEquals("user", Json.at(messages, ChatProtocol.MAX_TURNS, "role"))
    }

    @Test
    fun repliesModelsAndErrorsAreRead() {
        val ok = """{"choices":[{"message":{"role":"assistant","content":" Very good, sir. é "}}]}"""
        assertEquals(ChatReply.Text("Very good, sir. é"), ChatProtocol.reply(ok))
        assertEquals(ChatReply.Failed("model not found"), ChatProtocol.reply("""{"error":"model not found"}"""))
        assertEquals(ChatReply.Failed("Invalid key"), ChatProtocol.reply("""{"error":{"message":"Invalid key"}}"""))
        val models = """{"data":[{"id":"b-model"},{"id":"a:free"},{"id":"models/gemini-x"},{"id":"A-model"}]}"""
        assertEquals(listOf("a:free", "A-model", "b-model", "gemini-x"), ChatProtocol.models(models))
        assertEquals(emptyList<String>(), ChatProtocol.models("not json"))
    }

    @Test
    fun jsonRoundTripsWhatItWrites() {
        val value = mapOf("a" to listOf(1, true, null, "x\ty"), "b" to mapOf("c" to "\u0001"))
        val back = Json.parse(Json.write(value))
        assertEquals("x\ty", Json.at(back, "a", 3))
        assertEquals(1.0, Json.at(back, "a", 0))
        assertEquals("\u0001", Json.at(back, "b", "c"))
        assertNull(Json.parse("{\"a\":1} trailing"))
    }

    @Test
    fun theNameGivesTheCommandWord() {
        assertEquals("cosgrove", AssistantSettings().commandWord)
        assertEquals("mrhollis2", Persona.commandWord("Mr. Hollis 2"))
        assertEquals("ask", Persona.commandWord("!!"))
        assertEquals("Cosgrove", Persona.cleanName("   "))
        assertTrue(Persona.system("Cosgrove").startsWith("You are Cosgrove,"))
        assertEquals(Persona.MAX_NAME, Persona.cleanName("x".repeat(40)).length)
    }
}
