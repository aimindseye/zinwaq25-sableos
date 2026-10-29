package org.sableos.media

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.sableos.design.SableMiniPlayer
import org.sableos.design.SableMiniPlayerInput
import org.sableos.design.SableMiniPlayerModel
import org.sableos.design.SableMiniPlayerPolicy
import org.sableos.design.SablePlaybackState
import org.sableos.design.SableRowAction
import org.sableos.design.SableSpacing
import org.sableos.media.MediaText.t

/*
 * DESIGN-KF-D part 2 adoption in Sable Media: the mini-player always states
 * what is playing and whether it is playing, it is pinned below the list so the
 * current state is visible on open (no scrolling to discover it), and secondary
 * transport actions sit in its menu on constrained layouts.
 */

internal val NOTHING_QUEUED_TITLE: String get() = t("nothing_queued")

internal fun PlaybackUiState.miniPlayerModel(): SableMiniPlayerModel? {
    val idle = title == NOTHING_QUEUED_TITLE && queue.isEmpty() && !isPlaying
    return SableMiniPlayerPolicy.present(
        SableMiniPlayerInput(
            title = title.takeUnless { it == NOTHING_QUEUED_TITLE },
            source = source,
            state =
                when {
                    idle -> SablePlaybackState.Idle
                    isPlaying -> SablePlaybackState.Playing
                    else -> SablePlaybackState.Paused
                },
            positionMs = positionMs,
            durationMs = durationMs,
        ),
    )
}

private val PreviousAction: SableRowAction get() = SableRowAction("previous", t("previous"))
private val NextAction: SableRowAction get() = SableRowAction("next", t("next"))
private val QueueAction: SableRowAction get() = SableRowAction("queue", t("up_next_2"))

/** Pinned (non-scrolling) mini-player shared by Collection, Podcasts and Radio. */
@Composable
internal fun MediaPinnedMiniPlayer(
    playback: PlaybackUiState,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val model = remember(playback) { playback.miniPlayerModel() } ?: return
    val menu =
        buildList {
            if (playback.hasPrevious) add(PreviousAction)
            if (playback.hasNext) add(NextAction)
            if (playback.queue.size > 1) add(QueueAction)
        }
    SableMiniPlayer(
        model = model,
        onToggle = onPlayPause,
        onOpen = onOpenNowPlaying,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SableSpacing.Lg, vertical = SableSpacing.Sm),
        menuActions = menu,
        onMenuAction = { action ->
            when (action.id) {
                PreviousAction.id -> onPrevious()
                NextAction.id -> onNext()
                QueueAction.id -> onOpenQueue()
            }
        },
    )
}
