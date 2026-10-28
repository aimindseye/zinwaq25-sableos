package org.sableos.titan2.setup.core

import org.sableos.titan2.setup.core.SetupText.t

object SetupModel {
    val TITLE get() = t("setup_title")
    val SELF_DESCRIPTION get() = t("setup_self_description")

    /**
     * Setup observes and opens screens; the home app (IR-003), the default keyboard (IR-005) and the
     * profile identity (IR-001) are only ever changed on Android's own screens.
     */
    val FOOTNOTE get() = t("setup_footnote")
    const val MAX_PROFILE_ID = 32

    /** Rows that carry a conclusion; informational rows are not counted. */
    private fun counted(items: List<Item>) = items.filter { it.state != Readiness.Informational }

    /**
     * "1 of 4 confirmed" plus a clause for every non-empty bucket, so unavailable and unknown rows are
     * visible instead of silently disappearing from the count.
     */
    fun summary(items: List<Item>): String {
        val rows = counted(items)
        val confirmed = rows.count { it.state == Readiness.Satisfied }
        var line = t("summary_confirmed", confirmed, rows.size)
        listOf(
            Readiness.ActionRequired to "summary_action_required",
            Readiness.Unavailable to "summary_unavailable",
            Readiness.Unknown to "summary_unknown"
        ).forEach { (state, key) ->
            val n = rows.count { it.state == state }
            if (n > 0) line = t("summary_separator", line, t(key, n))
        }
        return line
    }

    /** True only when nothing needs action and nothing is unavailable or unknown. */
    fun isReady(items: List<Item>) = items.none {
        it.state == Readiness.ActionRequired || it.state == Readiness.Unavailable ||
            it.state == Readiness.Unknown
    }

    /**
     * Profile line from the provisional `ro.sable.profile.id` read. Display text only: nothing else in the
     * helper branches on it, and a blank or unreadable value is an explicit unknown (IR-001 stays canonical).
     */
    fun profileLine(id: String?): String {
        val clean = id?.trim().orEmpty()
        val safe = clean.take(MAX_PROFILE_ID).filter { it.isLetterOrDigit() || it in "-_." }
        return when {
            clean.isEmpty() || safe.isEmpty() -> t("profile_unknown")
            clean == "titan2" -> "Unihertz Titan 2"
            clean == "titan2-elite" -> "Unihertz Titan 2 Elite"
            clean == "zinwa-q25" -> "Zinwa Q25"
            else -> t("profile_unrecognized", safe)
        }
    }
}
