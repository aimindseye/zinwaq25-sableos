package org.sableos.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableAdaptiveTopNav
import org.sableos.design.SableDenseRow
import org.sableos.design.SableDestination
import org.sableos.design.SableRowAction
import org.sableos.design.SableSpacing
import java.text.DateFormat
import java.util.Date

private const val LATEST_EPISODE_LIMIT = 36
private const val PODCAST_SEED_MULTIPLIER = 31
private const val MILLIS_PER_MINUTE = 60_000L
private val PodcastHeroSize = 72.dp
private val PodcastArtworkSize = 58.dp

private enum class PodcastPivot(
    val label: String,
) {
    Latest("latest"),
    Shows("shows"),
    Saved("saved"),
}

internal data class PodcastScreenState(
    val subscriptions: List<PodcastSubscription>,
    val savedEpisodeIds: Set<String>,
    val playedEpisodeIds: Set<String>,
    val feedUrl: String,
    val busy: Boolean,
    val message: String?,
    val playback: PlaybackUiState,
    val showMiniPlayer: Boolean,
)

internal data class PodcastScreenActions(
    val onFeedUrlChange: (String) -> Unit,
    val onAddFeed: () -> Unit,
    val onRemoveSubscription: (PodcastSubscription) -> Unit,
    val onPlayEpisode: (PodcastEpisode) -> Unit,
    val onQueueEpisode: (PodcastEpisode) -> Unit,
    val onToggleSavedEpisode: (PodcastEpisode) -> Unit,
    val onOpenNowPlaying: () -> Unit,
)

@Composable
internal fun PodcastScreen(
    state: PodcastScreenState,
    actions: PodcastScreenActions,
) {
    val subscriptions = state.subscriptions
    val savedEpisodeIds = state.savedEpisodeIds
    val playedEpisodeIds = state.playedEpisodeIds
    val feedUrl = state.feedUrl
    val busy = state.busy
    val message = state.message
    val onFeedUrlChange = actions.onFeedUrlChange
    val onAddFeed = actions.onAddFeed
    val onRemoveSubscription = actions.onRemoveSubscription
    val onPlayEpisode = actions.onPlayEpisode
    val onQueueEpisode = actions.onQueueEpisode
    val onToggleSavedEpisode = actions.onToggleSavedEpisode
    var pivot by remember { mutableStateOf(PodcastPivot.Latest) }

    val latest =
        remember(subscriptions) {
            subscriptions
                .flatMap { it.episodes }
                .sortedWith(
                    compareByDescending<PodcastEpisode> { it.publishedAt }
                        .thenBy { it.title.lowercase() },
                ).take(LATEST_EPISODE_LIMIT)
        }

    val saved =
        remember(subscriptions, savedEpisodeIds) {
            subscriptions
                .flatMap { it.episodes }
                .filter { it.id in savedEpisodeIds }
                .sortedWith(
                    compareByDescending<PodcastEpisode> { it.publishedAt }
                        .thenBy { it.title.lowercase() },
                )
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        PodcastStatusDeck(
            subscriptionCount = subscriptions.size,
            episodeCount = latest.size,
        )

        SableAdaptiveTopNav(
            destinations = PODCAST_DESTINATIONS,
            selectedId = pivot.name,
            onSelect = { id -> pivot = PodcastPivot.valueOf(id) },
            accent = MediaPink,
        )

        when (pivot) {
            PodcastPivot.Latest -> {
                if (latest.isEmpty()) {
                    PodcastEmptyPanel(
                        title = "No episodes yet",
                        detail =
                            "Add an RSS or Atom feed. Sable Media contacts only feeds you explicitly subscribe to.",
                    )
                } else {
                    latest.forEach { episode ->
                        PodcastEpisodeRow(
                            episode = episode,
                            saved = episode.id in savedEpisodeIds,
                            played = episode.id in playedEpisodeIds,
                            onPlay = { onPlayEpisode(episode) },
                            onQueue = { onQueueEpisode(episode) },
                            onToggleSaved = { onToggleSavedEpisode(episode) },
                        )
                    }
                }
            }

            PodcastPivot.Shows -> {
                if (subscriptions.isEmpty()) {
                    PodcastEmptyPanel(
                        title = "No subscriptions",
                        detail =
                            "Paste a direct podcast feed URL below. " +
                                "There is no account, recommendation profile, " +
                                "or ambient directory lookup.",
                    )
                } else {
                    subscriptions.forEach { subscription ->
                        PodcastSubscriptionCard(
                            subscription = subscription,
                            onRemove = { onRemoveSubscription(subscription) },
                        )
                    }
                }
            }

            PodcastPivot.Saved -> {
                if (saved.isEmpty()) {
                    PodcastEmptyPanel(
                        title = "Nothing saved",
                        detail =
                            "Save an episode to keep it in a local listening list. Saved does not imply downloaded.",
                    )
                } else {
                    saved.forEach { episode ->
                        PodcastEpisodeRow(
                            episode = episode,
                            saved = true,
                            played = episode.id in playedEpisodeIds,
                            onPlay = { onPlayEpisode(episode) },
                            onQueue = { onQueueEpisode(episode) },
                            onToggleSaved = { onToggleSavedEpisode(episode) },
                        )
                    }
                }
            }
        }

        PodcastFeedEntry(
            value = feedUrl,
            enabled = !busy,
            onValueChange = onFeedUrlChange,
            onAdd = onAddFeed,
        )

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PodcastPrivacyNote()
        // The mini-player is pinned below the list by MediaScreen (current state visible on open).
    }
}

private val PODCAST_DESTINATIONS = PodcastPivot.entries.map { SableDestination(it.name, it.label) }

@Composable
private fun PodcastStatusDeck(
    subscriptionCount: Int,
    episodeCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(SableSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PodcastArtworkTile(
                seed = subscriptionCount * PODCAST_SEED_MULTIPLIER + episodeCount,
                label = "P",
                size = PodcastHeroSize,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "$subscriptionCount shows",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text =
                        if (episodeCount == 1) {
                            "1 recent episode"
                        } else {
                            "$episodeCount recent episodes"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    saved: Boolean,
    played: Boolean,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onToggleSaved: () -> Unit,
) {
    // Dense row: title (2 lines), one-line show/meta, play as the single trailing
    // action; queue and save move to the row menu (also on the Menu key).
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color =
            if (played) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        SableDenseRow(
            title = episode.title,
            subtitle = listOf(episode.podcastTitle, podcastEpisodeMeta(episode)).filter { it.isNotBlank() }.joinToString(" · "),
            onClick = onPlay,
            modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Xs),
            actions =
                listOf(
                    SableRowAction(EPISODE_ACTION_PLAY, "Play", primary = true),
                    SableRowAction(EPISODE_ACTION_QUEUE, "Add to up next"),
                    SableRowAction(EPISODE_ACTION_SAVE, if (saved) "Remove from saved ★" else "Save ☆"),
                ),
            onAction = { action ->
                when (action.id) {
                    EPISODE_ACTION_PLAY -> onPlay()
                    EPISODE_ACTION_QUEUE -> onQueue()
                    EPISODE_ACTION_SAVE -> onToggleSaved()
                }
            },
            leading = {
                PodcastArtworkTile(
                    seed = episode.podcastId.hashCode(),
                    label =
                        episode.podcastTitle
                            .trim()
                            .firstOrNull()
                            ?.uppercase()
                            ?: "P",
                )
            },
        )
    }
}

private const val EPISODE_ACTION_PLAY = "play"
private const val EPISODE_ACTION_QUEUE = "queue"
private const val EPISODE_ACTION_SAVE = "save"

@Composable
private fun PodcastSubscriptionCard(
    subscription: PodcastSubscription,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PodcastArtworkTile(
                    seed = subscription.id.hashCode(),
                    label =
                        subscription.title
                            .trim()
                            .firstOrNull()
                            ?.uppercase()
                            ?: "P",
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = subscription.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subscription.author.isNotBlank()) {
                        Text(
                            text = subscription.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (subscription.description.isNotBlank()) {
                Text(
                    text = subscription.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text =
                    "${subscription.episodes.size} episodes · refreshed ${formatPodcastDate(subscription.refreshedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SableActionButton(
                text = "Remove show",
                primary = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun PodcastFeedEntry(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Md),
        ) {
            Text(
                text = "add a show",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Paste the publisher's RSS or Atom feed URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = {
                    Text("Feed URL")
                },
                placeholder = {
                    Text("https://example.org/podcast/feed.xml")
                },
            )
            SableActionButton(
                text = if (enabled) "Subscribe" else "Working…",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (enabled && value.isNotBlank()) {
                        onAdd()
                    }
                },
            )
        }
    }
}

@Composable
private fun PodcastPrivacyNote() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
    ) {
        Text(
            text = "network model",
            style = MaterialTheme.typography.labelLarge,
            color = MediaPink,
        )
        Text(
            text =
                "Sable Media refreshes only feeds you add and streams only episodes you choose. " +
                    "No podcast account, analytics SDK, recommendation profile, " +
                    "or automatic directory query is built into this flow.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PodcastArtworkTile(
    seed: Int,
    label: String,
    size: androidx.compose.ui.unit.Dp = PodcastArtworkSize,
) {
    val palette =
        listOf(
            MediaPurple,
            MediaBlue,
            MediaPink,
            MediaOrange,
            MediaGreen,
        )
    val accent = palette[Math.floorMod(seed, palette.size)]

    Box(
        modifier =
            Modifier
                .width(size)
                .aspectRatio(1f)
                .background(
                    brush =
                        Brush.linearGradient(
                            listOf(
                                accent,
                                MediaPurple,
                                MediaPink,
                            ),
                        ),
                    shape = MaterialTheme.shapes.medium,
                ),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Light,
            color = Color.White,
        )
    }
}

@Composable
private fun PodcastEmptyPanel(
    title: String,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun podcastEpisodeMeta(episode: PodcastEpisode): String {
    val pieces = mutableListOf<String>()

    if (episode.publishedAt > 0L) {
        pieces += formatPodcastDate(episode.publishedAt)
    }
    if (episode.durationMs > 0L) {
        val minutes = episode.durationMs / MILLIS_PER_MINUTE
        pieces +=
            when {
                minutes < 1L -> "< 1 min"
                minutes == 1L -> "1 min"
                else -> "$minutes min"
            }
    }

    return pieces.joinToString(" · ").ifBlank { "episode" }
}

private fun formatPodcastDate(epochMs: Long): String =
    if (epochMs <= 0L) {
        "unknown"
    } else {
        DateFormat
            .getDateInstance(DateFormat.MEDIUM)
            .format(Date(epochMs))
    }
