package org.sableos.titan2.camera.core

/**
 * Camera capability model (CAMERA_ENHANCEMENT_MODEL). Four layers are kept separate and this code
 * only ever reasons about the third:
 *   sensor capability != HAL capability != ordinary-app-visible capability != privileged
 *   system-app capability.
 * Everything here describes what an ordinary app can see through Camera2 on public camera IDs.
 */
enum class Facing { Back, Front, External }

data class Size(val w: Int, val h: Int) {
    val pixels: Long get() = w.toLong() * h
    val megapixels: Double get() = pixels / 1_000_000.0
    override fun toString() = "${w}x$h"
}

data class FloatRange(val lo: Float, val hi: Float) {
    fun contains(v: Float) = v in lo..hi
}
data class IntBounds(val lo: Int, val hi: Int)
data class LongBounds(val lo: Long, val hi: Long)

/**
 * Facts read from CameraCharacteristics for one *public* camera id. Built by the Android glue; the
 * core never touches the platform.
 */
data class CameraInfo(
    val id: String,
    val facing: Facing,
    // every JPEG size in the ordinary stream map (may include vendor high-res sizes)
    val jpegSizes: List<Size>,
    /**
     * SENSOR_INFO_ACTIVE_ARRAY_SIZE. JPEG sizes with more pixels than this are vendor high-res,
     * not conventional.
     */
    val activeArray: Size? = null,
    // StreamConfigurationMap.getHighResolutionOutputSizes
    val jpegHighResSizes: List<Size> = emptyList(),
    /**
     * Sizes only reachable with SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION (separate stream map on
     * ultra-high-res sensors).
     */
    val jpegMaxResSizes: List<Size> = emptyList(),
    val rawSizes: List<Size> = emptyList(),
    val hasRaw: Boolean = false,
    val manualSensor: Boolean = false,
    val manualPostProcessing: Boolean = false,
    val isoRange: IntBounds? = null,
    val exposureNsRange: LongBounds? = null,
    val minFocusDistance: Float = 0f, // diopters, 0 = fixed focus
    val zoomRatio: FloatRange? = null,
    val aeCompensationSteps: IntBounds? = null,
    val aeCompensationStep: Float = 0f,
    val flashAvailable: Boolean = false,
    val physicalIdCount: Int = 0,
    val sensorOrientation: Int = 90,
    /** CONTROL_AWB_AVAILABLE_MODES as raw Camera2 ints. Empty = not reported (only Auto offered). */
    val awbModes: Set<Int> = emptySet(),
    /**
     * Output sizes usable by MediaRecorder
     * (StreamConfigurationMap.getOutputSizes(MediaRecorder::class.java)).
     */
    val videoSizes: List<Size> = emptyList()
)

enum class CaptureMode { Auto, HighRes, Raw, Pro, Tele }

sealed interface ModeAvailability {
    data object Available : ModeAvailability
    data class Unavailable(val reason: String) : ModeAvailability
    val ok: Boolean get() = this is Available
}

data class CameraReport(
    val camera: CameraInfo,
    val modes: Map<CaptureMode, ModeAvailability>,
    val bestJpeg: Size,
    val bestHighRes: Size?,
    val bestRaw: Size?,
    val zoomSteps: List<Float>,
    /** Sizes that need the maximum-resolution pixel mode on the request and output config. */
    val maxResSizes: Set<Size> = emptySet()
)

enum class MismatchKind { MissingCamera, MissingHighRes, MissingRaw, UnexpectedCameraCount, Note }
data class Mismatch(val kind: MismatchKind, val cameraId: String?, val message: String)

data class CapabilityReport(
    val cameras: List<CameraReport>,
    val mismatches: List<Mismatch>,
    val profileId: String?
) {
    fun camera(id: String): CameraReport? = cameras.firstOrNull { it.camera.id == id }
    fun defaultCamera(): CameraReport? =
        cameras.firstOrNull { it.camera.facing == Facing.Back } ?: cameras.firstOrNull()
}
