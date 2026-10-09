package org.sableos.start.privacy

/**
 * Compact All Apps privacy row and its expanded detail (DESIGN-KF-D part 1).
 *
 * ```text
 * MAX_INLINE_PERMISSION_LABELS=3   EXCESS_LABELS=+N   SECONDARY_LINE_MAX=1
 * RAW_PERMISSION_NAMES=NO          PACKAGE_NAME=NO
 * WARNING_ICON_ONLY_WITH_ACCESSIBLE_LABEL=YES
 * ```
 *
 * Labels are whole words; the row drops whole labels into "+N" when the width
 * is smaller, so a label is never clipped mid-word.
 */
enum class PrivacyRowState {
    /** At least one effective group is listed. */
    Labels,

    /** Every requested sensitive group is known and none is effectively allowed. */
    NoneSensitive,

    /** Nothing effective, but at least one requested group could not be read. */
    NotConfirmed,

    /** The app's permission state could not be read at all. */
    Unavailable,
}

data class PrivacyRow(
    val state: PrivacyRowState,
    val labels: List<String> = emptyList(),
    val overflowCount: Int = 0,
    /** Special-access labels shown as a warning badge (with this accessible text) instead of inline. */
    val badgeLabels: List<String> = emptyList(),
    /** True when any special access is effective, inline or badged. */
    val warning: Boolean = false,
) {
    val text: String
        get() =
            when (state) {
                PrivacyRowState.Labels ->
                    (listOf(PrivacySummaryPolicy.PREFIX) + labels + overflowSuffix())
                        .joinToString(PrivacySummaryPolicy.SEPARATOR)
                PrivacyRowState.NoneSensitive -> PrivacySummaryPolicy.TEXT_NONE_SENSITIVE
                PrivacyRowState.NotConfirmed -> PrivacySummaryPolicy.TEXT_NOT_CONFIRMED
                PrivacyRowState.Unavailable -> PrivacySummaryPolicy.TEXT_UNAVAILABLE
            }

    /** Spoken text: names every label, including the ones folded into +N and the badge. */
    fun accessibleText(privacy: AppPrivacy?): String {
        if (state != PrivacyRowState.Labels || privacy == null) return text
        val everyday = PrivacySummaryPolicy.everydayLabels(privacy)
        val special = PrivacySummaryPolicy.specialLabels(privacy)
        val parts = mutableListOf<String>()
        if (everyday.isNotEmpty()) parts += "Permissions: " + everyday.joinToString(", ")
        if (special.isNotEmpty()) parts += "Special access: " + special.joinToString(", ")
        return parts.joinToString(". ")
    }

    private fun overflowSuffix(): List<String> = if (overflowCount > 0) listOf("+$overflowCount") else emptyList()
}

/** One line of the expanded (Space) privacy detail. */
data class PrivacyDetailLine(
    val label: String,
    val special: Boolean,
)

object PrivacySummaryPolicy {
    const val PREFIX = "permissions"
    const val SEPARATOR = " · "
    const val MAX_INLINE_PERMISSION_LABELS = 3
    const val TEXT_NONE_SENSITIVE = "permissions · none sensitive"
    const val TEXT_NOT_CONFIRMED = "permissions · see app info"
    const val TEXT_UNAVAILABLE = "permissions · unavailable"
    const val DETAIL_FOOTER = "App info has the full permission detail."

    fun everydayLabels(privacy: AppPrivacy): List<String> =
        privacy.effective.filterNot { it.special }.map(privacy::labelFor)

    fun specialLabels(privacy: AppPrivacy): List<String> =
        privacy.effective.filter { it.special }.map(privacy::labelFor)

    /**
     * Builds the compact row.
     *
     * @param maxInline never more than [MAX_INLINE_PERMISSION_LABELS].
     * @param fits optional width check for a candidate row text; whole labels
     *   move into "+N" until it fits (at least one label stays; the view then
     *   ellipsizes, which the design allows).
     */
    fun compact(
        privacy: AppPrivacy?,
        maxInline: Int = MAX_INLINE_PERMISSION_LABELS,
        fits: ((String) -> Boolean)? = null,
    ): PrivacyRow {
        if (privacy == null) return PrivacyRow(PrivacyRowState.Unavailable)
        val everyday = everydayLabels(privacy)
        val special = specialLabels(privacy)
        if (everyday.isEmpty() && special.isEmpty()) {
            return PrivacyRow(
                if (privacy.hasUnknown) PrivacyRowState.NotConfirmed else PrivacyRowState.NoneSensitive,
            )
        }
        // Special access joins the row only when there is nothing everyday to show.
        val inline = everyday.ifEmpty { special }
        val badge = if (everyday.isEmpty()) emptyList() else special
        val limit = maxInline.coerceIn(1, MAX_INLINE_PERMISSION_LABELS)
        var shown = minOf(limit, inline.size)
        fun candidate(count: Int) =
            PrivacyRow(
                state = PrivacyRowState.Labels,
                labels = inline.take(count),
                overflowCount = inline.size - count,
                badgeLabels = badge,
                warning = special.isNotEmpty(),
            )
        if (fits != null) {
            while (shown > 1 && !fits(candidate(shown).text)) shown--
        }
        return candidate(shown)
    }

    /** Expanded detail for Space / Peek: everything effective, everyday first. */
    fun detail(privacy: AppPrivacy?): List<PrivacyDetailLine> {
        if (privacy == null) return emptyList()
        return privacy.effective.map { PrivacyDetailLine(privacy.labelFor(it), it.special) }
    }

    /** Words All Apps search may match ("camera", "mic", "location", ...). */
    fun searchTerms(privacy: AppPrivacy?): Set<String> {
        if (privacy == null) return emptySet()
        return privacy.effective
            .flatMap { group -> listOf(group.label.lowercase()) + aliases(group) }
            .toSet()
    }

    fun matchesPrivacyQuery(
        privacy: AppPrivacy?,
        query: String,
    ): Boolean {
        val q = query.trim().lowercase()
        if (q.length < MIN_PRIVACY_QUERY) return false
        return searchTerms(privacy).any { term -> term.startsWith(q) || term.split(' ').any { it.startsWith(q) } }
    }

    private const val MIN_PRIVACY_QUERY = 3

    private fun aliases(group: PrivacyGroup): List<String> =
        when (group) {
            PrivacyGroup.Microphone -> listOf("mic")
            PrivacyGroup.Messages -> listOf("sms", "mms")
            PrivacyGroup.Phone -> listOf("calls", "call log")
            PrivacyGroup.Photos -> listOf("media", "images", "video")
            PrivacyGroup.Audio -> listOf("media", "music")
            PrivacyGroup.Nearby -> listOf("bluetooth", "nearby devices")
            PrivacyGroup.Overlay -> listOf("display over other apps")
            PrivacyGroup.InstallApps -> listOf("install unknown apps")
            else -> emptyList()
        }
}
