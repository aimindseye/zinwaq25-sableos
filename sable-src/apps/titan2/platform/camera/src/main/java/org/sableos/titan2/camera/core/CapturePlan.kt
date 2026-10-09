package org.sableos.titan2.camera.core

enum class StillKind(val suffix: String, val ext: String) {
    Jpeg(
        "jpg_std",
        "jpg"
    ),
    HighResJpeg("jpg_hires", "jpg"),
    RawDng("raw", "dng")
}

data class StillPlan(
    val kind: StillKind,
    val size: Size,
    val cameraId: String,
    val maxResMode: Boolean = false
)

object CapturePlanner {
    /**
     * One still for the chosen mode. Falls back to Auto when the requested mode is unavailable,
     * never to a different camera.
     */
    fun single(r: CameraReport, mode: CaptureMode): StillPlan = when {
        mode == CaptureMode.HighRes && r.bestHighRes != null -> StillPlan(
            StillKind.HighResJpeg,
            r.bestHighRes,
            r.camera.id,
            r.bestHighRes in r.maxResSizes
        )

        mode == CaptureMode.Raw && r.bestRaw != null -> StillPlan(
            StillKind.RawDng,
            r.bestRaw,
            r.camera.id
        )

        else -> StillPlan(StillKind.Jpeg, r.bestJpeg, r.camera.id)
    }

    /**
     * Compare/validation bracket: the same scene captured on every available still path so detail
     * can be judged side by side (the doc requires same-scene, same-lighting comparison of
     * standard JPEG, high-res JPEG and RAW/DNG).
     */
    fun bracket(r: CameraReport): List<StillPlan> = buildList {
        add(StillPlan(StillKind.Jpeg, r.bestJpeg, r.camera.id))
        r.bestHighRes?.let {
            add(
                StillPlan(
                    StillKind.HighResJpeg,
                    it,
                    r.camera.id,
                    it in r.maxResSizes
                )
            )
        }
        r.bestRaw?.let { add(StillPlan(StillKind.RawDng, it, r.camera.id)) }
    }

    /** SBL_<yyyyMMdd>_<HHmmss>_cam<id>_<kind>[_n].<ext>; the timestamp is passed in so this stays pure. */
    fun fileName(p: StillPlan, yyyymmdd: String, hhmmss: String, seq: Int = 0): String =
        "SBL_${yyyymmdd}_${hhmmss}_cam${p.cameraId}_${p.kind.suffix}${if (seq > 0) "_$seq" else ""}.${p.kind.ext}"
}

object PreviewChooser {
    private const val ASPECT_TOLERANCE = 0.01

    /**
     * Largest preview size with the same aspect as the capture size (within 1%), capped on the
     * long side; else the closest aspect.
     */
    fun choose(available: List<Size>, capture: Size, maxLongSide: Int = 1920): Size? {
        if (available.isEmpty()) return null
        val target = capture.w.toDouble() / capture.h
        fun diff(s: Size) = kotlin.math.abs(s.w.toDouble() / s.h - target)
        val capped = available.filter { maxOf(it.w, it.h) <= maxLongSide }.ifEmpty { available }
        val exact = capped.filter { diff(it) < ASPECT_TOLERANCE }
        return (exact.ifEmpty { listOf(capped.minBy { diff(it) }) }).maxBy { it.pixels }
    }
}

object CaptureOrientation {
    private const val FULL_TURN = 360

    /**
     * Standard Camera2 JPEG_ORIENTATION: the clockwise rotation applied so the picture is upright
     * for the current display rotation.
     */
    fun jpeg(sensorOrientation: Int, displayRotationDegrees: Int, frontFacing: Boolean): Int {
        val d = ((displayRotationDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN
        return if (frontFacing) {
            (sensorOrientation + d) % FULL_TURN
        } else {
            ((sensorOrientation - d) % FULL_TURN + FULL_TURN) % FULL_TURN
        }
    }
}
