package org.sableos.titan2.camera.core

enum class EvidenceLevel { None, ResearchOnly, RetailValidated }

/**
 * Per-device camera facts used for VALIDATION and key bindings only. A profile never grants a
 * mode: modes come from what the device actually reports. Expectations that fail to match are
 * surfaced as [Mismatch]es so stale evidence cannot silently drive behaviour.
 * Titan 2 numbers are the org's research evidence (platform_sable CAMERA_CAPTURE_UX); Elite and
 * Q27 and Q25 must be captured independently.
 */
data class CameraDeviceProfile(
    val id: String,
    val evidence: EvidenceLevel,
    val expectedPublicIds: Map<String, Facing> = emptyMap(),
    val expectedHighResJpeg: Map<String, Size> = emptyMap(),
    val expectedRaw: Map<String, Size> = emptyMap(),
    val keyOverrides: Map<Binding, CameraAction> = emptyMap()
) {
    companion object {
        private const val HIRES_BACK_W = 8192
        private const val HIRES_BACK_H = 6144
        private const val HIRES_FRONT_W = 6560
        private const val HIRES_FRONT_H = 4928
        private const val RAW_BACK_W = 4096
        private const val RAW_BACK_H = 3072

        val Titan2 = CameraDeviceProfile(
            id = "titan2",
            evidence = EvidenceLevel.ResearchOnly,
            expectedPublicIds = mapOf("0" to Facing.Back, "1" to Facing.Front),
            expectedHighResJpeg = mapOf(
                "0" to Size(HIRES_BACK_W, HIRES_BACK_H),
                "1" to Size(HIRES_FRONT_W, HIRES_FRONT_H)
            ),
            expectedRaw = mapOf("0" to Size(RAW_BACK_W, RAW_BACK_H)),
            // Stock-camera probe (unihertz-titan2 TITAN2_TIER1_FINDINGS): Volume Up/Down enter the
            // real capture pipeline. Func1/Func2 are intercepted by vendor framework policy before
            // app delivery, so they are NOT bound here.
            keyOverrides = mapOf(
                Binding("volume_up") to CameraAction.Shutter,
                Binding("volume_down") to CameraAction.Shutter
            )
        )
        val Titan2Elite = CameraDeviceProfile("titan2-elite", EvidenceLevel.None)
        val ZinwaQ27 = CameraDeviceProfile("zinwa-q27", EvidenceLevel.None)

        // Public spec only (50 MP rear with flash, 8 MP front); ids, sizes and keys not captured on a Q25 yet.
        val ZinwaQ25 = CameraDeviceProfile("zinwa-q25", EvidenceLevel.None)
        val Unknown = CameraDeviceProfile("unknown", EvidenceLevel.None)
        fun byId(id: String?) =
            listOf(Titan2, Titan2Elite, ZinwaQ27, ZinwaQ25).firstOrNull { it.id == id } ?: Unknown
    }
}
