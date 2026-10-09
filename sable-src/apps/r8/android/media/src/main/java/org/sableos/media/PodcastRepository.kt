package org.sableos.media

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class PodcastEpisode(
    val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val title: String,
    val mediaUrl: String,
    val description: String,
    val publishedAt: Long,
    val durationMs: Long,
)

internal data class PodcastSubscription(
    val id: String,
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val episodes: List<PodcastEpisode>,
    val refreshedAt: Long,
)

internal data class PodcastRefreshResult(
    val subscriptions: List<PodcastSubscription>,
    val failedFeeds: Int,
)

internal class PodcastRepository(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PODCAST_PREFS,
            Context.MODE_PRIVATE,
        )

    fun loadSubscriptions(): List<PodcastSubscription> {
        val raw = preferences.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                decodeSubscription(array.optJSONObject(index))
            }.filterNotNull()
                .sortedBy { it.title.lowercase(Locale.getDefault()) }
        }.getOrDefault(emptyList())
    }

    fun subscribe(
        current: List<PodcastSubscription>,
        feedUrl: String,
    ): List<PodcastSubscription> {
        val normalized = feedUrl.trim()
        require(isSupportedFeedUrl(normalized)) {
            "Podcast feed must use HTTPS."
        }

        val fetched = fetch(normalized)
        val updated =
            (
                current.filterNot {
                    it.feedUrl.equals(normalized, ignoreCase = true)
                } + fetched
            ).sortedBy { it.title.lowercase(Locale.getDefault()) }

        persist(updated)
        return updated
    }

    fun refreshAll(current: List<PodcastSubscription>): PodcastRefreshResult {
        if (current.isEmpty()) {
            return PodcastRefreshResult(
                subscriptions = emptyList(),
                failedFeeds = 0,
            )
        }

        var failures = 0
        val refreshed =
            current
                .map { subscription ->
                    runCatching {
                        fetch(subscription.feedUrl)
                    }.getOrElse {
                        failures += 1
                        subscription
                    }
                }.sortedBy { it.title.lowercase(Locale.getDefault()) }

        persist(refreshed)
        return PodcastRefreshResult(
            subscriptions = refreshed,
            failedFeeds = failures,
        )
    }

    fun remove(
        current: List<PodcastSubscription>,
        podcastId: String,
    ): List<PodcastSubscription> {
        val updated = current.filterNot { it.id == podcastId }
        persist(updated)
        return updated
    }

    private fun fetch(feedUrl: String): PodcastSubscription {
        val connection = openConnection(feedUrl)

        try {
            require(connection.responseCode in HTTP_SUCCESS_RANGE) {
                "Podcast feed returned HTTP ${connection.responseCode}."
            }

            val resolvedFeedUrl =
                connection.url
                    ?.toString()
                    ?.takeIf(String::isNotBlank)
                    ?: feedUrl

            require(isSupportedFeedUrl(resolvedFeedUrl)) {
                "Podcast feed redirected to a non-HTTPS URL."
            }

            val bytes =
                connection.inputStream.use(::readLimited)

            return PodcastFeedParser.parse(
                feedUrl = resolvedFeedUrl,
                bytes = bytes,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(feedUrl: String): HttpURLConnection =
        (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = HTTP_METHOD_GET
            setRequestProperty(
                HEADER_ACCEPT,
                PODCAST_ACCEPT,
            )
            setRequestProperty(
                HEADER_USER_AGENT,
                USER_AGENT,
            )
        }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var total = 0
        var read = input.read(buffer)

        while (read >= 0) {
            total += read
            require(total <= MAX_FEED_BYTES) {
                "Podcast feed is larger than the Sable Media safety limit."
            }
            output.write(buffer, 0, read)
            read = input.read(buffer)
        }

        return output.toByteArray()
    }

    private fun persist(subscriptions: List<PodcastSubscription>) {
        val array = JSONArray()
        subscriptions.forEach { subscription ->
            array.put(encodeSubscription(subscription))
        }

        preferences
            .edit()
            .putString(KEY_SUBSCRIPTIONS, array.toString())
            .apply()
    }

    private fun encodeSubscription(subscription: PodcastSubscription): JSONObject =
        JSONObject()
            .put("id", subscription.id)
            .put("feedUrl", subscription.feedUrl)
            .put("title", subscription.title)
            .put("author", subscription.author)
            .put("description", subscription.description)
            .put("refreshedAt", subscription.refreshedAt)
            .put(
                "episodes",
                JSONArray().apply {
                    subscription.episodes.forEach { episode ->
                        put(encodeEpisode(episode))
                    }
                },
            )

    private fun encodeEpisode(episode: PodcastEpisode): JSONObject =
        JSONObject()
            .put("id", episode.id)
            .put("podcastId", episode.podcastId)
            .put("podcastTitle", episode.podcastTitle)
            .put("title", episode.title)
            .put("mediaUrl", episode.mediaUrl)
            .put("description", episode.description)
            .put("publishedAt", episode.publishedAt)
            .put("durationMs", episode.durationMs)

    private fun decodeSubscription(item: JSONObject?): PodcastSubscription? =
        item?.let { json ->
            val id = json.optString("id")
            val feedUrl = json.optString("feedUrl")
            val title = json.optString("title")
            val validSubscription =
                id.isNotBlank() &&
                    title.isNotBlank() &&
                    isSupportedFeedUrl(feedUrl)

            if (!validSubscription) {
                null
            } else {
                val episodesArray = json.optJSONArray("episodes") ?: JSONArray()
                val episodes =
                    List(episodesArray.length()) { index ->
                        decodeEpisode(
                            item = episodesArray.optJSONObject(index),
                            podcastId = id,
                            podcastTitle = title,
                        )
                    }.filterNotNull()

                PodcastSubscription(
                    id = id,
                    feedUrl = feedUrl,
                    title = title,
                    author = json.optString("author"),
                    description = json.optString("description"),
                    episodes = episodes,
                    refreshedAt = json.optLong("refreshedAt", 0L),
                )
            }
        }

    private fun decodeEpisode(
        item: JSONObject?,
        podcastId: String,
        podcastTitle: String,
    ): PodcastEpisode? =
        item?.let { json ->
            val id = json.optString("id")
            val mediaUrl = json.optString("mediaUrl")
            val title = json.optString("title")
            val validEpisode =
                id.isNotBlank() &&
                    title.isNotBlank() &&
                    PodcastUrlPolicy.isHttpsUrl(mediaUrl)

            if (!validEpisode) {
                null
            } else {
                PodcastEpisode(
                    id = id,
                    podcastId = podcastId,
                    podcastTitle =
                        json
                            .optString("podcastTitle")
                            .ifBlank { podcastTitle },
                    title = title,
                    mediaUrl = mediaUrl,
                    description = json.optString("description"),
                    publishedAt = json.optLong("publishedAt", 0L),
                    durationMs = json.optLong("durationMs", 0L),
                )
            }
        }

    companion object {
        fun isSupportedFeedUrl(value: String): Boolean = PodcastUrlPolicy.isHttpsUrl(value)

        private const val PODCAST_PREFS = "sable_media_podcasts"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val USER_AGENT =
            "SableMedia/0.2 (Android; privacy-first podcast client)"
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_FEED_BYTES = 4 * 1_024 * 1_024
        private const val READ_BUFFER_BYTES = 16 * 1_024
        private const val HTTP_METHOD_GET = "GET"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val PODCAST_ACCEPT =
            "application/rss+xml, application/atom+xml, " +
                "application/xml, text/xml;q=0.9, */*;q=0.1"
        private val HTTP_SUCCESS_RANGE = 200..299
    }
}

internal class PodcastStateRepository(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            STATE_PREFS,
            Context.MODE_PRIVATE,
        )

    fun loadSavedEpisodeIds(): Set<String> =
        preferences
            .getStringSet(KEY_SAVED, emptySet())
            ?.toSet()
            .orEmpty()

    fun loadPlayedEpisodeIds(): Set<String> =
        preferences
            .getStringSet(KEY_PLAYED, emptySet())
            ?.toSet()
            .orEmpty()

    fun toggleSaved(
        current: Set<String>,
        episodeId: String,
    ): Set<String> {
        val updated =
            current
                .toMutableSet()
                .apply {
                    if (!add(episodeId)) {
                        remove(episodeId)
                    }
                }.toSet()

        persistSet(
            key = KEY_SAVED,
            value = updated,
        )
        return updated
    }

    fun markPlayed(
        current: Set<String>,
        episodeId: String,
    ): Set<String> {
        val updated =
            if (episodeId in current) {
                current
            } else {
                current + episodeId
            }

        if (updated !== current) {
            persistSet(
                key = KEY_PLAYED,
                value = updated,
            )
        }

        return updated
    }

    private fun persistSet(
        key: String,
        value: Set<String>,
    ) {
        preferences
            .edit()
            .putStringSet(key, value)
            .apply()
    }

    private companion object {
        const val STATE_PREFS = "sable_media_podcast_state"
        const val KEY_SAVED = "saved_episode_ids"
        const val KEY_PLAYED = "played_episode_ids"
    }
}
