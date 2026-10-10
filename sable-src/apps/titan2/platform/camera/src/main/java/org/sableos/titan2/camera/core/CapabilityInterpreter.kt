package org.sableos.titan2.camera.core

object CapabilityInterpreter {
    private const val ASPECT_TOLERANCE = 0.02
    private const val DEFAULT_ASPECT = 4.0 / 3.0
    private const val FALLBACK_JPEG_W = 1920
    private const val FALLBACK_JPEG_H = 1080

    fun interpret(
        cameras: List<CameraInfo>,
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown
    ): CapabilityReport {
        val reports = cameras.map { report(it, profile) }
        return CapabilityReport(
            reports,
            validate(cameras, profile),
            profile.id.takeIf {
                profile.evidence !=
                    EvidenceLevel.None
            }
        )
    }

    /**
     * Largest size whose aspect matches the sensor (within 2%); else the largest. Mirrors the
     * proven MVP's "conventional 4:3" rule.
     */
    private fun conventional(sizes: List<Size>, active: Size?): Size? {
        if (sizes.isEmpty()) return null
        val target = active?.let { it.w.toDouble() / it.h } ?: DEFAULT_ASPECT
        return sizes.filter {
            kotlin.math.abs(it.w.toDouble() / it.h - target) < ASPECT_TOLERANCE
        }.maxByOrNull { it.pixels }
            ?: sizes.maxByOrNull { it.pixels }
    }

    fun report(
        c: CameraInfo,
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown
    ): CameraReport {
        // Titan 2 evidence: 8192x6144 sits in the ORDINARY JPEG map (not the high-res or
        // maximum-resolution maps), so "normal" must be defined against the sensor: JPEG sizes
        // with more pixels than the active array are vendor high-res. Sizes the profile marks as
        // slow remosaic output are high-res whatever the active array says.
        val limit = c.activeArray?.pixels
        fun isHigh(s: Size) = s in profile.remosaicJpegSizes || (limit != null && s.pixels > limit)
        val normal = c.jpegSizes.filterNot { isHigh(it) }.ifEmpty { c.jpegSizes }
        val bestJpeg = conventional(normal, c.activeArray)
            ?: Size(FALLBACK_JPEG_W, FALLBACK_JPEG_H)
        val aboveActive = c.jpegSizes.filter { isHigh(it) }
        val bestHigh = (aboveActive + c.jpegHighResSizes + c.jpegMaxResSizes).maxByOrNull {
            it.pixels
        }?.takeIf {
            it.pixels > bestJpeg.pixels
        }
        val bestRaw = if (c.hasRaw) c.rawSizes.maxByOrNull { it.pixels } else null
        val modes = linkedMapOf<CaptureMode, ModeAvailability>()
        modes[CaptureMode.Auto] = autoAvailability(c)
        modes[CaptureMode.HighRes] = if (bestHigh != null) {
            ModeAvailability.Available
        } else {
            ModeAvailability.Unavailable("no JPEG size larger than the conventional $bestJpeg")
        }
        modes[CaptureMode.Raw] = rawAvailability(c, bestRaw)
        modes[CaptureMode.Pro] = proAvailability(c)
        // Tele (hidden physical camera) is a privileged-system-app capability; it can never come
        // from the ordinary public-id path.
        modes[CaptureMode.Tele] =
            ModeAvailability.Unavailable(
                "requires SYSTEM_CAMERA; deferred until evidence and access gates pass"
            )
        return CameraReport(
            c,
            modes,
            bestJpeg,
            bestHigh,
            bestRaw,
            ZoomPlanner.steps(c.zoomRatio),
            c.jpegMaxResSizes.toSet()
        )
    }

    private fun autoAvailability(c: CameraInfo): ModeAvailability = if (c.jpegSizes.isNotEmpty()) {
        ModeAvailability.Available
    } else {
        ModeAvailability.Unavailable("no JPEG output sizes reported")
    }

    private fun rawAvailability(c: CameraInfo, bestRaw: Size?): ModeAvailability = when {
        !c.hasRaw -> ModeAvailability.Unavailable("RAW capability not reported on this camera id")

        bestRaw == null -> ModeAvailability.Unavailable(
            "RAW reported but no RAW_SENSOR output size"
        )

        else -> ModeAvailability.Available
    }

    private fun proAvailability(c: CameraInfo): ModeAvailability = when {
        !c.manualSensor -> ModeAvailability.Unavailable("MANUAL_SENSOR not supported")

        c.isoRange == null || c.exposureNsRange == null ->
            ModeAvailability.Unavailable("ISO/exposure ranges not reported")

        else -> ModeAvailability.Available
    }

    private fun validate(cams: List<CameraInfo>, p: CameraDeviceProfile): List<Mismatch> {
        if (p.evidence == EvidenceLevel.None) {
            return listOf(
                Mismatch(
                    MismatchKind.Note,
                    null,
                    "No validated camera profile for '${p.id}': " +
                        "capabilities come only from what this device reports."
                )
            )
        }
        val byId = cams.associateBy { it.id }
        val out = mutableListOf<Mismatch>()
        out += validateCameras(cams, byId, p)
        out += validateHighRes(byId, p)
        out += validateRaw(byId, p)
        if (p.evidence == EvidenceLevel.ResearchOnly) {
            out +=
                Mismatch(
                    MismatchKind.Note,
                    null,
                    "Profile '${p.id}' is research evidence, not validated on this build; " +
                        "treat expectations as hypotheses."
                )
        }
        return out
    }

    private fun validateCameras(
        cams: List<CameraInfo>,
        byId: Map<String, CameraInfo>,
        p: CameraDeviceProfile
    ): List<Mismatch> {
        val out = mutableListOf<Mismatch>()
        p.expectedPublicIds.forEach { (id, facing) ->
            val c = byId[id]
            if (c == null) {
                out +=
                    Mismatch(
                        MismatchKind.MissingCamera,
                        id,
                        "Expected public camera $id ($facing) is not visible to an ordinary app."
                    )
            } else if (c.facing != facing) {
                out +=
                    Mismatch(
                        MismatchKind.MissingCamera,
                        id,
                        "Camera $id reports ${c.facing}, expected $facing."
                    )
            }
        }
        if (cams.size != p.expectedPublicIds.size) {
            out +=
                Mismatch(
                    MismatchKind.UnexpectedCameraCount,
                    null,
                    "Ordinary app sees ${cams.size} public cameras; " +
                        "profile expects ${p.expectedPublicIds.size}."
                )
        }
        return out
    }

    private fun validateHighRes(
        byId: Map<String, CameraInfo>,
        p: CameraDeviceProfile
    ): List<Mismatch> {
        val out = mutableListOf<Mismatch>()
        p.expectedHighResJpeg.forEach { (id, want) ->
            val have =
                byId[id]?.jpegHighResSizes.orEmpty() + byId[id]?.jpegMaxResSizes.orEmpty() +
                    byId[id]?.jpegSizes.orEmpty()
            if (have.none { it == want }) {
                out +=
                    Mismatch(
                        MismatchKind.MissingHighRes,
                        id,
                        "Camera $id does not offer the researched high-res JPEG $want."
                    )
            }
        }
        return out
    }

    private fun validateRaw(byId: Map<String, CameraInfo>, p: CameraDeviceProfile): List<Mismatch> {
        val out = mutableListOf<Mismatch>()
        p.expectedRaw.forEach { (id, want) ->
            val c = byId[id]
            if (c == null || !c.hasRaw || want !in c.rawSizes) {
                out +=
                    Mismatch(
                        MismatchKind.MissingRaw,
                        id,
                        "Camera $id does not offer the researched RAW size $want."
                    )
            }
        }
        return out
    }
}

object ZoomPlanner {
    private val ZOOM_STOPS = listOf(0.6f, 1f, 2f, 3f, 5f, 8f, 10f)
    private const val STEP_EPSILON = 0.001f

    fun steps(range: FloatRange?): List<Float> {
        if (range == null) return listOf(1f)
        val inRange = ZOOM_STOPS.filter { range.contains(it) }
        return inRange.ifEmpty { listOf(range.lo.coerceAtLeast(1f).coerceAtMost(range.hi)) }
    }
    fun clamp(z: Float, range: FloatRange?): Float = if (range ==
        null
    ) {
        1f
    } else {
        z.coerceIn(range.lo, range.hi)
    }

    /** Next stop in direction [dir] (+1/-1); stays put at the ends. */
    fun step(current: Float, dir: Int, range: FloatRange?): Float {
        val s = steps(range)
        return if (dir >
            0
        ) {
            s.firstOrNull { it > current + STEP_EPSILON } ?: s.last()
        } else {
            s.lastOrNull {
                it <
                    current - STEP_EPSILON
            }
                ?: s.first()
        }
    }
}
