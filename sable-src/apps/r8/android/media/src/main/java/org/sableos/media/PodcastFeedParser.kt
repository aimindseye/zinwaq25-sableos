package org.sableos.media

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal object PodcastUrlPolicy {
    fun isHttpsUrl(value: String): Boolean =
        runCatching {
            URL(value.trim()).protocol.lowercase(Locale.US)
        }.getOrNull() == HTTPS_SCHEME

    private const val HTTPS_SCHEME = "https"
}

internal object PodcastFeedParser {
    fun parse(
        feedUrl: String,
        bytes: ByteArray,
    ): PodcastSubscription {
        rejectDocumentType(bytes)
        val parser = newParser(bytes)
        val state =
            FeedParseState(
                podcastId = stableId(feedUrl),
            )

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            consumeEvent(
                parser = parser,
                state = state,
            )
            parser.next()
        }

        return state.toSubscription(feedUrl)
    }

    private fun rejectDocumentType(bytes: ByteArray) {
        val prologLength = minOf(bytes.size, PROLOG_SCAN_BYTES)
        val prolog =
            String(
                bytes,
                0,
                prologLength,
                StandardCharsets.ISO_8859_1,
            )

        require(!prolog.contains(DOCTYPE_TOKEN, ignoreCase = true)) {
            "Podcast feed document types are not accepted."
        }
    }

    private fun newParser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(
                XmlPullParser.FEATURE_PROCESS_NAMESPACES,
                false,
            )
            setInput(
                ByteArrayInputStream(bytes),
                null,
            )
        }

    private fun consumeEvent(
        parser: XmlPullParser,
        state: FeedParseState,
    ) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> {
                consumeStartTag(
                    parser = parser,
                    state = state,
                )
            }

            XmlPullParser.TEXT,
            XmlPullParser.CDSECT,
            -> {
                consumeText(
                    parser = parser,
                    state = state,
                )
            }

            XmlPullParser.END_TAG -> {
                consumeEndTag(
                    parser = parser,
                    state = state,
                )
            }
        }
    }

    private fun consumeStartTag(
        parser: XmlPullParser,
        state: FeedParseState,
    ) {
        val tag = localName(parser.name)
        state.currentTag = tag

        when {
            tag.isEpisodeContainer() -> {
                state.beginEpisode()
            }

            state.inEpisode && tag.isMediaContainer() -> {
                mediaUrlFromContainer(parser)?.let(state::setEpisodeMediaUrl)
            }

            state.inEpisode && tag == "link" -> {
                mediaUrlFromAtomLink(parser)?.let(state::setEpisodeMediaUrl)
            }
        }
    }

    private fun consumeText(
        parser: XmlPullParser,
        state: FeedParseState,
    ) {
        val value = parser.text?.trim().orEmpty()
        if (value.isBlank()) {
            return
        }

        if (state.inEpisode) {
            state.appendEpisodeText(value)
        } else {
            state.appendFeedText(value)
        }
    }

    private fun consumeEndTag(
        parser: XmlPullParser,
        state: FeedParseState,
    ) {
        if (localName(parser.name).isEpisodeContainer()) {
            state.finishEpisode()
        }
        state.currentTag = ""
    }

    private fun mediaUrlFromContainer(parser: XmlPullParser): String? {
        val candidate = attribute(parser, "url").orEmpty().trim()
        val type = attribute(parser, "type").orEmpty().lowercase(Locale.US)

        return candidate.takeIf {
            it.isNotBlank() && type.isSupportedAudioType()
        }
    }

    private fun mediaUrlFromAtomLink(parser: XmlPullParser): String? {
        val rel = attribute(parser, "rel").orEmpty().lowercase(Locale.US)
        val href = attribute(parser, "href").orEmpty().trim()
        val type = attribute(parser, "type").orEmpty().lowercase(Locale.US)

        return href.takeIf {
            it.isNotBlank() &&
                rel == "enclosure" &&
                type.isSupportedAudioType()
        }
    }

    private fun String.isEpisodeContainer(): Boolean = this == "item" || this == "entry"

    private fun String.isMediaContainer(): Boolean = this == "enclosure" || this == "content"

    private fun String.isSupportedAudioType(): Boolean = isBlank() || startsWith(AUDIO_MIME_PREFIX)

    private fun attribute(
        parser: XmlPullParser,
        name: String,
    ): String? =
        (0 until parser.attributeCount)
            .firstOrNull { index ->
                localName(parser.getAttributeName(index)) == name
            }?.let { index ->
                parser.getAttributeValue(index)
            }

    private fun localName(value: String?): String =
        value
            .orEmpty()
            .substringAfterLast(':')
            .lowercase(Locale.US)

    private fun stableId(value: String): String =
        UUID
            .nameUUIDFromBytes(
                value.toByteArray(StandardCharsets.UTF_8),
            ).toString()

    private fun cleanText(value: String): String =
        Html
            .fromHtml(
                value,
                Html.FROM_HTML_MODE_LEGACY,
            ).toString()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .take(MAX_TEXT_LENGTH)

    private fun appendText(
        existing: String,
        value: String,
    ): String =
        if (existing.isBlank()) {
            value
        } else {
            "$existing $value"
        }

    private fun parsePublishedAt(value: String): Long {
        val candidate = value.trim()
        val parsed =
            candidate
                .takeIf(String::isNotBlank)
                ?.let { timestamp ->
                    DATE_PATTERNS.firstNotNullOfOrNull { pattern ->
                        parseDate(
                            value = timestamp,
                            pattern = pattern,
                        )
                    }
                }

        return parsed ?: 0L
    }

    private fun parseDate(
        value: String,
        pattern: String,
    ): Long? =
        runCatching {
            SimpleDateFormat(
                pattern,
                Locale.US,
            ).apply {
                isLenient = true
                timeZone = UTC
            }.parse(value)
                ?.time
        }.getOrNull()

    private fun parseDurationMs(value: String): Long {
        val candidate = value.trim()
        val seconds =
            candidate
                .takeIf(String::isNotBlank)
                ?.toLongOrNull()
                ?: parseClockDurationSeconds(candidate)

        return seconds
            .coerceAtLeast(0L)
            .times(MILLIS_PER_SECOND)
    }

    private fun parseClockDurationSeconds(value: String): Long {
        val parts =
            value
                .split(':')
                .mapNotNull(String::toLongOrNull)

        return when (parts.size) {
            CLOCK_HOURS_PARTS -> {
                parts[0] * SECONDS_PER_HOUR +
                    parts[1] * SECONDS_PER_MINUTE +
                    parts[2]
            }

            CLOCK_MINUTES_PARTS -> {
                parts[0] * SECONDS_PER_MINUTE +
                    parts[1]
            }

            CLOCK_SECONDS_PARTS -> {
                parts[0]
            }

            else -> {
                0L
            }
        }
    }

    private data class FeedParseState(
        val podcastId: String,
        var feedTitle: String = "",
        var feedAuthor: String = "",
        var feedDescription: String = "",
        var inEpisode: Boolean = false,
        var currentTag: String = "",
        var currentEpisode: EpisodeBuilder? = null,
        val episodes: MutableList<PodcastEpisode> = mutableListOf(),
    ) {
        fun beginEpisode() {
            inEpisode = true
            currentEpisode = EpisodeBuilder()
        }

        fun setEpisodeMediaUrl(value: String) {
            currentEpisode?.mediaUrl = value
        }

        fun appendEpisodeText(value: String) {
            val episode = currentEpisode ?: return

            when (currentTag) {
                "title" -> {
                    episode.title = appendText(episode.title, value)
                }

                "guid",
                "id",
                -> {
                    episode.guid = appendText(episode.guid, value)
                }

                "pubdate",
                "published",
                "updated",
                -> {
                    episode.published = appendText(episode.published, value)
                }

                "description",
                "summary",
                "encoded",
                -> {
                    episode.description = appendText(episode.description, value)
                }

                "duration" -> {
                    episode.duration = appendText(episode.duration, value)
                }
            }
        }

        fun appendFeedText(value: String) {
            when (currentTag) {
                "title" -> {
                    if (feedTitle.isBlank()) {
                        feedTitle = value
                    }
                }

                "author",
                "creator",
                "name",
                -> {
                    if (feedAuthor.isBlank()) {
                        feedAuthor = value
                    }
                }

                "description",
                "subtitle",
                -> {
                    if (feedDescription.isBlank()) {
                        feedDescription = value
                    }
                }
            }
        }

        fun finishEpisode() {
            currentEpisode
                ?.toEpisode(
                    podcastId = podcastId,
                    podcastTitle =
                        cleanText(feedTitle)
                            .ifBlank { "Podcast" },
                )?.let(episodes::add)

            currentEpisode = null
            inEpisode = false
        }

        fun toSubscription(feedUrl: String): PodcastSubscription {
            val normalizedTitle =
                cleanText(feedTitle)
                    .ifBlank {
                        URL(feedUrl)
                            .host
                            .removePrefix("www.")
                            .ifBlank { "Podcast" }
                    }

            val normalizedEpisodes =
                episodes
                    .map { episode ->
                        episode.copy(
                            podcastTitle = normalizedTitle,
                        )
                    }.distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<PodcastEpisode> { it.publishedAt }
                            .thenBy { it.title.lowercase(Locale.getDefault()) },
                    ).take(MAX_EPISODES_PER_FEED)

            require(normalizedEpisodes.isNotEmpty()) {
                "No playable audio episodes were found in this feed."
            }

            return PodcastSubscription(
                id = podcastId,
                feedUrl = feedUrl,
                title = normalizedTitle,
                author = cleanText(feedAuthor),
                description = cleanText(feedDescription),
                episodes = normalizedEpisodes,
                refreshedAt = System.currentTimeMillis(),
            )
        }
    }

    private data class EpisodeBuilder(
        var title: String = "",
        var guid: String = "",
        var mediaUrl: String = "",
        var description: String = "",
        var published: String = "",
        var duration: String = "",
    ) {
        fun toEpisode(
            podcastId: String,
            podcastTitle: String,
        ): PodcastEpisode? {
            val playableUrl =
                mediaUrl
                    .trim()
                    .takeIf(PodcastUrlPolicy::isHttpsUrl)
                    ?: return null

            val normalizedTitle =
                cleanText(title)
                    .ifBlank { "Untitled episode" }
            val publishedAt = parsePublishedAt(published)
            val stableEpisodeKey =
                guid
                    .trim()
                    .ifBlank { playableUrl }
                    .ifBlank { "$normalizedTitle:$publishedAt" }

            return PodcastEpisode(
                id = stableId("$podcastId:$stableEpisodeKey"),
                podcastId = podcastId,
                podcastTitle = podcastTitle,
                title = normalizedTitle,
                mediaUrl = playableUrl,
                description = cleanText(description),
                publishedAt = publishedAt,
                durationMs = parseDurationMs(duration),
            )
        }
    }

    private val UTC = TimeZone.getTimeZone("UTC")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DATE_PATTERNS =
        listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd",
        )

    private const val DOCTYPE_TOKEN = "<!DOCTYPE"
    private const val AUDIO_MIME_PREFIX = "audio/"
    private const val PROLOG_SCAN_BYTES = 4_096
    private const val MAX_TEXT_LENGTH = 4_000
    private const val MAX_EPISODES_PER_FEED = 200
    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val CLOCK_HOURS_PARTS = 3
    private const val CLOCK_MINUTES_PARTS = 2
    private const val CLOCK_SECONDS_PARTS = 1
}
