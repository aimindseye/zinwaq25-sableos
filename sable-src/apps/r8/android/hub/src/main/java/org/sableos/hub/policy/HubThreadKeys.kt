package org.sableos.hub.policy

import org.sableos.hub.HubMessage

/** Keys a conversation view reacts to, mapped from Compose keys by the screen. */
enum class HubThreadKey {
    End,
    Escape,
    PageUp,
    PageDown,
    Slash,
    LetterL,
    Other,
}

/** What a key does in an open conversation. */
enum class HubThreadCommand {
    /** Not ours: the focused control (or Back) keeps it. */
    None,

    /** Scroll to the newest message and leave history/search mode. */
    Latest,

    /** One page toward older messages (history mode). */
    OlderPage,

    /** One page toward the newest message. */
    NewerPage,

    /** Open search in this conversation. */
    Search,
}

/** Where the user is in the conversation when the key arrives. */
data class HubThreadKeyContext(
    val composerFocused: Boolean,
    val draftEmpty: Boolean,
    val atLatest: Boolean,
    val searchOpen: Boolean,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
)

/**
 * MESSAGES_CONVERSATION_UX "History access without touchpad-style scrolling" on the Q25:
 *
 * - End (or Ctrl+L) returns to the latest message; in the composer End keeps moving the cursor
 *   unless the draft is empty.
 * - Esc leaves history or search and returns to latest; at latest it is Back (one layer up).
 * - PageUp / PageDown (Sym+R / Sym+E on the Q25) page older / newer, wherever focus is: the
 *   composer is at most four lines, so it has nothing to page.
 * - "/" opens search in the conversation unless it would be typed into a draft.
 *
 * Shift, Alt and Meta chords are left alone (Alt is the Q25's Fn and symbol layer).
 */
object HubThreadKeys {
    fun decide(
        key: HubThreadKey,
        context: HubThreadKeyContext,
    ): HubThreadCommand {
        if (context.meta) return HubThreadCommand.None
        if (key == HubThreadKey.LetterL) {
            return if (context.ctrl && !context.alt) HubThreadCommand.Latest else HubThreadCommand.None
        }
        if (context.ctrl || context.alt) return HubThreadCommand.None
        val typingDraft = context.composerFocused && !context.draftEmpty
        return when (key) {
            HubThreadKey.End -> {
                if (context.shift || typingDraft) HubThreadCommand.None else HubThreadCommand.Latest
            }

            HubThreadKey.Escape -> {
                if (context.atLatest && !context.searchOpen) HubThreadCommand.None else HubThreadCommand.Latest
            }

            HubThreadKey.PageUp -> {
                if (context.shift) HubThreadCommand.None else HubThreadCommand.OlderPage
            }

            HubThreadKey.PageDown -> {
                if (context.shift) HubThreadCommand.None else HubThreadCommand.NewerPage
            }

            HubThreadKey.Slash -> {
                if (typingDraft || context.searchOpen) HubThreadCommand.None else HubThreadCommand.Search
            }

            HubThreadKey.LetterL, HubThreadKey.Other -> {
                HubThreadCommand.None
            }
        }
    }

    /**
     * First visible index after paging a newest-first list ([firstVisible] is the index of the
     * newest visible message, 0 = latest). Pages overlap by one message so context carries over.
     */
    fun pageTarget(
        firstVisible: Int,
        visibleCount: Int,
        count: Int,
        older: Boolean,
    ): Int {
        if (count <= 0) return 0
        val step = (visibleCount - 1).coerceAtLeast(1)
        val target = if (older) firstVisible + step else firstVisible - step
        return target.coerceIn(0, count - 1)
    }
}

/**
 * Search inside one conversation: matches over the newest-first message list, so match 0 is the
 * newest. Only text the (redacted) snapshot holds can match.
 */
object HubThreadSearch {
    /** Indices into [newestFirst] whose text contains [query], newest first. */
    fun matches(
        newestFirst: List<HubMessage>,
        query: String,
    ): List<Int> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return newestFirst.indices.filter { index ->
            newestFirst[index].body.contains(term, ignoreCase = true) ||
                newestFirst[index].senderLabel.orEmpty().contains(term, ignoreCase = true)
        }
    }

    /** The next match position: Enter goes older (wraps to newest), Shift+Enter newer. */
    fun step(
        current: Int,
        matchCount: Int,
        older: Boolean,
    ): Int =
        when {
            matchCount <= 0 -> -1
            current !in 0 until matchCount -> 0
            older -> (current + 1) % matchCount
            else -> (current - 1 + matchCount) % matchCount
        }

    /** "2 of 5", "No matches" or empty while nothing is typed. */
    fun status(
        current: Int,
        matchCount: Int,
        query: String,
    ): String =
        when {
            query.isBlank() -> ""
            matchCount == 0 -> "No matches"
            else -> "${current.coerceAtLeast(0) + 1} of $matchCount"
        }
}

/**
 * The new-message pill (MESSAGES_CONVERSATION_UX `HISTORY_MODE_SHOWS_NEW_MESSAGE_INDICATOR`):
 * while the user reads history, a message that arrives is not scrolled to; the pill says so and
 * End, Esc or Enter on it returns to latest.
 */
object HubNewMessagePill {
    /**
     * [seenNewestId]: the newest message id the user has seen at latest; [newest]: the newest
     * message now. The pill shows when something newer than that arrived while not at latest.
     */
    fun show(
        atLatest: Boolean,
        seenNewestId: Long?,
        newest: HubMessage?,
    ): Boolean = !atLatest && newest != null && newest.incoming && seenNewestId != null && newest.id != seenNewestId

    const val LABEL = "New message · Enter for latest"
}
