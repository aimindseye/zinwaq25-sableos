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
    Event("instant_event", "▦", listOf("event")),

    /** A typed web address, opened in the browser. */
    OpenAddress("instant_open_address", "↗", emptyList()),
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
 * unchanged. The quick prefixes (TenSeven's, from the MinkLauncher Magic Box) pick one action outright:
 * `-text` a to-do in Sable Tasks, `/text` a note in Sable Notes, `+text` a calendar event; a typed web
 * address opens in the browser. Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
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
        return when {
            text.isEmpty() -> emptyList()
            else -> quickRows(text) ?: addressRows(text) ?: keywordRows(text) ?: defaultRows(text)
        }
    }

    private fun quickRows(text: String): List<InstantRow>? {
        val action = QUICK[text.first()]?.takeUnless { CommandQuery.dialable(text) != null } ?: return null
        // A prefix with nothing after it does nothing yet: keep typing.
        val rest = text.drop(1).trim()
        return if (rest.isEmpty()) emptyList() else listOf(InstantRow(action, rest, chosen = true))
    }

    private fun addressRows(text: String): List<InstantRow>? =
        webAddress(text)?.let { url ->
            val sure = text.contains("://") || text.startsWith("www.", ignoreCase = true) || text.contains('/')
            listOf(
                InstantRow(InstantAction.OpenAddress, url, chosen = sure),
                InstantRow(InstantAction.WebSearch, text),
            )
        }

    private fun keywordRows(text: String): List<InstantRow>? =
        keyword(text)
            ?.takeIf { (action, rest) ->
                rest.isNotEmpty() && (action != InstantAction.Call || CommandQuery.dialable(rest) != null)
            }?.let { (action, rest) -> listOf(InstantRow(action, rest, chosen = true)) }

    private fun defaultRows(text: String): List<InstantRow> {
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

    /**
     * The quick prefixes, typed first with nothing before them. A `/` that starts a scope word
     * (`/settings`, `/apps`…) never gets here: CommandQuery takes it as that scope. Unlike TenSeven, a
     * phone number that starts with `+` stays a number (Call first), so international numbers work.
     */
    private val QUICK =
        mapOf(
            '-' to InstantAction.RemindMe,
            '/' to InstantAction.Note,
            '+' to InstantAction.Event,
        )

    private val WEB_ADDRESS =
        Regex(
            "(?i)(https?://)?(www\\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?" +
                "(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,24}(:\\d{1,5})?([/?#]\\S*)?",
        )

    /** A typed web address as a URL to open, `https://` added when missing; null for anything else. */
    fun webAddress(text: String): String? {
        val t = text.trim()
        if (t.any { it.isWhitespace() } || emailAddress(t) != null || !WEB_ADDRESS.matches(t)) return null
        return if (t.contains("://")) t else "https://$t"
    }

    /** A typed email address, for Email's recipient; null sends the text as the message instead. */
    fun emailAddress(text: String): String? = text.trim().takeIf { it.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) }
}
