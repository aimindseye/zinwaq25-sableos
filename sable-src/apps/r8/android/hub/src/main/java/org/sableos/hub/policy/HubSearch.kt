package org.sableos.hub.policy

import org.sableos.hub.HubCall
import org.sableos.hub.HubConversation

/**
 * Hub search (SABLE_HUB_KEYBOARD_FIRST_UX "Type input and search"): local, never the web. Plain
 * words must all match a row's name, number, text or source. These words narrow instead:
 *
 * - `unread`: conversations with unread messages (and calls the user did not take)
 * - `missed`: missed calls and voicemail only
 * - `today`: rows since the start of today
 * - `from:<name or number>`: who the row is from
 * - `app:<source>`: the source app's label (`app:sms` for Hub's own SMS)
 *
 * Search only sees what the redacted snapshot holds, so it cannot find hidden text.
 */
data class HubSearch(
    val words: List<String> = emptyList(),
    val unread: Boolean = false,
    val missed: Boolean = false,
    val today: Boolean = false,
    val from: String? = null,
    val app: String? = null,
) {
    val isEmpty: Boolean
        get() = words.isEmpty() && !unread && !missed && !today && from == null && app == null

    fun matches(
        conversation: HubConversation,
        todayStartMillis: Long,
    ): Boolean {
        if (missed) return false
        if (unread && conversation.unreadCount <= 0) return false
        if (today && conversation.lastDateMillis < todayStartMillis) return false
        if (from != null && !conversation.isFrom(from)) return false
        if (app != null && !sourceLabel(conversation).contains(app, ignoreCase = true)) return false
        return words.all { word -> conversation.hasText(word) }
    }

    fun matches(
        call: HubCall,
        todayStartMillis: Long,
    ): Boolean {
        if ((unread || missed) && !call.kind.needsAttention) return false
        if (today && call.dateMillis < todayStartMillis) return false
        if (from != null && !call.isFrom(from)) return false
        if (app != null && !CALLS_SOURCE.contains(app, ignoreCase = true)) return false
        return words.all { word -> call.isFrom(word) }
    }

    companion object {
        const val UNREAD = "unread"
        const val MISSED = "missed"
        const val TODAY = "today"
        const val FROM_PREFIX = "from:"
        const val APP_PREFIX = "app:"

        /** Source label calls answer to with `app:` (the Phone app owns the call log). */
        const val CALLS_SOURCE = "Phone calls"

        /** Words of the search hint under the field. */
        const val HINT = "unread · missed · today · from: · app:"

        fun parse(raw: String): HubSearch {
            var search = HubSearch()
            val words = mutableListOf<String>()
            raw.trim().split(WHITESPACE).filter(String::isNotEmpty).forEach { token ->
                val lower = token.lowercase()
                search =
                    when {
                        lower == UNREAD -> search.copy(unread = true)
                        lower == MISSED -> search.copy(missed = true)
                        lower == TODAY -> search.copy(today = true)
                        lower.startsWith(FROM_PREFIX) && lower.length > FROM_PREFIX.length ->
                            search.copy(from = token.substring(FROM_PREFIX.length))
                        lower.startsWith(APP_PREFIX) && lower.length > APP_PREFIX.length ->
                            search.copy(app = token.substring(APP_PREFIX.length))
                        else -> {
                            words += token
                            search
                        }
                    }
            }
            return search.copy(words = words)
        }

        private val WHITESPACE = Regex("\\s+")

        private fun sourceLabel(conversation: HubConversation): String =
            conversation.sourceLabel ?: HubRedaction.SMS_LABEL

        private fun HubConversation.isFrom(term: String): Boolean =
            displayName.contains(term, ignoreCase = true) || address.contains(term, ignoreCase = true)

        private fun HubConversation.hasText(term: String): Boolean =
            isFrom(term) ||
                lastBody.contains(term, ignoreCase = true) ||
                sourceLabel.orEmpty().contains(term, ignoreCase = true)

        private fun HubCall.isFrom(term: String): Boolean =
            displayName.contains(term, ignoreCase = true) || number.contains(term, ignoreCase = true)
    }
}
