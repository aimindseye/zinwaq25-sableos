package org.sableos.titan2.setup.core

object SetupModel {
    const val TITLE = "Sable setup"
    const val SELF_DESCRIPTION =
        "Post-provisioning readiness helper. Not the Android Setup Wizard; it changes no settings."
    const val FOOTNOTE =
        "Observation only. HOME (IR-003), keyboard default (IR-005) and " +
            "profile identity (IR-001) " +
            "are owned by the canonical lane."
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
        val parts = mutableListOf("$confirmed of ${rows.size} confirmed")
        listOf(
            Readiness.ActionRequired to "action required",
            Readiness.Unavailable to "unavailable",
            Readiness.Unknown to "unknown"
        ).forEach { (state, word) ->
            val n = rows.count { it.state == state }
            if (n > 0) parts += "$n $word"
        }
        return parts.joinToString(" · ")
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
            clean.isEmpty() || safe.isEmpty() -> "Profile unknown (not reported by this build)"
            clean == "titan2" -> "Unihertz Titan 2"
            clean == "titan2-elite" -> "Unihertz Titan 2 Elite"
            else -> "Unrecognized profile id: $safe"
        }
    }
}
