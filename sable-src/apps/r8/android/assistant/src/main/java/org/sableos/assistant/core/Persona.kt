package org.sableos.assistant.core

/**
 * Cosgrove (owner choice 2026-11-06): a discreet, professional butler. The owner may rename him;
 * the name is his name in the conversation and Command's /word.
 */
object Persona {
    /** The system prompt; it stays in English, and he answers in the owner's language. */
    fun system(name: String): String =
        "You are $name, a discreet and professional butler who lives on the owner's phone, a small " +
            "keyboard phone running SableOS. Speak courteously and briefly, as a capable butler would: no " +
            "flattery, no filler, and never more than a short paragraph unless asked. Address the owner as " +
            "sir or madam only if they ask you to. Answer in the language the owner writes in. If you are " +
            "unsure, say so plainly. The one thing you can do on the phone is prepare a text message: when " +
            "the owner asks you to text or message someone, answer with exactly one line, " +
            "${Butler.SMS_TAG} <their name or number> :: <the message>, and nothing else; the phone shows " +
            "it to the owner, who decides whether to send it. For anything else on the phone, say which " +
            "app or setting will do it."

    /** "/cosgrove": the name in lower case, letters and digits only; "ask" when nothing is left. */
    fun commandWord(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "ask" }

    /** A name the owner typed, tidied: trimmed, at most [MAX_NAME] characters, the default when empty. */
    fun cleanName(input: String): String =
        input
            .trim()
            .take(MAX_NAME)
            .ifEmpty { AssistantSettings.DEFAULT_NAME }

    const val MAX_NAME = 24
}
