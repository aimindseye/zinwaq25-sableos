package org.sableos.titan2.setup.core

import org.sableos.titan2.setup.core.SetupText.t

/** How an attempt to open a destination ended. The exception text is never shown to the user. */
enum class LaunchOutcome { Launched, TargetMissing, Denied, Failed }

object LaunchReport {
    const val MAX_LABEL = 40
    const val MAX_MESSAGE = 120

    /** One short, bounded user-visible line for a failed launch; null when the launch worked. */
    fun message(label: String, outcome: LaunchOutcome): String? {
        val name = label.trim().take(MAX_LABEL).ifBlank { t("launch_that_screen") }
        val text =
            when (outcome) {
                LaunchOutcome.Launched -> return null

                LaunchOutcome.TargetMissing ->
                    t("launch_missing", name)

                LaunchOutcome.Denied -> t("launch_denied", name)

                LaunchOutcome.Failed -> t("launch_failed", name)
            }
        return text.take(MAX_MESSAGE)
    }
}
