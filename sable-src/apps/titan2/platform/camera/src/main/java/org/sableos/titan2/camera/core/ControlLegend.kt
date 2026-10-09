package org.sableos.titan2.camera.core

/** One legend line: the keys that really resolve to [action], what it does, and its gate. */
data class LegendEntry(
    val keys: String,
    val label: String,
    val action: CameraAction,
    val gate: Gate
)

/**
 * Dismissible control legend. It is generated from [CameraKeyMap], never hand-written, so it
 * cannot document a key that does not work. Unavailable controls are shown with their reason
 * rather than hidden. Func1/Func2 never appear because nothing binds them.
 */
object ControlLegend {
    private val displayName = mapOf(
        "space" to "Space", "enter" to "Enter", "camera" to "Camera", "up" to "↑",
        "down" to "↓", "left" to "←", "right" to "→", "plus" to "+", "minus" to "-",
        "tab" to "Tab", "escape" to "Esc", "back" to "Back", "slash" to "/",
        "volume_up" to "Vol+", "volume_down" to "Vol-"
    )

    /** Display order; every action appears once. */
    private val order = listOf(
        CameraAction.Shutter, CameraAction.VideoToggle, CameraAction.PauseToggle,
        CameraAction.Autofocus, CameraAction.ExposureUp, CameraAction.ExposureDown,
        CameraAction.SelectIso, CameraAction.SelectWb, CameraAction.SelectShutter,
        CameraAction.StepUp, CameraAction.StepDown, CameraAction.ProAuto,
        CameraAction.ZoomIn, CameraAction.ZoomOut, CameraAction.PrevMode,
        CameraAction.NextMode, CameraAction.SwitchCamera, CameraAction.NextCaptureMode,
        CameraAction.QualityNext, CameraAction.CompareToggle, CameraAction.OpenLast,
        CameraAction.ToggleLegend, CameraAction.Back
    )

    private val baseLabels = mapOf(
        CameraAction.Shutter to "Shutter",
        CameraAction.VideoToggle to "Video record",
        CameraAction.PauseToggle to "Pause",
        CameraAction.Autofocus to "Autofocus",
        CameraAction.ExposureUp to "Exposure +",
        CameraAction.ExposureDown to "Exposure -",
        CameraAction.SelectIso to "ISO",
        CameraAction.SelectWb to "White balance",
        CameraAction.SelectShutter to "Shutter speed",
        CameraAction.StepUp to "Zoom in",
        CameraAction.StepDown to "Zoom out",
        CameraAction.ProAuto to "Back to Auto",
        CameraAction.ZoomIn to "Zoom in",
        CameraAction.ZoomOut to "Zoom out",
        CameraAction.PrevMode to "Previous mode",
        CameraAction.NextMode to "Next mode",
        CameraAction.SwitchCamera to "Switch camera",
        CameraAction.NextCaptureMode to "Photo size",
        CameraAction.QualityNext to "Video quality",
        CameraAction.CompareToggle to "Compare bracket",
        CameraAction.OpenLast to "Last capture",
        CameraAction.ToggleLegend to "This legend",
        CameraAction.Back to "Close / exit"
    )

    private val proLabels = mapOf(
        CameraAction.Autofocus to "Focus (manual)",
        CameraAction.ExposureUp to "Exposure",
        CameraAction.StepUp to "Adjust +",
        CameraAction.StepDown to "Adjust -"
    )

    private val videoLabels = mapOf(CameraAction.Shutter to "Record / stop")

    fun label(a: CameraAction, ui: UiMode): String = when (ui) {
        UiMode.Pro -> proLabels[a]
        UiMode.Video -> videoLabels[a]
        UiMode.Photo -> null
    } ?: baseLabels.getValue(a)

    /** Every action has a label; used by tests so a new action cannot ship without one. */
    fun labelled(): Set<CameraAction> = baseLabels.keys

    fun keysFor(
        a: CameraAction,
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown
    ): List<Binding> = (CameraKeyMap.standard + profile.keyOverrides).filterValues { it == a }.keys
        .sortedWith(compareBy({ it.alt }, { it.key }))

    fun keyText(b: Binding): String =
        (if (b.alt) "Alt+" else "") + (displayName[b.key] ?: b.key.uppercase())

    fun entries(c: DeckContext, profile: CameraDeviceProfile): List<LegendEntry> =
        order.mapNotNull { a ->
            val keys = keysFor(a, profile)
            if (keys.isEmpty()) {
                null
            } else {
                LegendEntry(
                    keys.joinToString(" / ", transform = ::keyText),
                    label(a, c.ui),
                    a,
                    ControlGate.check(a, c)
                )
            }
        }
}

/** Touch controls for every semantic action, so touch alone can run the whole camera. */
data class TouchControl(val action: CameraAction, val label: String, val gate: Gate)

object TouchFallback {
    /** Back is the system back gesture/button; every other action has an on-screen control. */
    val systemProvided: Set<CameraAction> = setOf(CameraAction.Back)

    private val labels = mapOf(
        CameraAction.Shutter to "Shutter",
        CameraAction.VideoToggle to "Video",
        CameraAction.PauseToggle to "Pause",
        CameraAction.Autofocus to "AF",
        CameraAction.ExposureUp to "EV",
        CameraAction.ExposureDown to "EV-",
        CameraAction.SelectIso to "ISO",
        CameraAction.SelectWb to "WB",
        CameraAction.SelectShutter to "S",
        CameraAction.StepUp to "▲",
        CameraAction.StepDown to "▼",
        CameraAction.ProAuto to "Auto",
        CameraAction.ZoomIn to "+",
        CameraAction.ZoomOut to "-",
        CameraAction.PrevMode to "◀",
        CameraAction.NextMode to "▶",
        CameraAction.SwitchCamera to "Flip",
        CameraAction.NextCaptureMode to "Size",
        CameraAction.QualityNext to "Quality",
        CameraAction.CompareToggle to "Compare",
        CameraAction.OpenLast to "Last",
        CameraAction.ToggleLegend to "?"
    )

    /** Actions that have a touch control. Must equal every action minus [systemProvided]. */
    fun covered(): Set<CameraAction> = labels.keys

    fun controls(c: DeckContext): List<TouchControl> =
        labels.map { (a, text) -> TouchControl(a, text, ControlGate.check(a, c)) }
}
