package org.sableos.start.model

import com.sable.start.model.StartText.t

/** One Instant Action Command offers for typed text (owner plan 2026-11-05, item 4). */
enum class InstantAction(
    private val labelKey: String,
    val mark: String,
    /** The power-user keywords that pick this action ("remind buy milk"). */
    val keywords: List<String>,
) {
    RemindMe("instant_remind_me", "✓", listOf("remind")),
    Note("instant_note", "✎", listOf("note")),
    Call("instant_call", "☎", listOf("call", "dial")),
    Text("instant_text", "✉", listOf("text", "sms")),
    Email("instant_email", "@", listOf("email", "mail")),
    Map("instant_map", "⌖", listOf("map")),
    WebSearch("instant_web_search", "⌕", listOf("web", "g")),
    ;

    val label: String get() = t(labelKey)
}

/** An Instant Action with the text it acts on, the typed keyword already taken off. */
data class InstantRow(
    val action: InstantAction,
    val payload: String,
    /** True when the user typed the action's keyword: Enter runs it. */
    val chosen: Boolean = false,
)

/**
 * Instant Actions (BB10 style): rows under any typed text that act on it. Remind me puts it into
 * Sable Tasks, Note into Sable Notes; Call shows only for a number; Text, Email, Map and Web search
 * hand it to the user's own apps. A leading keyword ("note ", "remind me ", "call ") puts that
 * action first and makes it the one Enter runs; the other prefixes (`?`, `@`, `/settings`…) are
 * unchanged. Pure policy, unit tested in apps/r8/android/sablestart-presentation-check/src/test.
 */
object InstantActions {
    private val WORDS =
        listOf(
            InstantAction.RemindMe,
            InstantAction.Note,
            InstantAction.WebSearch,
            InstantAction.Map,
            InstantAction.Text,
            InstantAction.Email,
        )
    private val NUMBER =
        listOf(
            InstantAction.Call,
            InstantAction.Text,
            InstantAction.Note,
            InstantAction.RemindMe,
            InstantAction.WebSearch,
        )
    private val ADDRESS =
        listOf(InstantAction.Email, InstantAction.Note, InstantAction.RemindMe, InstantAction.WebSearch)

    fun forTerm(term: String): List<InstantRow> {
        val text = term.trim()
        if (text.isEmpty()) return emptyList()
        keyword(text)?.let { (action, rest) ->
            if (rest.isNotEmpty() && (action != InstantAction.Call || CommandQuery.dialable(rest) != null)) {
                return listOf(InstantRow(action, rest, chosen = true))
            }
        }
        val actions =
            when {
                CommandQuery.dialable(text) != null -> NUMBER
                emailAddress(text) != null -> ADDRESS
                else -> WORDS
            }
        return actions.map { InstantRow(it, text) }
    }

    /** The action a leading keyword names and the text after it ("remind me to call" → "to call"). */
    fun keyword(text: String): Pair<InstantAction, String>? {
        val first = text.substringBefore(' ').lowercase()
        val action = InstantAction.entries.firstOrNull { first in it.keywords } ?: return null
        var rest = text.substringAfter(' ', "").trim()
        if (action == InstantAction.RemindMe && rest.startsWith("me ", ignoreCase = true)) rest = rest.drop(3).trim()
        return action to rest
    }

    /** A typed email address, for Email's recipient; null sends the text as the message instead. */
    fun emailAddress(text: String): String? =
        text.trim().takeIf { it.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) }
}
