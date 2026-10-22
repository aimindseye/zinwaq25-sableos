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
        "volume_up" to "Vol+", "volume_down" to "Vol-", "backspace" to "Del",
        "delete" to "Delete"
    )

    /** Display order; every action appears once. */
    private val order = listOf(
        CameraAction.Shutter, CameraAction.VideoToggle, CameraAction.PauseToggle,
        CameraAction.Autofocus, CameraAction.AfLockToggle, CameraAction.ExposureUp,
        CameraAction.ExposureDown, CameraAction.AeLockToggle,
        CameraAction.SelectIso, CameraAction.SelectWb, CameraAction.SelectShutter,
        CameraAction.StepUp, CameraAction.StepDown, CameraAction.ProAuto,
        CameraAction.ZoomIn, CameraAction.ZoomOut, CameraAction.PrevMode,
        CameraAction.NextMode, CameraAction.SwitchCamera, CameraAction.RawToggle,
        CameraAction.NextCaptureMode,
        CameraAction.QualityNext, CameraAction.CompareToggle, CameraAction.OpenLast,
        CameraAction.ToggleLegend, CameraAction.Back
    )

    private val baseLabels = mapOf(
        CameraAction.Shutter to "Shutter",
        CameraAction.VideoToggle to "Video record",
        CameraAction.PauseToggle to "Pause",
        CameraAction.Autofocus to "Autofocus",
        CameraAction.AfLockToggle to "AF lock on/off",
        CameraAction.AeLockToggle to "AE lock on/off",
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
        CameraAction.RawToggle to "RAW / high-res",
        CameraAction.QualityNext to "Video quality",
        CameraAction.CompareToggle to "Compare bracket",
        CameraAction.OpenLast to "Review last capture",
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
        .filter { ArrowGuard.shown(it, profile) }
        .sortedWith(compareBy({ it.hold }, { it.alt }, { it.key }))

    fun keyText(b: Binding): String = (if (b.hold) "Hold " else "") +
        (if (b.alt) "Alt+" else "") + (displayName[b.key] ?: b.key.uppercase())

    private val reviewOrder = listOf(
        ReviewAction.Older,
        ReviewAction.Newer,
        ReviewAction.Delete,
        ReviewAction.Share,
        ReviewAction.ShowInFiles,
        ReviewAction.Open,
        ReviewAction.Close
    )

    private val reviewLabels = mapOf(
        ReviewAction.Older to "Older capture",
        ReviewAction.Newer to "Newer capture",
        ReviewAction.Delete to "Delete (asks first)",
        ReviewAction.Share to "Share",
        ReviewAction.ShowInFiles to "Show in Files",
        ReviewAction.Open to "Open in another app",
        ReviewAction.Close to "Back to the viewfinder",
        ReviewAction.Confirm to "Delete it",
        ReviewAction.Cancel to "Keep it"
    )

    fun reviewLabel(a: ReviewAction): String = reviewLabels.getValue(a)

    /** Every review action has a label; used by tests. */
    fun reviewLabelled(): Set<ReviewAction> = reviewLabels.keys

    /**
     * Quick-review lines (key text, label), generated from [ReviewKeyMap] like the viewfinder
     * legend, for the browse layer or the delete confirmation.
     */
    fun reviewEntries(confirming: Boolean = false): List<Pair<String, String>> {
        val actions = if (confirming) {
            listOf(ReviewAction.Confirm, ReviewAction.Cancel)
        } else {
            reviewOrder
        }
        return actions.mapNotNull { a ->
            ReviewKeyMap.keys(a, confirming).sortedBy { it.key }.takeIf { it.isNotEmpty() }
                ?.let { keys -> keys.joinToString(" / ", transform = ::keyText) to reviewLabel(a) }
        }
    }

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

/** On-screen buttons of the quick review: the same [ReviewAction]s the keys reach. */
object ReviewTouch {
    val browse: List<Pair<ReviewAction, String>> = listOf(
        ReviewAction.Older to "◀",
        ReviewAction.Newer to "▶",
        ReviewAction.Delete to "Delete",
        ReviewAction.Share to "Share",
        ReviewAction.ShowInFiles to "Files",
        ReviewAction.Open to "Open",
        ReviewAction.Close to "Close"
    )

    val confirm: List<Pair<ReviewAction, String>> = listOf(
        ReviewAction.Confirm to "Delete",
        ReviewAction.Cancel to "Keep"
    )
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
        CameraAction.AfLockToggle to "AF-L",
        CameraAction.AeLockToggle to "AE-L",
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
        CameraAction.RawToggle to "RAW",
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

/** One hint in the dock under the viewfinder: the key as printed, what it does, and its gate. */
data class Hint(
    val key: Binding,
    val keyText: String,
    val label: String,
    val action: CameraAction,
    val gate: Gate
)

/**
 * The hint dock (design `sable-camera-capture-titan2`: "[Space] Shutter  [Z] Zoom" under Photo,
 * "[R] RAW/HiRes [E] Exposure [F] Focus" under Pro, "[Space] Record" under Video). Every hint names
 * a binding that really resolves to its action on this profile, so the dock cannot advertise a key
 * that does not work; tests check that.
 */
object HintDock {
    private val photo = listOf(
        Binding("space") to CameraAction.Shutter,
        Binding("z") to CameraAction.ZoomIn,
        Binding("r") to CameraAction.RawToggle,
        Binding("h") to CameraAction.ToggleLegend
    )
    private val pro = listOf(
        Binding("space") to CameraAction.Shutter,
        Binding("e") to CameraAction.ExposureUp,
        Binding("f") to CameraAction.Autofocus,
        Binding("h") to CameraAction.ToggleLegend
    )
    private val video = listOf(
        Binding("space") to CameraAction.Shutter,
        Binding("z") to CameraAction.ZoomIn,
        Binding("q") to CameraAction.QualityNext,
        Binding("h") to CameraAction.ToggleLegend
    )

    private val shortLabels = mapOf(
        CameraAction.ZoomIn to "Zoom",
        CameraAction.RawToggle to "RAW/HiRes",
        CameraAction.ToggleLegend to "Keys",
        CameraAction.ExposureUp to "Exposure",
        CameraAction.Autofocus to "Focus",
        CameraAction.QualityNext to "Quality"
    )

    /** Bindings of the dock for [ui], before gating (tests check each resolves to its action). */
    fun bindings(ui: UiMode): List<Pair<Binding, CameraAction>> = when (ui) {
        UiMode.Photo -> photo
        UiMode.Pro -> pro
        UiMode.Video -> video
    }

    fun hints(c: DeckContext, profile: CameraDeviceProfile): List<Hint> =
        bindings(c.ui).filter { (b, a) -> CameraKeyMap.resolve(b, profile) == a }.map { (b, a) ->
            val label = if (a == CameraAction.Shutter) {
                ControlLegend.label(a, c.ui).substringBefore(" /")
            } else {
                shortLabels[a] ?: ControlLegend.label(a, c.ui)
            }
            Hint(b, ControlLegend.keyText(b), label, a, ControlGate.check(a, c))
        }
}
