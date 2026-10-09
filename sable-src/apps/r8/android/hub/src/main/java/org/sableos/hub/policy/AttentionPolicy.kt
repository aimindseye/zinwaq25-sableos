package org.sableos.hub.policy

/**
 * Android's effective decision for one posted notification, read from the listener Ranking.
 * Sable Attention maps this; it never changes it.
 */
data class AndroidAlertState(
    /** NotificationManager.IMPORTANCE_* of the effective channel/conversation. */
    val importance: Int,
    /** Ranking.matchesInterruptionFilter(): false when Do Not Disturb intercepts it. */
    val matchesInterruptionFilter: Boolean,
    /** Update to an existing notification with FLAG_ONLY_ALERT_ONCE: Android does not re-alert. */
    val alertOnlyOnceUpdate: Boolean = false,
) {
    companion object {
        const val IMPORTANCE_NONE = 0
        const val IMPORTANCE_MIN = 1
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
        const val IMPORTANCE_HIGH = 4
    }
}

/**
 * A finite light/glance pattern. Hardware attention may never flash, pulse or wake continuously
 * (DESIGN-KF-A "Default attention posture"), so construction rejects anything unbounded.
 */
data class BoundedPattern(
    val pulses: Int,
    val onMillis: Long,
    val offMillis: Long,
) {
    init {
        require(pulses in 1..MAX_PULSES)
        require(onMillis in 1..MAX_ON_MILLIS)
        require(offMillis in 0..MAX_ON_MILLIS)
        require(totalMillis() <= MAX_TOTAL_MILLIS)
    }

    fun totalMillis(): Long = pulses * onMillis + (pulses - 1) * offMillis

    companion object {
        const val MAX_PULSES = 3
        const val MAX_ON_MILLIS = 5_000L
        const val MAX_TOTAL_MILLIS = 6_000L
    }
}

sealed interface AttentionDecision {
    val output: AttentionOutput

    /** Android delivers this output (sound, vibration, lights, AOD) from its own policy. */
    data class DelegatedToAndroid(
        override val output: AttentionOutput,
    ) : AttentionDecision

    data class Suppressed(
        override val output: AttentionOutput,
        val reason: SuppressionReason,
    ) : AttentionDecision

    data class Emit(
        override val output: AttentionOutput,
        val content: AttentionContent,
        val pattern: BoundedPattern,
    ) : AttentionDecision
}

enum class SuppressionReason {
    NotSelected,
    QuietOrBlockedByAndroid,
    DoNotDisturb,
    AlertOnce,
    Privacy,
}

object AttentionPolicy {
    private val KEYBOARD_PATTERN = BoundedPattern(pulses = 3, onMillis = 300L, offMillis = 300L)
    private val GLANCE_PATTERN = BoundedPattern(pulses = 1, onMillis = 5_000L, offMillis = 0L)

    /**
     * Decides each output the profile supports. Unsupported outputs get no decision at all (they
     * are hidden). A Sable output emits only when the user selected it, Android itself would
     * alert (importance DEFAULT or higher), Android's interruption filter lets it through (so
     * Sable never bypasses DND; Android's own DND exceptions already show up as a match), and
     * privacy leaves something to show.
     */
    fun decide(
        profile: AttentionDeviceProfile,
        selection: Set<AttentionOutput>,
        android: AndroidAlertState,
        privacy: PrivacyContext,
    ): List<AttentionDecision> =
        profile.visibleOutputs().map { output ->
            if (output.owner != OutputOwner.Sable) {
                AttentionDecision.DelegatedToAndroid(output)
            } else {
                decideSable(output, selection, android, privacy)
            }
        }

    private fun decideSable(
        output: AttentionOutput,
        selection: Set<AttentionOutput>,
        android: AndroidAlertState,
        privacy: PrivacyContext,
    ): AttentionDecision {
        val content = capContent(output, PrivacyPosture.attentionContent(privacy))
        val reason =
            when {
                output !in selection -> SuppressionReason.NotSelected
                android.importance < AndroidAlertState.IMPORTANCE_DEFAULT -> SuppressionReason.QuietOrBlockedByAndroid
                !android.matchesInterruptionFilter -> SuppressionReason.DoNotDisturb
                android.alertOnlyOnceUpdate -> SuppressionReason.AlertOnce
                content == AttentionContent.None -> SuppressionReason.Privacy
                else -> null
            }
        return if (reason != null) {
            AttentionDecision.Suppressed(output, reason)
        } else {
            AttentionDecision.Emit(output, content, patternFor(output))
        }
    }

    /** A keyboard backlight can only signal that something arrived; it never carries content. */
    private fun capContent(
        output: AttentionOutput,
        content: AttentionContent,
    ): AttentionContent =
        if (output == AttentionOutput.KeyboardBacklight && content.ordinal < AttentionContent.Generic.ordinal) {
            AttentionContent.Generic
        } else {
            content
        }

    private fun patternFor(output: AttentionOutput): BoundedPattern =
        if (output == AttentionOutput.KeyboardBacklight) KEYBOARD_PATTERN else GLANCE_PATTERN
}
