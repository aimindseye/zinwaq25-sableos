package org.sableos.messages.core

/**
 * MESSAGES_CONVERSATION_UX latest-anchored policy: a thread opens at its newest message, sending
 * and receiving advance to it, and while the user reads older history an incoming message shows
 * "New message — End to latest" instead of yanking the view.
 */
data class LatestState(
    /** The newest message is in view. */
    val atLatest: Boolean = true,
    /** The view must scroll to the newest message on the next frame. */
    val scrollToLatest: Boolean = true,
    /** Incoming messages that arrived while the user was reading history. */
    val unseenIncoming: Int = 0,
) {
    val showNewMessageIndicator: Boolean get() = !atLatest && unseenIncoming > 0
    val showReturnToLatest: Boolean get() = !atLatest
}

sealed interface LatestEvent {
    data object Opened : LatestEvent

    data object Sent : LatestEvent

    data class Arrived(
        val incoming: Int,
        val outgoing: Int,
    ) : LatestEvent

    data object ReturnToLatest : LatestEvent

    /** The list scrolled; [lastVisibleIndex] of [count] items is the last one on screen. */
    data class Viewport(
        val lastVisibleIndex: Int,
        val count: Int,
    ) : LatestEvent

    /** The view finished the scroll that [LatestState.scrollToLatest] asked for. */
    data object Scrolled : LatestEvent
}

object LatestPolicy {
    fun reduce(
        state: LatestState,
        event: LatestEvent,
    ): LatestState =
        when (event) {
            LatestEvent.Opened, LatestEvent.Sent, LatestEvent.ReturnToLatest -> {
                LatestState(atLatest = true, scrollToLatest = true, unseenIncoming = 0)
            }

            is LatestEvent.Arrived -> {
                arrived(state, event)
            }

            is LatestEvent.Viewport -> {
                val atLatest = event.count == 0 || event.lastVisibleIndex >= event.count - 1
                state.copy(
                    atLatest = atLatest,
                    unseenIncoming = if (atLatest) 0 else state.unseenIncoming,
                )
            }

            LatestEvent.Scrolled -> {
                state.copy(scrollToLatest = false, atLatest = true, unseenIncoming = 0)
            }
        }

    private fun arrived(
        state: LatestState,
        event: LatestEvent.Arrived,
    ): LatestState =
        when {
            // A message the user just sent always brings them back to latest.
            event.outgoing > 0 -> LatestState(atLatest = true, scrollToLatest = true, unseenIncoming = 0)

            event.incoming == 0 -> state

            state.atLatest -> state.copy(scrollToLatest = true)

            else -> state.copy(unseenIncoming = state.unseenIncoming + event.incoming)
        }

    /** Messages in [messages] that were not in [previousKeys], by direction. */
    fun arrivals(
        previousKeys: Set<String>,
        messages: List<ConversationMessage>,
    ): LatestEvent.Arrived {
        val fresh = messages.filter { it.key !in previousKeys }
        return LatestEvent.Arrived(
            incoming = fresh.count { it.direction == Direction.Incoming },
            outgoing = fresh.count { it.direction == Direction.Outgoing },
        )
    }
}
