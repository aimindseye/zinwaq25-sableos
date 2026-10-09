package org.sableos.titan2.camera.core

/**
 * Sable Camera Control Deck, source architecture (docs/SABLE_CAMERA_CONTROL_DECK.md).
 *
 * Layers, all pure so they are JVM-testable:
 *   raw key code -> [RawKeys] -> core key name -> [CameraKeyMap] -> [CameraAction]
 *   touch control -> [TouchFallback] -> the same [CameraAction]
 *   [CameraAction] -> [ActionRouter] -> Cmd -> [ControlGate] / [ParameterStrips] / [DeckState]
 * Physical keys and touch controls therefore run one semantic path. Nothing here is runtime
 * validated on a device.
 */
object DeckPolicy {
    /** Orientation follows the sensor/system. The camera never forces landscape. */
    const val ORIENTATION_POLICY = "AUTO"
    const val GLOBAL_LANDSCAPE_LOCK = false
    const val PORTRAIT_SUPPORTED = true
    const val STRIP_TIMEOUT_MS = 2500L
    const val STRIP_MAX_CELLS = 7
}

enum class DeckPresentation { Portrait, LandscapeDeck }

object DeckOrientation {
    /** Landscape gets the Control Deck; portrait and square stay the minimal conventional UI. */
    fun presentation(widthPx: Int, heightPx: Int): DeckPresentation =
        if (widthPx > heightPx) DeckPresentation.LandscapeDeck else DeckPresentation.Portrait

    /** Touch chrome is always there in portrait; in the deck it appears only when revealed. */
    fun touchChromeVisible(p: DeckPresentation, revealed: Boolean): Boolean =
        p == DeckPresentation.Portrait || revealed
}

/** Result of asking whether a control may run on this camera right now. */
sealed interface Gate {
    data object Open : Gate
    data class Closed(val reason: String) : Gate

    val isOpen: Boolean get() = this is Open
}

/** Everything the gate needs; built by the Android glue from state it already holds. */
data class DeckContext(
    val ui: UiMode,
    val cam: CameraReport,
    val cameraCount: Int,
    val recording: Boolean = false,
    val proField: ProField = ProField.Iso
)

/** Capability gate: no control is offered that existing Camera2 support does not back. */
object ControlGate {
    fun check(a: CameraAction, c: DeckContext): Gate =
        captureGate(a, c) ?: parameterGate(a, c) ?: navigationGate(a, c)

    private fun captureGate(a: CameraAction, c: DeckContext): Gate? = when (a) {
        CameraAction.Shutter -> need(c.ui in ActionRouter.available(c.cam), "No ${c.ui.label} here")

        CameraAction.VideoToggle ->
            need(UiMode.Video in ActionRouter.available(c.cam), "No recordable video size")

        CameraAction.PauseToggle ->
            need(c.ui == UiMode.Video && c.recording, "Only while recording")

        CameraAction.QualityNext -> need(
            c.ui == UiMode.Video && !c.recording &&
                VideoPlanner.options(c.cam.camera.videoSizes).size > 1,
            "Only one video size"
        )

        CameraAction.NextCaptureMode -> need(
            c.ui == UiMode.Photo && photoPaths(c.cam) > 1,
            "Only one photo path"
        )

        CameraAction.CompareToggle -> need(c.ui == UiMode.Photo, "Photo only")

        else -> null
    }

    private fun parameterGate(a: CameraAction, c: DeckContext): Gate? = when (a) {
        CameraAction.ZoomIn, CameraAction.ZoomOut -> zoom(c)

        CameraAction.StepUp, CameraAction.StepDown ->
            if (c.ui == UiMode.Pro) pro(c.proField, c) else zoom(c)

        CameraAction.SelectWb -> pro(ProField.Wb, c)

        CameraAction.SelectIso -> pro(ProField.Iso, c)

        CameraAction.SelectShutter -> pro(ProField.Shutter, c)

        CameraAction.ProAuto -> need(c.ui == UiMode.Pro, "Pro only")

        CameraAction.ExposureUp, CameraAction.ExposureDown ->
            if (c.ui == UiMode.Pro) pro(ProField.Ev, c) else need(hasEv(c.cam), "No EV control")

        CameraAction.Autofocus ->
            if (c.ui == UiMode.Pro) pro(ProField.Focus, c) else need(hasAf(c.cam), "Fixed focus")

        else -> null
    }

    private fun navigationGate(a: CameraAction, c: DeckContext): Gate = when (a) {
        CameraAction.SwitchCamera ->
            need(c.cameraCount > 1 && !c.recording, "Single camera or recording")

        CameraAction.NextMode, CameraAction.PrevMode ->
            need(ActionRouter.available(c.cam).size > 1 && !c.recording, "Single mode")

        else -> Gate.Open
    }

    private fun need(ok: Boolean, reason: String): Gate = if (ok) Gate.Open else Gate.Closed(reason)

    private fun zoom(c: DeckContext) = need(c.cam.zoomSteps.size > 1, "No zoom range")

    private fun hasEv(r: CameraReport) = r.camera.aeCompensationSteps?.let { it.hi > it.lo } == true

    private fun hasAf(r: CameraReport) = r.camera.minFocusDistance > 0f

    private fun photoPaths(r: CameraReport) = listOf(
        CaptureMode.Auto,
        CaptureMode.HighRes,
        CaptureMode.Raw
    ).count { r.modes[it]?.ok == true }

    private fun pro(f: ProField, c: DeckContext): Gate = need(
        c.ui == UiMode.Pro && ProLimits.of(c.cam.camera).offers(f),
        "${f.label} not adjustable"
    )
}

/** One transient parameter strip: labels, the selected cell (-1 = none) and whether it is Auto. */
data class ParameterStrip(
    val title: String,
    val labels: List<String>,
    val selected: Int,
    val isAuto: Boolean
) {
    /** At most [max] cells, centred on the selection, so a long ladder never fills the screen. */
    fun window(max: Int = DeckPolicy.STRIP_MAX_CELLS): Pair<List<String>, Int> {
        if (labels.size <= max) return labels to selected
        val centre = if (selected < 0) 0 else selected
        val start = (centre - max / 2).coerceIn(0, labels.size - max)
        return labels.subList(start, start + max) to if (selected < 0) -1 else selected - start
    }
}

/** What the strips need from the live camera state; all values are read, never assumed. */
data class StripContext(
    val cam: CameraReport,
    val pro: ProState,
    val proField: ProField,
    val seed: AutoSeed,
    val zoom: Float,
    val aeSteps: Int
)

object ParameterStrips {
    private const val AUTO = "AUTO"

    /** Strip for one Pro field, or null when the camera does not offer that field. */
    fun forField(f: ProField, s: ProState, l: ProLimits): ParameterStrip? {
        if (!l.offers(f)) return null
        return when (f) {
            ProField.Iso -> ladder("ISO", s.iso, ProControls.isoLadder(l.iso!!), Int::toString)

            ProField.Shutter -> ladder(
                "Shutter",
                s.shutterNs,
                ProControls.shutterLadder(l.exposureNs!!),
                ProControls::shutterLabel
            )

            ProField.Focus -> ladder(
                "Focus",
                s.focusDiopters,
                ProControls.focusLadder(l.minFocusDiopters),
                ProControls::focusLabel
            )

            ProField.Ev -> ev(s.evSteps, l.evSteps!!, l.evStep)

            ProField.Wb -> {
                val c = ProControls.wbChoices(l)
                ParameterStrip("WB", c.map { it.label }, c.indexOf(s.wb), s.wb == WbPreset.Auto)
            }
        }
    }

    private fun <T : Any> ladder(
        title: String,
        current: T?,
        values: List<T>,
        label: (T) -> String
    ): ParameterStrip {
        val labels = listOf(AUTO) + values.map(label)
        val sel = if (current == null) 0 else 1 + values.indexOf(current).coerceAtLeast(0)
        return ParameterStrip(title, labels, sel, current == null)
    }

    fun ev(steps: Int, range: IntBounds, stepSize: Float): ParameterStrip {
        val values = (range.lo..range.hi).toList()
        return ParameterStrip(
            "EV",
            values.map { EvLabels.of(it, stepSize) },
            values.indexOf(steps.coerceIn(range.lo, range.hi)),
            steps == 0
        )
    }

    /** Zoom strip over the stops the camera really offers; null for a fixed-zoom camera. */
    fun zoom(current: Float, cam: CameraReport): ParameterStrip? {
        val stops = cam.zoomSteps
        if (stops.size <= 1) return null
        val idx = stops.indices.minBy { kotlin.math.abs(stops[it] - current) }
        return ParameterStrip("Zoom", stops.map { zoomLabel(it) }, idx, false)
    }

    private fun zoomLabel(z: Float) = if (z ==
        Math.floor(z.toDouble()).toFloat()
    ) {
        "${z.toInt()}x"
    } else {
        "%.1fx".format(java.util.Locale.US, z)
    }

    /** The strip a routed command should show after it ran, or null when it shows none. */
    fun after(cmd: Cmd, c: StripContext): ParameterStrip? {
        val l = ProLimits.of(c.cam.camera)
        return when (cmd) {
            is Cmd.ProSelect -> forField(cmd.field, c.pro, l)
            is Cmd.ProStep -> forField(c.proField, c.pro, l)
            is Cmd.ProAuto -> forField(c.proField, c.pro, l)
            is Cmd.Act -> actStrip(cmd.action, c)
            else -> null
        }
    }

    private fun actStrip(a: CameraAction, c: StripContext): ParameterStrip? = when (a) {
        CameraAction.ZoomIn, CameraAction.ZoomOut -> zoom(c.zoom, c.cam)

        CameraAction.ExposureUp, CameraAction.ExposureDown ->
            c.cam.camera.aeCompensationSteps
                ?.takeIf { it.hi > it.lo }
                ?.let { ev(c.aeSteps, it, c.cam.camera.aeCompensationStep) }

        else -> null
    }
}

/** Transient overlay state: at most one strip, an optional legend, and the touch-chrome toggle. */
data class DeckState(
    val strip: ParameterStrip? = null,
    val stripShownAtMs: Long = 0L,
    val legendVisible: Boolean = false,
    val touchChromeRevealed: Boolean = false
)

/** What a Back press should do once the deck has had its say. */
sealed interface BackResult {
    data class Consumed(val state: DeckState) : BackResult
    data object PassThrough : BackResult
}

/** What the activity should do with one control press. */
data class Dispatch(val deck: DeckState, val route: Boolean, val status: String? = null)

object ControlDeck {
    /**
     * The single decision point for every control, key or touch: legend/Back handling first, then
     * the capability gate. [Dispatch.route] says whether the action should reach the router.
     */
    fun dispatch(s: DeckState, a: CameraAction, c: DeckContext): Dispatch {
        val back = if (a == CameraAction.Back) onBack(s) else BackResult.PassThrough
        val next = onAction(s, a)
        val gate = ControlGate.check(a, c)
        return when {
            back is BackResult.Consumed -> Dispatch(back.state, false)

            a == CameraAction.ToggleLegend -> Dispatch(next, false)

            gate is Gate.Closed ->
                Dispatch(next, false, "${ControlLegend.label(a, c.ui)}: ${gate.reason}")

            else -> Dispatch(next, true)
        }
    }

    fun showStrip(s: DeckState, strip: ParameterStrip?, nowMs: Long): DeckState =
        if (strip == null) s else s.copy(strip = strip, stripShownAtMs = nowMs)

    /** Strips disappear on their own after [DeckPolicy.STRIP_TIMEOUT_MS] of no interaction. */
    fun tick(s: DeckState, nowMs: Long): DeckState =
        if (s.strip != null && nowMs - s.stripShownAtMs >= DeckPolicy.STRIP_TIMEOUT_MS) {
            s.copy(strip = null)
        } else {
            s
        }

    fun dismissStrip(s: DeckState): DeckState = s.copy(strip = null)

    fun toggleLegend(s: DeckState): DeckState = s.copy(legendVisible = !s.legendVisible)

    fun dismissLegend(s: DeckState): DeckState = s.copy(legendVisible = false)

    fun toggleTouchChrome(s: DeckState): DeckState =
        s.copy(touchChromeRevealed = !s.touchChromeRevealed)

    /**
     * Any action other than the help toggle closes the legend without being swallowed, so the
     * legend can never trap input.
     */
    fun onAction(s: DeckState, a: CameraAction): DeckState = when {
        a == CameraAction.ToggleLegend -> toggleLegend(s)
        s.legendVisible -> dismissLegend(s)
        else -> s
    }

    /** Back closes the legend first, then an open strip, and only then leaves the camera. */
    fun onBack(s: DeckState): BackResult = when {
        s.legendVisible -> BackResult.Consumed(dismissLegend(s))
        s.strip != null -> BackResult.Consumed(dismissStrip(s))
        else -> BackResult.PassThrough
    }
}
