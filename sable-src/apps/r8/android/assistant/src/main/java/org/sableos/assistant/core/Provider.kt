package org.sableos.assistant.core

/**
 * Where Cosgrove's answers come from. Every one speaks the OpenAI-style chat API: Ollama and
 * other servers on the owner's network, or a hosted provider under the owner's own key (their
 * free plans have limits, and the messages go to that company). Nothing is chosen until the
 * owner chooses.
 */
enum class AssistantProvider(
    /** The API base for a hosted provider; null when the owner gives the address. */
    val hostedBase: String?,
    val needsKey: Boolean,
    /** The provider's name, as it is shown and as the privacy line names it. */
    val company: String,
) {
    Ollama(null, false, "Ollama"),
    OpenRouter("https://openrouter.ai/api/v1", true, "OpenRouter"),
    Groq("https://api.groq.com/openai/v1", true, "Groq"),
    Gemini("https://generativelanguage.googleapis.com/v1beta/openai", true, "Google"),
    Custom(null, false, ""),
    ;

    val onOwnNetwork: Boolean get() = hostedBase == null

    companion object {
        fun byName(name: String?): AssistantProvider? = entries.firstOrNull { it.name == name }
    }
}

/** The owner's choices in Settings. The key stays in Cosgrove's own storage, never backed up. */
data class AssistantSettings(
    val name: String = DEFAULT_NAME,
    val provider: AssistantProvider? = null,
    val address: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    /** The API base the requests go to, or why there is none yet. */
    fun base(): ApiBase {
        val chosen = provider
        val port = if (chosen == AssistantProvider.Ollama) ServerAddress.OLLAMA_PORT else null
        val normalized = if (chosen?.onOwnNetwork == true) ServerAddress.normalize(address, port) else null
        return when {
            chosen == null -> ApiBase.Problem(SetupProblem.NoProvider)
            chosen.hostedBase != null -> ApiBase.Ready(chosen.hostedBase)
            normalized == null -> ApiBase.Problem(SetupProblem.NoAddress)
            !ServerAddress.allowed(normalized) -> ApiBase.Problem(SetupProblem.PlainHttpOutside)
            chosen == AssistantProvider.Ollama -> ApiBase.Ready("$normalized/v1")
            else -> ApiBase.Ready(normalized)
        }
    }

    fun missingKey(): Boolean = provider?.needsKey == true && apiKey.isBlank()

    /** The name with anything but letters and digits dropped, for Command's /word. */
    val commandWord: String get() = Persona.commandWord(name)

    companion object {
        const val DEFAULT_NAME = "Cosgrove"
    }
}

/** Why Cosgrove cannot answer yet; each has its own line in Settings. */
enum class SetupProblem { NoProvider, NoAddress, PlainHttpOutside }

/** The API base, or the setup problem in the way. */
sealed interface ApiBase {
    data class Ready(
        val url: String,
    ) : ApiBase

    data class Problem(
        val problem: SetupProblem,
    ) : ApiBase
}
