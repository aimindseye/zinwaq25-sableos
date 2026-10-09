package org.sableos.reader.comic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The five ways a comic can be read. [stableValue] is persisted and never changes. */
enum class ComicReadingMode(val stableValue: String) {
    PAGED_LTR("PAGED_LTR"),
    PAGED_RTL("PAGED_RTL"),
    VERTICAL_PAGER("VERTICAL_PAGER"),
    CONTINUOUS_VERTICAL("CONTINUOUS_VERTICAL"),
    WEBTOON("WEBTOON"),
    ;

    val isPaged: Boolean get() = this == PAGED_LTR || this == PAGED_RTL

    val isRightToLeft: Boolean get() = this == PAGED_RTL

    /** Continuous modes scroll a strip of pages instead of snapping to one page. */
    val isContinuous: Boolean get() = this == CONTINUOUS_VERTICAL || this == WEBTOON

    companion object {
        fun fromStableValue(value: String?): ComicReadingMode? = entries.firstOrNull { it.stableValue == value }
    }
}

enum class ComicFit(val stableValue: String) {
    FIT_PAGE("FIT_PAGE"),
    FIT_WIDTH("FIT_WIDTH"),
    ;

    companion object {
        fun fromStableValue(value: String?): ComicFit? = entries.firstOrNull { it.stableValue == value }
    }
}

/** Per-title viewer choices. Webtoon strips always fit the width; the stored fit only matters for paged modes. */
data class ComicViewSettings(
    val mode: ComicReadingMode = ComicReadingMode.PAGED_LTR,
    val fit: ComicFit = ComicFit.FIT_PAGE,
) {
    val effectiveFit: ComicFit
        get() = if (mode.isContinuous) ComicFit.FIT_WIDTH else fit

    /** Gap between pages in continuous modes: a webtoon is one seamless strip. */
    val pageGapDp: Int
        get() = if (mode == ComicReadingMode.CONTINUOUS_VERTICAL) PAGE_GAP_DP else 0

    companion object {
        const val PAGE_GAP_DP: Int = 8
    }
}

@Serializable
private data class StoredComicSettings(val v: Int = 1, val mode: String? = null, val fit: String? = null)

/** Stable JSON for [ComicViewSettings] as kept in the per-title settings table. Never throws on bad input. */
object ComicViewSettingsCodec {
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: ComicViewSettings): String = json.encodeToString(
        StoredComicSettings.serializer(),
        StoredComicSettings(VERSION, settings.mode.stableValue, settings.fit.stableValue),
    )

    /** Returns null for blank, malformed or unrecognised content so the caller falls back to the title default. */
    fun decode(text: String?): ComicViewSettings? {
        val stored = text?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(StoredComicSettings.serializer(), it) }.getOrNull() }
        val mode = ComicReadingMode.fromStableValue(stored?.mode)
        return mode?.let { ComicViewSettings(it, ComicFit.fromStableValue(stored?.fit) ?: ComicFit.FIT_PAGE) }
    }
}
