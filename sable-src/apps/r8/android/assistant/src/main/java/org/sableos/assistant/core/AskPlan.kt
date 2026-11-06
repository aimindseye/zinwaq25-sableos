package org.sableos.assistant.core

/** What asking would do with the settings as they are: send a request, or show why it can't yet. */
sealed interface AskPlan {
    data class Send(
        val base: String,
        val body: String,
    ) : AskPlan

    /** [key] is the AssistantText line that says what to set up. */
    data class Blocked(
        val key: String,
    ) : AskPlan

    companion object {
        /** The chat request for [turns], the owner's latest question last. */
        fun chat(
            settings: AssistantSettings,
            turns: List<ChatTurn>,
        ): AskPlan =
            when (val target = target(settings)) {
                is Blocked -> {
                    target
                }

                is Send -> {
                    if (settings.model.isBlank()) {
                        Blocked("error_model")
                    } else {
                        Send(target.base, ChatProtocol.chatBody(settings.model, Persona.system(settings.name), turns))
                    }
                }
            }

        /** Where requests go (Test connection lists the models there); the body is empty. */
        fun target(settings: AssistantSettings): AskPlan =
            when (val base = settings.base()) {
                is ApiBase.Problem -> Blocked(problemKey(base.problem))
                is ApiBase.Ready -> if (settings.missingKey()) Blocked("error_key") else Send(base.url, "")
            }

        fun problemKey(problem: SetupProblem): String =
            when (problem) {
                SetupProblem.NoProvider -> "error_provider"
                SetupProblem.NoAddress -> "error_address"
                SetupProblem.PlainHttpOutside -> "error_plain_http"
            }
    }
}
