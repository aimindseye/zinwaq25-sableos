package org.sableos.messages.core

/**
 * Phone-number handling for SMS addresses: what the provider stores, what the user types and what
 * other apps hand over in sms:/smsto: links. No Android types, so it is unit tested.
 */
object Addresses {
    /** Shortest digit run treated as a phone number when matching numbers across formats. */
    private const val MIN_MATCH_DIGITS = 7

    /** Digits compared from the end, so "+44 7700 900123" matches "07700 900123". */
    private const val SUFFIX_MATCH_DIGITS = 9

    /** Shortest number SmsManager is given (short codes such as "611"). */
    private const val MIN_SENDABLE_DIGITS = 3

    /**
     * Number as it is sent and stored: separators removed, one leading "+" kept, letters kept
     * (alphanumeric senders such as "BANK" stay as they are). Blank input gives "".
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim().substringBefore("/TYPE=")
        // Alphanumeric senders ("BANK", "Vodafone") and e-mail gateways stay as they are.
        if (trimmed.any { it.isLetter() }) return trimmed
        return trimmed.filterIndexed { index, char ->
            char.isDigit() || char == '*' || char == '#' || (char == '+' && index == 0)
        }
    }

    /** Key used to group and look up a number: its last digits, or the normalized text. */
    fun matchKey(raw: String): String {
        val normalized = normalize(raw)
        val digits = normalized.filter { it.isDigit() }
        return if (digits.length >= MIN_MATCH_DIGITS && digits.length == normalized.trimStart('+').length) {
            digits.takeLast(SUFFIX_MATCH_DIGITS)
        } else {
            normalized.lowercase()
        }
    }

    /** Whether two addresses name the same sender, ignoring formatting and country prefixes. */
    fun same(
        first: String,
        second: String,
    ): Boolean {
        val a = matchKey(first)
        return a.isNotEmpty() && a == matchKey(second)
    }

    /** A number the user typed that SmsManager can send to (digits, optional leading "+"). */
    fun isSendable(raw: String): Boolean {
        val normalized = normalize(raw)
        val digits = normalized.count { it.isDigit() }
        return digits >= MIN_SENDABLE_DIGITS && normalized.trimStart('+').all { it.isDigit() || it == '*' || it == '#' }
    }

    /** Splits a recipient list ("a,b;c") into distinct normalized numbers, keeping order. */
    fun splitRecipients(raw: String): List<String> =
        raw
            .split(',', ';')
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .distinctBy { matchKey(it) }
}

/** Recipients and body that another app handed over with an sms:/smsto:/mms:/mmsto: link. */
data class ComposeRequest(
    val recipients: List<String>,
    val body: String,
)

object SmsLinks {
    private val schemes = setOf("sms", "smsto", "mms", "mmsto")

    fun isMessagingScheme(scheme: String?): Boolean = scheme?.lowercase() in schemes

    /**
     * Parses the scheme-specific part of a messaging link ("+1555,+1666?body=Hi"), already
     * percent-decoded. The "sms_body" extra wins over a "body" query, as in AOSP Messaging.
     */
    fun parse(
        schemeSpecificPart: String?,
        smsBodyExtra: String?,
        textExtra: String? = null,
    ): ComposeRequest {
        val part = schemeSpecificPart.orEmpty().removePrefix("//")
        val recipients = Addresses.splitRecipients(part.substringBefore('?'))
        val query = part.substringAfter('?', missingDelimiterValue = "")
        val queryBody =
            query
                .split('&')
                .firstOrNull { it.startsWith("body=") }
                ?.removePrefix("body=")
        val body = listOf(smsBodyExtra, queryBody, textExtra).firstNotNullOfOrNull { it }.orEmpty()
        return ComposeRequest(recipients = recipients, body = body)
    }
}
