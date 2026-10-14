package org.sableos.hub.policy

/** What a hardware Enter does in Hub's inline composer. */
enum class ComposerEnterAction {
    /** Send the draft; the key is consumed. */
    Send,

    /** Let the text field insert a line break (Shift+Enter). */
    Newline,

    /** Not the composer's key (Alt+Enter is reserved for "open source app"). */
    PassThrough,

    /** Consume and do nothing: an empty draft never inserts a stray line break or sends. */
    Swallow,
}

/**
 * MESSAGES_CONVERSATION_UX "Enter send / Shift+Enter newline". The Q25 keyboard has no Fn key,
 * so only Shift, Alt and (external keyboards) Ctrl modify Enter.
 */
object HubComposerKeys {
    fun enterAction(
        shift: Boolean,
        alt: Boolean,
        body: String,
        canSend: Boolean,
    ): ComposerEnterAction =
        when {
            shift -> ComposerEnterAction.Newline
            alt -> ComposerEnterAction.PassThrough
            body.isBlank() || !canSend -> ComposerEnterAction.Swallow
            else -> ComposerEnterAction.Send
        }
}

/**
 * Unsent text per thread (DRAFT_PERSISTENCE). SMS drafts are keyed by the normalized address so
 * a draft written before the first message (no thread id yet) is the same draft afterwards.
 */
object HubDrafts {
    const val MAX_DRAFTS = 64

    fun smsKey(address: String): String = "sms:" + HubAddress.normalize(address)

    fun connectedKey(threadId: Long): String = "app:$threadId"

    fun composeKey(recipient: String): String = "compose:" + HubAddress.normalize(recipient)

    /**
     * [drafts] with [key] set to [body]; an empty body removes the draft. The newest edit is kept
     * last, and the oldest drafts are dropped beyond [MAX_DRAFTS].
     */
    fun update(
        drafts: Map<String, String>,
        key: String,
        body: String,
    ): Map<String, String> {
        val updated = LinkedHashMap(drafts)
        updated.remove(key)
        if (body.isNotEmpty()) {
            updated[key] = body
        }
        while (updated.size > MAX_DRAFTS) {
            updated.remove(updated.keys.first())
        }
        return updated
    }
}

/** Address matching for SMS threads, independent of formatting ("+1 (555) 010-0100" == "+15550100100"). */
object HubAddress {
    fun normalize(address: String): String {
        val trimmed = address.trim()
        val dialable =
            trimmed.filterIndexed { index, char ->
                char.isDigit() || (char == '+' && index == 0)
            }
        // Alphanumeric senders ("BANK") have no digits: compare them case-insensitively instead.
        return if (dialable.any(Char::isDigit) && trimmed.none(Char::isLetter)) {
            dialable
        } else {
            trimmed.lowercase()
        }
    }

    fun sameAddress(
        first: String,
        second: String,
    ): Boolean = first.isNotBlank() && normalize(first) == normalize(second)
}
