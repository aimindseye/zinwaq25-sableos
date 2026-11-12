package org.sableos.assistant.core

/**
 * What Cosgrove may do on the phone: prepare a text message (SMS). Nothing is ever sent until the
 * owner presses Send on the confirm card, whether the owner typed the command or the model
 * proposed it, so a reply shaped by text someone shared into the chat cannot send on its own.
 * Pure Kotlin: the activity resolves the name on the phone and sends.
 */
sealed interface ButlerAction {
    /** A text to [to], a contact's name or a phone number, as the owner or the model wrote it. */
    data class SendSms(
        val to: String,
        val body: String,
    ) : ButlerAction
}

object Butler {
    const val MAX_TO = 60
    const val MAX_BODY = 1_000

    /** The line the model answers with to propose a text (Persona.system says how). */
    const val SMS_TAG = "SEND_SMS"
    private const val SMS_SEPARATOR = "::"

    /** Words that start a typed text command. The colon keeps "text me a poem" a question. */
    private val SMS_WORDS = setOf("text", "sms")

    /**
     * "text Anna: running late" or "sms +15551234567: on my way": a text the owner typed, which
     * needs no model. Null when [input] is anything else (then it goes to the model).
     */
    fun typed(input: String): ButlerAction.SendSms? {
        val s = input.trim()
        val word = s.substringBefore(' ', "").lowercase()
        val rest = s.substringAfter(' ')
        val command = word in SMS_WORDS && ':' in rest
        return if (command) action(rest.substringBefore(':'), rest.substringAfter(':')) else null
    }

    /** "SEND_SMS Anna :: running late" on a line of its own in the model's reply; null otherwise. */
    fun proposed(reply: String): ButlerAction.SendSms? {
        val line =
            reply
                .lineSequence()
                .map { it.trim().trim('`').trim() }
                .firstOrNull { it.startsWith("$SMS_TAG ") && SMS_SEPARATOR in it } ?: return null
        val rest = line.removePrefix(SMS_TAG)
        return action(rest.substringBefore(SMS_SEPARATOR), rest.substringAfter(SMS_SEPARATOR))
    }

    private fun action(
        to: String,
        body: String,
    ): ButlerAction.SendSms? {
        val name = to.trim().trim('"', '\'').trim()
        val text = body.trim().trim('"').trim()
        if (name.isEmpty() || text.isEmpty() || name.length > MAX_TO) return null
        return ButlerAction.SendSms(name, text.take(MAX_BODY))
    }

    /** True when [to] is a phone number rather than a name: digits with + ( ) - . and spaces. */
    fun isNumber(to: String): Boolean {
        val digits = to.count { it.isDigit() }
        return digits >= MIN_DIGITS && to.all { it.isDigit() || it in NUMBER_PUNCTUATION }
    }

    private const val MIN_DIGITS = 3
    private const val NUMBER_PUNCTUATION = "+()-. "
}
