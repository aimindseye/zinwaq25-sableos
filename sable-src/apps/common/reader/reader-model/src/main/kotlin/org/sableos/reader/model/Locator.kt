package org.sableos.reader.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A typed position inside a publication. Each format keeps its own lossless representation;
 * [fraction] is only the normalized value used for presentation and sorting.
 */
@Serializable
sealed interface Locator {
    /** Normalized 0.0..1.0 position used for progress bars and ordering. Never persisted alone. */
    val fraction: Double
}

/** Readium locator JSON (EPUB). [progression] is Readium's total progression across the book. */
@Serializable
@SerialName("epub")
data class EpubLocator(
    val locatorJson: String,
    val progression: Double = 0.0,
) : Locator {
    override val fraction: Double get() = progression.coerceIn(0.0, 1.0)
}

/** Zero-based [pageIndex] inside a document or comic of [pageCount] pages (PDF, comic). */
@Serializable
@SerialName("page")
data class PageLocator(
    val pageIndex: Int,
    val pageCount: Int,
) : Locator {
    override val fraction: Double
        get() = if (pageCount <= 0) 0.0 else ((pageIndex + 1).toDouble() / pageCount).coerceIn(0.0, 1.0)
}

/** Playback [positionMs] of [durationMs] within chapter [chapterIndex] (audiobook). */
@Serializable
@SerialName("time")
data class TimeLocator(
    val positionMs: Long,
    val durationMs: Long,
    val chapterIndex: Int = 0,
) : Locator {
    override val fraction: Double
        get() = if (durationMs <= 0L) 0.0 else (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
}

/** Stable JSON encoding for [Locator]; the `type` discriminator is part of the persisted format. */
object LocatorCodec {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    fun encode(locator: Locator): String = json.encodeToString(Locator.serializer(), locator)

    /** Returns null for blank, malformed or unknown-type input instead of throwing. */
    fun decode(text: String?): Locator? {
        if (text.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(Locator.serializer(), text) }.getOrNull()
    }
}
