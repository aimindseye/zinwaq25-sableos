package org.sableos.assistant.core

/** One line of the conversation. */
data class ChatTurn(
    val fromOwner: Boolean,
    val text: String,
)

/** What a server said back: a reply, or why there is none. */
sealed interface ChatReply {
    data class Text(
        val text: String,
    ) : ChatReply

    data class Failed(
        val message: String,
    ) : ChatReply
}

/** The OpenAI-style chat API, as Ollama, OpenRouter, Groq, Gemini and LM Studio all speak it. */
object ChatProtocol {
    /** Only the latest turns go with each request, so a long chat stays quick on a phone. */
    const val MAX_TURNS = 20

    fun chatBody(
        model: String,
        system: String,
        turns: List<ChatTurn>,
    ): String =
        Json.write(
            mapOf(
                "model" to model,
                "stream" to false,
                "messages" to
                    listOf(mapOf("role" to "system", "content" to system)) +
                    turns.takeLast(MAX_TURNS).map { turn ->
                        mapOf("role" to if (turn.fromOwner) "user" else "assistant", "content" to turn.text)
                    },
            ),
        )

    /** The reply in an answer body, or the server's own error message. */
    fun reply(body: String): ChatReply {
        val json = Json.parse(body)
        val text = Json.at(json, "choices", 0, "message", "content") as? String
        return when {
            json == null -> ChatReply.Failed(body.take(MAX_ERROR).ifBlank { "?" })
            text != null -> ChatReply.Text(text.trim())
            else -> ChatReply.Failed(errorOf(json) ?: body.take(MAX_ERROR))
        }
    }

    /** The model ids in a /models answer, free ones first for OpenRouter, then A to Z. */
    fun models(body: String): List<String> {
        val data = Json.at(Json.parse(body), "data") as? List<*> ?: return emptyList()
        return data
            .mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
            .map { it.removePrefix("models/") }
            .distinct()
            .sortedWith(compareBy({ !it.endsWith(":free") }, { it.lowercase() }))
    }

    /** The error text of an error body ({"error": {"message": …}} or Ollama's {"error": "…"}). */
    fun errorOf(json: Any?): String? =
        (Json.at(json, "error", "message") as? String)
            ?: (Json.at(json, "error") as? String)
            ?: (Json.at(json, 0, "error", "message") as? String)

    private const val MAX_ERROR = 200
}
