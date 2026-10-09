package org.sableos.tools.core

/** Where a tool may appear. */
enum class Visibility {
    /** Normal Utilities/Diagnostics list. */
    VISIBLE,

    /** Only when developer/service mode is on. */
    DEVELOPER_ONLY,

    /** Not listed (an "Unavailable" row may still appear in the developer capability inventory). */
    HIDDEN
}

/** The capability badges DESIGN-KF-C allows. */
enum class Badge(val label: String) {
    AVAILABLE("Available"),
    NEEDS_PERMISSION("Needs permission"),
    NEEDS_CALIBRATION("Needs calibration"),
    DEVELOPER_ONLY("Developer only"),
    UNAVAILABLE("Unavailable on this device")
}

data class Resolution(
    val capability: Capability,
    val declared: Declared,
    val runtime: Probe,
    val visibility: Visibility,
    val reason: String
) {
    /** A declared-supported capability the device does not actually report: shown in diagnostics as a mismatch. */
    val mismatch: Boolean get() = declared == Declared.SUPPORTED && runtime == Probe.ABSENT
}

/**
 * DESIGN-KF-C capability gating:
 * ```
 * CAPABILITY_UNKNOWN_DEFAULT=HIDDEN
 * CAPABILITY_UNSUPPORTED=HIDDEN
 * CAPABILITY_DIAGNOSTIC_ONLY=DEVELOPER_MODE_ONLY
 * CAPABILITY_SUPPORTED=VISIBLE
 * ```
 * plus: the runtime probe can only take visibility away, never grant it. A declared-supported capability whose
 * hardware probes ABSENT is hidden (the profile is stale), and an UNKNOWN capability never becomes visible because
 * the hardware happens to report itself.
 */
object CapabilityGate {
    fun resolve(
        profile: ToolsDeviceProfile,
        probes: Map<Hardware, Probe>,
        c: Capability
    ): Resolution {
        val d = profile.declaration(c)
        val rt = c.runtime(probes)
        val (vis, why) = when {
            d.state == Declared.UNSUPPORTED ->
                Visibility.HIDDEN to
                    "profile: unsupported (${d.evidence})"

            rt == Probe.ABSENT -> Visibility.HIDDEN to "hardware not reported by this device"

            d.state == Declared.UNKNOWN -> Visibility.HIDDEN to "profile: unproven (${d.evidence})"

            d.state == Declared.DIAGNOSTIC_ONLY ->
                Visibility.DEVELOPER_ONLY to
                    "profile: diagnostic only (${d.evidence})"

            else -> Visibility.VISIBLE to "profile: supported (${d.evidence})"
        }
        return Resolution(c, d.state, rt, vis, why)
    }

    fun resolveAll(
        profile: ToolsDeviceProfile,
        probes: Map<Hardware, Probe>
    ): Map<Capability, Resolution> =
        Capability.entries.associateWith { resolve(profile, probes, it) }

    /**
     * Whether a tool is listed with the current developer-mode setting. HIDDEN tools (unknown, unsupported, absent)
     * appear only as rows of the developer capability inventory, never as a tool entry.
     */
    fun listed(r: Resolution, developerMode: Boolean): Boolean = when (r.visibility) {
        Visibility.VISIBLE -> true
        Visibility.DEVELOPER_ONLY -> developerMode
        Visibility.HIDDEN -> false
    }

    /**
     * The single badge for a listed tool. Permission comes before calibration because the tool cannot read at all
     * without it.
     */
    fun badge(r: Resolution, missingPermissions: Boolean, calibrated: Boolean): Badge = when {
        r.visibility == Visibility.HIDDEN -> Badge.UNAVAILABLE
        r.visibility == Visibility.DEVELOPER_ONLY -> Badge.DEVELOPER_ONLY
        missingPermissions -> Badge.NEEDS_PERMISSION
        r.capability.calibratable && !calibrated -> Badge.NEEDS_CALIBRATION
        else -> Badge.AVAILABLE
    }
}

/**
 * Developer/service mode needs BOTH Android developer options and the in-app opt-in, so neither a stray system
 * setting nor an app preference alone reveals the deeper tier.
 */
object DeveloperMode {
    fun enabled(systemDeveloperOptions: Boolean, userOptIn: Boolean): Boolean =
        systemDeveloperOptions && userOptIn
}
