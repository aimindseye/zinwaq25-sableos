package org.sableos.hub.policy

/**
 * Who drives an attention output once Android has decided a notification alerts.
 *
 * [AndroidChannel] and [AndroidPlatform] outputs are Android delivery: Sable shows where they are
 * configured and links there, never a duplicate toggle. Only [Sable] outputs (no Android channel
 * field exists for them) have a Sable Attention selection.
 */
enum class OutputOwner {
    AndroidChannel,
    AndroidPlatform,
    Sable,
}

enum class AttentionOutput(
    val token: String,
    val owner: OutputOwner,
    val concept: NotificationConcept,
) {
    Audio("audio", OutputOwner.AndroidChannel, NotificationConcept.Sound),
    Haptic("haptic", OutputOwner.AndroidChannel, NotificationConcept.Vibration),
    StatusLed("status_led", OutputOwner.AndroidChannel, NotificationConcept.StatusLight),
    KeyboardBacklight("keyboard_backlight", OutputOwner.Sable, NotificationConcept.AttentionKeyboardBacklight),
    SecondaryDisplay("secondary_display", OutputOwner.Sable, NotificationConcept.AttentionSecondaryDisplay),
    AlwaysOnDisplay("aod", OutputOwner.AndroidPlatform, NotificationConcept.AlwaysOnDisplay),
    ;

    companion object {
        fun fromToken(token: String): AttentionOutput? = entries.firstOrNull { it.token == token }
    }
}

/** Evidence level of one output on one device profile. Only [Validated] is shown as working. */
enum class CapabilityEvidence {
    Absent,
    Candidate,
    Validated,
}

/** Facts the Android layer reads from public platform APIs (not from a device model name). */
data class PlatformAttentionFacts(
    val hasAudioOutput: Boolean,
    val hasVibrator: Boolean,
)

/**
 * The attention capabilities of the running device profile.
 *
 * Sable-controlled outputs (status LED, keyboard backlight, secondary display, AOD) come only from
 * the device profile's own declaration, the read-only property
 * `ro.sable.attention.outputs=<profile-id>:<output>=<evidence>,...`. A declaration whose profile id
 * differs from `ro.sable.profile.id` is ignored, so device families never inherit PASS from each
 * other, and a missing or malformed declaration fails closed (everything [CapabilityEvidence.Absent]).
 * Audio and haptic follow Android's own hardware facts because Android delivers them.
 */
class AttentionDeviceProfile private constructor(
    val profileId: String,
    private val evidence: Map<AttentionOutput, CapabilityEvidence>,
) {
    fun evidenceFor(output: AttentionOutput): CapabilityEvidence = evidence[output] ?: CapabilityEvidence.Absent

    /** Supported means validated on this exact profile. Candidates stay hidden. */
    fun isSupported(output: AttentionOutput): Boolean = evidenceFor(output) == CapabilityEvidence.Validated

    /** Outputs that may appear in UI at all: unsupported outputs are hidden, not shown disabled. */
    fun visibleOutputs(): List<AttentionOutput> = AttentionOutput.entries.filter(::isSupported)

    /** Outputs that have a Sable Attention selection (validated and Sable-driven). */
    fun selectableOutputs(): List<AttentionOutput> = visibleOutputs().filter { it.owner == OutputOwner.Sable }

    override fun toString(): String = "AttentionDeviceProfile($profileId, $evidence)"

    companion object {
        const val PROPERTY_PROFILE_ID = "ro.sable.profile.id"
        const val PROPERTY_ATTENTION_OUTPUTS = "ro.sable.attention.outputs"
        private const val MAX_DECLARATION_LENGTH = 512
        private val PROFILE_ID = Regex("[A-Za-z0-9._-]{1,32}")
        private val PLATFORM_OUTPUTS = setOf(AttentionOutput.Audio, AttentionOutput.Haptic)

        fun parse(
            profileId: String?,
            declaration: String?,
            platform: PlatformAttentionFacts,
        ): AttentionDeviceProfile {
            val id = profileId?.trim().orEmpty()
            val evidence = mutableMapOf<AttentionOutput, CapabilityEvidence>()
            if (platform.hasAudioOutput) evidence[AttentionOutput.Audio] = CapabilityEvidence.Validated
            if (platform.hasVibrator) evidence[AttentionOutput.Haptic] = CapabilityEvidence.Validated
            if (PROFILE_ID.matches(id)) {
                evidence.putAll(declaredEvidence(id, declaration))
            }
            return AttentionDeviceProfile(id.ifEmpty { "unknown" }, evidence.toMap())
        }

        private fun declaredEvidence(
            profileId: String,
            declaration: String?,
        ): Map<AttentionOutput, CapabilityEvidence> {
            val text = declaration?.trim().orEmpty()
            val separator = text.indexOf(':')
            val ownDeclaration =
                text.length <= MAX_DECLARATION_LENGTH &&
                    separator > 0 &&
                    text.substring(0, separator) == profileId
            val entries =
                if (ownDeclaration) {
                    text.substring(separator + 1).split(',').map(String::trim).filter(String::isNotEmpty)
                } else {
                    emptyList()
                }
            val parsed = entries.map(::parseEntry)
            // A malformed entry fails the whole declaration closed. Audio and haptic are Android
            // facts; a declaration can neither claim nor remove them.
            return if (parsed.any { it == null }) {
                emptyMap()
            } else {
                parsed
                    .filterNotNull()
                    .filterNot { (output, _) -> output in PLATFORM_OUTPUTS }
                    .toMap()
            }
        }

        private fun parseEntry(entry: String): Pair<AttentionOutput, CapabilityEvidence>? {
            val parts = entry.split('=')
            val output = parts.firstOrNull()?.let(AttentionOutput::fromToken)
            val level = parts.getOrNull(1)?.let(::evidenceFromToken)
            return if (parts.size == 2 && output != null && level != null) output to level else null
        }

        private fun evidenceFromToken(token: String): CapabilityEvidence? =
            when (token) {
                "absent" -> CapabilityEvidence.Absent
                "candidate" -> CapabilityEvidence.Candidate
                "validated" -> CapabilityEvidence.Validated
                else -> null
            }
    }
}

/** DESIGN-KF-A "Default attention posture". */
enum class AttentionDefault {
    /** Off until the user turns it on. */
    Off,

    /** On only where the profile validated the output; content is still privacy-gated. */
    ProfileAndPrivacyGated,

    /** Whatever Android/the platform does; Sable has no toggle. */
    PlatformDefault,
}

object AttentionDefaults {
    fun defaultFor(output: AttentionOutput): AttentionDefault =
        when (output) {
            AttentionOutput.KeyboardBacklight -> AttentionDefault.Off
            AttentionOutput.SecondaryDisplay -> AttentionDefault.ProfileAndPrivacyGated
            AttentionOutput.Audio,
            AttentionOutput.Haptic,
            AttentionOutput.StatusLed,
            AttentionOutput.AlwaysOnDisplay,
            -> AttentionDefault.PlatformDefault
        }

    /** Default Sable Attention selection for an app the user has not configured. */
    fun defaultSelection(profile: AttentionDeviceProfile): Set<AttentionOutput> =
        profile
            .selectableOutputs()
            .filter { defaultFor(it) == AttentionDefault.ProfileAndPrivacyGated }
            .toSet()
}
