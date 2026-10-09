package org.sableos.reader.engine.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Per-title playback preferences, stored in the per-title settings table. */
data class AudioPlaybackSettings(
    val speed: Float = DEFAULT_SPEED,
    val skipBackMs: Long = DEFAULT_SKIP_BACK_MS,
    val skipForwardMs: Long = DEFAULT_SKIP_FORWARD_MS,
) {
    companion object {
        const val DEFAULT_SPEED: Float = 1.0f
        const val DEFAULT_SKIP_BACK_MS: Long = 15_000L
        const val DEFAULT_SKIP_FORWARD_MS: Long = 30_000L

        /** Speeds offered in the UI; a stored value outside this list is snapped to the nearest one. */
        val SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val SKIP_BACK_CHOICES_MS: List<Long> = listOf(5_000L, 10_000L, 15_000L, 30_000L)
        val SKIP_FORWARD_CHOICES_MS: List<Long> = listOf(10_000L, 15_000L, 30_000L, 60_000L)

        fun snapSpeed(value: Float): Float = SPEEDS.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT_SPEED
    }
}

@Serializable
private data class StoredAudioSettings(
    val v: Int = 1,
    val speed: Float? = null,
    val back: Long? = null,
    val fwd: Long? = null,
)

/** Stable JSON for [AudioPlaybackSettings]. Never throws on bad input; unknown values snap to offered choices. */
object AudioSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: AudioPlaybackSettings): String = json.encodeToString(
        StoredAudioSettings.serializer(),
        StoredAudioSettings(speed = settings.speed, back = settings.skipBackMs, fwd = settings.skipForwardMs),
    )

    fun decode(text: String?): AudioPlaybackSettings? {
        val stored = text?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(StoredAudioSettings.serializer(), it) }.getOrNull() }
        return stored?.takeIf { it.speed != null || it.back != null || it.fwd != null }?.let {
            AudioPlaybackSettings(
                speed = it.speed?.takeIf(Float::isFinite)?.let(AudioPlaybackSettings::snapSpeed)
                    ?: AudioPlaybackSettings.DEFAULT_SPEED,
                skipBackMs = nearest(
                    it.back,
                    AudioPlaybackSettings.SKIP_BACK_CHOICES_MS,
                    AudioPlaybackSettings.DEFAULT_SKIP_BACK_MS,
                ),
                skipForwardMs = nearest(
                    it.fwd,
                    AudioPlaybackSettings.SKIP_FORWARD_CHOICES_MS,
                    AudioPlaybackSettings.DEFAULT_SKIP_FORWARD_MS,
                ),
            )
        }
    }

    private fun nearest(value: Long?, choices: List<Long>, default: Long): Long =
        value?.let { v -> choices.minByOrNull { kotlin.math.abs(it - v) } } ?: default
}
