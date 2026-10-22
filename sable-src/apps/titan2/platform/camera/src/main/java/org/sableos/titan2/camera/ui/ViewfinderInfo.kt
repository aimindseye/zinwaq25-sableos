package org.sableos.titan2.camera.ui

import org.sableos.titan2.camera.android.CameraController
import org.sableos.titan2.camera.core.CameraReport
import org.sableos.titan2.camera.core.CaptureMode
import org.sableos.titan2.camera.core.PhotoPath
import org.sableos.titan2.camera.core.ProField
import org.sableos.titan2.camera.core.UiMode
import org.sableos.titan2.camera.core.VideoOption

/**
 * The activity state the viewfinder text is read from: the header, the HUD line and the chips.
 * [proValue] is the selected Pro field's value, null when this camera reports no Pro limits.
 */
data class ViewfinderInfo(
    val camId: String,
    val ui: UiMode,
    val mode: CaptureMode,
    val compare: Boolean,
    val proField: ProField,
    val proValue: String?,
    val video: VideoOption?,
    val micGranted: Boolean,
    val forApp: Boolean
) {
    /** Header over the viewfinder, as in the design: "PHOTO · AUTO", "PRO · ISO", "VIDEO · 1080p". */
    fun title(): String {
        val sub = when (ui) {
            UiMode.Photo -> PhotoPath.label(mode)
            UiMode.Pro -> proField.label.uppercase(java.util.Locale.ROOT)
            UiMode.Video -> video?.label ?: "-"
        }
        val head = ui.label.uppercase(java.util.Locale.ROOT) + " · " + sub
        return if (forApp) "$head · FOR APP" else head
    }

    /** The HUD line: camera, mode, zoom and what the mode adjusts, then the lock badges. */
    fun hud(controller: CameraController): String {
        val zoomText = "%.1f".format(controller.zoom)
        return when (ui) {
            UiMode.Photo ->
                "cam $camId · ${mode.name}${if (compare) " +compare" else ""}" +
                    " · ${zoomText}x · EV ${controller.aeSteps}"

            UiMode.Pro ->
                "cam $camId · Pro · ${zoomText}x" +
                    (proValue?.let { " · ${proField.label} $it" } ?: "")

            UiMode.Video ->
                "cam $camId · ${video?.label ?: "no recordable size"} · ${zoomText}x" +
                    if (!micGranted) " · no mic" else ""
        } + lockBadges(controller.locks)
    }

    /** Chips along the bottom of the viewfinder: what the next shot will be. */
    fun chips(controller: CameraController): List<String> = buildList {
        add("%.1f×".format(java.util.Locale.US, controller.zoom))
        when (ui) {
            UiMode.Photo -> {
                add(PhotoPath.label(mode))
                if (compare) add("Compare")
            }

            UiMode.Pro -> add("Manual")

            UiMode.Video -> {
                video?.let { add(it.label) }
                if (!micGranted) add("No mic")
            }
        }
        if (ui != UiMode.Pro && controller.aeSteps != 0) add("EV ${controller.aeSteps}")
        if (controller.locks.afLocked) add("AF-L")
        if (controller.locks.aeLocked) add("AE-L")
    }
}

/** M: the next photo sub-mode (Auto, high-res, RAW) that [cam] supports, after [mode]. */
internal fun nextPhotoMode(mode: CaptureMode, cam: CameraReport): CaptureMode {
    val avail = cam.modes.filter {
        it.value.ok &&
            it.key in listOf(CaptureMode.Auto, CaptureMode.HighRes, CaptureMode.Raw)
    }.keys.toList()
    return avail[(avail.indexOf(mode).coerceAtLeast(0) + 1) % avail.size]
}

private fun lockBadges(locks: CameraController.LockControls): String =
    (if (locks.afLocked) " · AF-L" else "") + (if (locks.aeLocked) " · AE-L" else "")
