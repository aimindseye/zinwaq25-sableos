package org.sableos.titan2.camera.core

import org.sableos.titan2.camera.core.CameraText.t

/** How the camera was started. */
enum class LaunchKind {
    /** Launcher, Sable Start or an explicit start: the full camera. */
    Normal,

    /** `STILL_IMAGE_CAMERA` (camera gesture, "open the camera"): the full camera in Photo. */
    StillCamera,

    /**
     * `IMAGE_CAPTURE`: another app waits for one photo. Photo/Auto JPEG only; the result goes to the
     * caller's `EXTRA_OUTPUT` (or back as a small bitmap) and the camera closes.
     */
    ImageCapture
}

/**
 * Sable Camera is the default still camera: it answers `STILL_IMAGE_CAMERA` and `IMAGE_CAPTURE`
 * (Aperture keeps video capture, the secure lock-screen camera and the QR scanner). Pure, so the
 * rules are JVM-tested; the action strings are the stable `MediaStore` constants.
 */
object LaunchIntent {
    const val STILL_IMAGE_CAMERA = "android.media.action.STILL_IMAGE_CAMERA"
    const val IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE"

    fun kind(action: String?): LaunchKind = when (action) {
        IMAGE_CAPTURE -> LaunchKind.ImageCapture
        STILL_IMAGE_CAMERA -> LaunchKind.StillCamera
        else -> LaunchKind.Normal
    }

    /** Actions that would leave the single Auto JPEG another app asked for. */
    private val notForCapture = setOf(
        CameraAction.VideoToggle,
        CameraAction.PauseToggle,
        CameraAction.QualityNext,
        CameraAction.NextMode,
        CameraAction.PrevMode,
        CameraAction.NextCaptureMode,
        CameraAction.RawToggle,
        CameraAction.CompareToggle,
        CameraAction.OpenLast
    )

    fun allows(kind: LaunchKind, a: CameraAction): Boolean =
        kind != LaunchKind.ImageCapture || a !in notForCapture

    val CAPTURE_REFUSED: String get() = t("another_app_is_waiting_for")

    /** The longest edge of the bitmap returned when the caller gave no EXTRA_OUTPUT. */
    const val THUMBNAIL_EDGE_PX = 512

    /** Sample size that brings [w] x [h] down to at most [THUMBNAIL_EDGE_PX] on its long edge. */
    fun thumbnailSample(w: Int, h: Int): Int {
        var sample = 1
        while (maxOf(w, h) / sample > THUMBNAIL_EDGE_PX) sample *= 2
        return sample
    }
}
