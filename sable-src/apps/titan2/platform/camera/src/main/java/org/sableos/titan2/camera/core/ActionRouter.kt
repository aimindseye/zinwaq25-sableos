package org.sableos.titan2.camera.core

/**
 * Stock order is Timelapse, Video, Photo, Pro, More. Timelapse and More are not built, so the strip
 * is Video, Photo, Pro.
 */
enum class UiMode(val label: String) { Video("Video"), Photo("Photo"), Pro("Pro") }

sealed interface Cmd {
    data class Act(val action: CameraAction) : Cmd
    data class ProSelect(val field: ProField) : Cmd
    data class ProStep(val dir: Int) : Cmd
    data object ProAuto : Cmd
    data class SetUi(val mode: UiMode) : Cmd
    data class UiStep(val dir: Int) : Cmd
    data object Nop : Cmd
}

/**
 * One key means different things per mode: arrows zoom in Photo/Video but step the selected field
 * in Pro; Space takes a picture in Photo/Pro and starts or stops recording in Video. Keeping this
 * table pure lets every rule be tested without a device.
 */
object ActionRouter {
    fun route(ui: UiMode, a: CameraAction): Cmd = when (a) {
        CameraAction.StepUp -> routeStep(ui, 1, CameraAction.ZoomIn)

        CameraAction.StepDown -> routeStep(ui, -1, CameraAction.ZoomOut)

        CameraAction.SelectWb,
        CameraAction.SelectIso,
        CameraAction.SelectShutter,
        CameraAction.ExposureUp,
        CameraAction.ExposureDown,
        CameraAction.Autofocus,
        CameraAction.ProAuto -> routeProKey(ui, a)

        CameraAction.Shutter,
        CameraAction.VideoToggle,
        CameraAction.PauseToggle,
        CameraAction.NextCaptureMode,
        CameraAction.CompareToggle,
        CameraAction.QualityNext -> routeCaptureKey(ui, a)

        CameraAction.NextMode -> Cmd.UiStep(1)

        CameraAction.PrevMode -> Cmd.UiStep(-1)

        else -> Cmd.Act(a)
    }

    /** Arrow Up/Down: step the selected Pro field, or zoom outside Pro. */
    private fun routeStep(ui: UiMode, dir: Int, zoom: CameraAction): Cmd =
        if (ui == UiMode.Pro) Cmd.ProStep(dir) else Cmd.Act(zoom)

    /** Keys whose meaning changes when the Pro strip is showing. */
    private fun routeProKey(ui: UiMode, a: CameraAction): Cmd {
        val pro = ui == UiMode.Pro
        return when (a) {
            CameraAction.SelectWb -> proOnly(ui, ProField.Wb)

            CameraAction.SelectIso -> proOnly(ui, ProField.Iso)

            CameraAction.SelectShutter -> proOnly(ui, ProField.Shutter)

            CameraAction.ExposureUp, CameraAction.ExposureDown ->
                if (pro) Cmd.ProSelect(ProField.Ev) else Cmd.Act(a)

            CameraAction.Autofocus -> if (pro) Cmd.ProSelect(ProField.Focus) else Cmd.Act(a)

            CameraAction.ProAuto -> if (pro) Cmd.ProAuto else Cmd.Nop

            else -> Cmd.Act(a)
        }
    }

    /** Keys whose meaning changes between Video and Photo. */
    private fun routeCaptureKey(ui: UiMode, a: CameraAction): Cmd = when (a) {
        CameraAction.Shutter ->
            if (ui == UiMode.Video) Cmd.Act(CameraAction.VideoToggle) else Cmd.Act(a)

        CameraAction.VideoToggle -> if (ui == UiMode.Video) Cmd.Act(a) else Cmd.SetUi(UiMode.Video)

        CameraAction.PauseToggle -> if (ui == UiMode.Video) Cmd.Act(a) else Cmd.Nop

        CameraAction.NextCaptureMode, CameraAction.CompareToggle ->
            if (ui == UiMode.Photo) Cmd.Act(a) else Cmd.Nop

        CameraAction.QualityNext -> if (ui == UiMode.Video) Cmd.Act(a) else Cmd.Nop

        else -> Cmd.Act(a)
    }

    private fun proOnly(ui: UiMode, f: ProField): Cmd =
        if (ui == UiMode.Pro) Cmd.ProSelect(f) else Cmd.Nop

    /**
     * Modes the mode keys may land on. Photo is always there; Video needs recordable sizes; Pro
     * needs manual sensor control.
     */
    fun available(r: CameraReport): List<UiMode> = buildList {
        if (VideoPlanner.options(r.camera.videoSizes).isNotEmpty()) add(UiMode.Video)
        if (r.modes[CaptureMode.Auto]?.ok == true) add(UiMode.Photo)
        if (r.modes[CaptureMode.Pro]?.ok == true) add(UiMode.Pro)
    }

    /** Move along [available] without wrapping, so Left at the first mode stays put. */
    fun step(cur: UiMode, dir: Int, available: List<UiMode>): UiMode {
        val i = available.indexOf(cur)
        if (i < 0) return available.firstOrNull() ?: cur
        return available[(i + if (dir >= 0) 1 else -1).coerceIn(0, available.lastIndex)]
    }
}
