package org.sableos.tools.core

/**
 * Per-device capability declarations, selected by `ro.sable.profile.id`. This table is the ONLY place device
 * differences live: the UI never branches on a device model. Profiles never inherit each other's evidence
 * (Titan 2, Titan 2 Elite, Q27 and Q25 are independent); anything not declared is [Declared.UNKNOWN].
 *
 * Moving an entry to [Declared.SUPPORTED] needs physical qualification evidence (DESIGN-KF-C TOOLS-I5), recorded in
 * [Declaration.evidence].
 */
data class ToolsDeviceProfile(
    val id: String,
    val displayName: String,
    private val declarations: Map<Capability, Declaration>
) {
    fun declaration(c: Capability): Declaration =
        declarations[c] ?: Declaration(Declared.UNKNOWN, "no evidence recorded for this profile")

    companion object {
        private const val TITAN2_TOOLBOX =
            "stock Toolbox grid and user guide (platform_sable TOOLBOX_HARDWARE_UTILITIES_UX)"
        private const val Q25_SPEC_ONLY =
            "public Q25 spec lists the hardware; Sable qualification NOT_RUN (device-profile/CAPABILITIES.md)"
        private const val Q25_ABSENT = "not in the Q25 hardware spec (docs/DEVICE_INFO.md)"
        private const val NO_PUBLIC_RX_API = "Android has no public IR receive API"

        private fun all(state: Declared, evidence: String, vararg c: Capability) =
            allOf(state, evidence, c.toList())

        private fun allOf(state: Declared, evidence: String, c: List<Capability>) =
            c.associateWith { Declaration(state, evidence) }

        private val SENSOR_TOOLS = listOf(
            Capability.COMPASS,
            Capability.BUBBLE_LEVEL,
            Capability.PLUMB_BOB,
            Capability.PROTRACTOR,
            Capability.PICTURE_HANGING,
            Capability.HEIGHT_ESTIMATE,
            Capability.PEDOMETER
        )
        private val CAMERA_MIC_GPS_TOOLS = listOf(
            Capability.FLASHLIGHT,
            Capability.MAGNIFIER,
            Capability.NOISE_METER,
            Capability.SPEEDOMETER
        )

        val Titan2 = ToolsDeviceProfile(
            "titan2",
            "Unihertz Titan 2",
            allOf(Declared.SUPPORTED, TITAN2_TOOLBOX, SENSOR_TOOLS) +
                allOf(Declared.SUPPORTED, TITAN2_TOOLBOX, CAMERA_MIC_GPS_TOOLS) +
                all(Declared.SUPPORTED, "$TITAN2_TOOLBOX; IR transmitter", Capability.IR_REMOTE) +
                all(Declared.UNKNOWN, NO_PUBLIC_RX_API, Capability.IR_LEARNING) +
                all(
                    Declared.DIAGNOSTIC_ONLY,
                    "TITAN2_FM_RADIO=PROVE_BEFORE_ENABLE",
                    Capability.FM_RADIO
                ) +
                all(
                    Declared.SUPPORTED,
                    "rear sub-screen (SUBSCREEN_SHORTCUT_KEYBOARD_UX)",
                    Capability.SUBSCREEN_COMPANION
                ) +
                all(
                    Declared.DIAGNOSTIC_ONLY,
                    "stock *#*#3377#*#* Factory Test (HARDWARE_DIAGNOSTICS_AND_DIALER_CODES)",
                    Capability.FACTORY_TEST_BRIDGE
                )
        )

        /** Independent validation required (TITAN2_ELITE_INDEPENDENT_VALIDATION): nothing is inherited from Titan 2. */
        val Titan2Elite = ToolsDeviceProfile("titan2-elite", "Unihertz Titan 2 Elite", emptyMap())

        /** Deferred until retail evidence (Q27_TOOLBOX=DEFER_UNTIL_RETAIL_EVIDENCE). */
        val ZinwaQ27 = ToolsDeviceProfile("zinwa-q27", "Zinwa Q27", emptyMap())

        /**
         * Zinwa Q25: the hardware exists on paper but nothing is qualified yet, so sensor/camera/mic/GPS tools are
         * developer-only until TOOLS-I5. No IR transmitter and no sub-screen. FM (MT6631) is status-only.
         */
        val ZinwaQ25 = ToolsDeviceProfile(
            "zinwa-q25",
            "Zinwa Q25",
            allOf(Declared.DIAGNOSTIC_ONLY, Q25_SPEC_ONLY, SENSOR_TOOLS) +
                allOf(Declared.DIAGNOSTIC_ONLY, Q25_SPEC_ONLY, CAMERA_MIC_GPS_TOOLS) +
                all(
                    Declared.UNSUPPORTED,
                    Q25_ABSENT,
                    Capability.IR_REMOTE,
                    Capability.SUBSCREEN_COMPANION
                ) +
                all(
                    Declared.UNSUPPORTED,
                    "$Q25_ABSENT; $NO_PUBLIC_RX_API",
                    Capability.IR_LEARNING
                ) +
                all(Declared.DIAGNOSTIC_ONLY, "MT6631 FM in spec; NOT_RUN", Capability.FM_RADIO) +
                all(
                    Declared.UNKNOWN,
                    "no factory-test dialer code observed on Q25",
                    Capability.FACTORY_TEST_BRIDGE
                )
        )

        val Unknown = ToolsDeviceProfile("unknown", "Unknown device", emptyMap())

        val ALL = listOf(Titan2, Titan2Elite, ZinwaQ27, ZinwaQ25)

        fun byId(id: String?): ToolsDeviceProfile =
            ALL.firstOrNull { it.id == id?.trim() } ?: Unknown
    }
}
