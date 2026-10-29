package org.sableos.titan2.camera.core

import org.sableos.titan2.camera.core.CameraText.t

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
    private val displayName get() = mapOf(
        "space" to t("space"), "enter" to t("enter"), "camera" to t("camera"), "up" to "↑",
        "down" to "↓", "left" to "←", "right" to "→", "plus" to "+", "minus" to "-",
        "tab" to t("tab"), "escape" to t("esc"), "back" to t("back"), "slash" to "/",
        "volume_up" to "Vol+", "volume_down" to "Vol-", "backspace" to t("del"),
        "delete" to t("delete")
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

    private val baseLabels get() = mapOf(
        CameraAction.Shutter to t("shutter"),
        CameraAction.VideoToggle to t("video_record"),
        CameraAction.PauseToggle to t("pause"),
        CameraAction.Autofocus to t("autofocus"),
        CameraAction.AfLockToggle to t("af_lock_on_off"),
        CameraAction.AeLockToggle to t("ae_lock_on_off"),
        CameraAction.ExposureUp to t("exposure"),
        CameraAction.ExposureDown to t("exposure_2"),
        CameraAction.SelectIso to "ISO",
        CameraAction.SelectWb to t("white_balance"),
        CameraAction.SelectShutter to t("shutter_speed"),
        CameraAction.StepUp to t("zoom_in"),
        CameraAction.StepDown to t("zoom_out"),
        CameraAction.ProAuto to t("back_to_auto"),
        CameraAction.ZoomIn to t("zoom_in"),
        CameraAction.ZoomOut to t("zoom_out"),
        CameraAction.PrevMode to t("previous_mode"),
        CameraAction.NextMode to t("next_mode"),
        CameraAction.SwitchCamera to t("switch_camera"),
        CameraAction.NextCaptureMode to t("photo_size"),
        CameraAction.RawToggle to t("raw_high_res"),
        CameraAction.QualityNext to t("video_quality"),
        CameraAction.CompareToggle to t("compare_bracket"),
        CameraAction.OpenLast to t("review_last_capture"),
        CameraAction.ToggleLegend to t("this_legend"),
        CameraAction.Back to t("close_exit")
    )

    private val proLabels get() = mapOf(
        CameraAction.Autofocus to t("focus_manual"),
        CameraAction.ExposureUp to t("exposure_3"),
        CameraAction.StepUp to t("adjust"),
        CameraAction.StepDown to t("adjust_2")
    )

    private val videoLabels get() = mapOf(CameraAction.Shutter to t("record_stop"))

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

    fun keyText(b: Binding): String = (if (b.hold) t("hold") else "") +
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

    private val reviewLabels get() = mapOf(
        ReviewAction.Older to t("older_capture"),
        ReviewAction.Newer to t("newer_capture"),
        ReviewAction.Delete to t("delete_asks_first"),
        ReviewAction.Share to t("share"),
        ReviewAction.ShowInFiles to t("show_in_files"),
        ReviewAction.Open to t("open_in_another_app"),
        ReviewAction.Close to t("back_to_the_viewfinder"),
        ReviewAction.Confirm to t("delete_it"),
        ReviewAction.Cancel to t("keep_it")
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
    val browse: List<Pair<ReviewAction, String>> get() = listOf(
        ReviewAction.Older to "◀",
        ReviewAction.Newer to "▶",
        ReviewAction.Delete to t("delete"),
        ReviewAction.Share to t("share"),
        ReviewAction.ShowInFiles to t("files_2"),
        ReviewAction.Open to t("open"),
        ReviewAction.Close to t("close")
    )

    val confirm: List<Pair<ReviewAction, String>> get() = listOf(
        ReviewAction.Confirm to t("delete"),
        ReviewAction.Cancel to t("keep")
    )
}

/** Touch controls for every semantic action, so touch alone can run the whole camera. */
data class TouchControl(val action: CameraAction, val label: String, val gate: Gate)

object TouchFallback {
    /** Back is the system back gesture/button; every other action has an on-screen control. */
    val systemProvided: Set<CameraAction> = setOf(CameraAction.Back)

    private val labels get() = mapOf(
        CameraAction.Shutter to t("shutter"),
        CameraAction.VideoToggle to t("video_2"),
        CameraAction.PauseToggle to t("pause"),
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
        CameraAction.ProAuto to t("auto"),
        CameraAction.ZoomIn to "+",
        CameraAction.ZoomOut to "-",
        CameraAction.PrevMode to "◀",
        CameraAction.NextMode to "▶",
        CameraAction.SwitchCamera to t("flip"),
        CameraAction.NextCaptureMode to t("size"),
        CameraAction.RawToggle to "RAW",
        CameraAction.QualityNext to t("quality"),
        CameraAction.CompareToggle to t("compare_2"),
        CameraAction.OpenLast to t("last"),
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
        CameraAction.ZoomIn to t("zoom"),
        CameraAction.RawToggle to "RAW/HiRes",
        CameraAction.ToggleLegend to t("keys"),
        CameraAction.ExposureUp to t("exposure_3"),
        CameraAction.Autofocus to t("focus"),
        CameraAction.QualityNext to t("quality")
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
