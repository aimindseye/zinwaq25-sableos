package org.sableos.titan2.camera.core

enum class CameraAction {
    Shutter,
    Autofocus,

    /** Hold F / hold E: lock focus or exposure until the next hold. Not in Pro (manual there). */
    AfLockToggle,
    AeLockToggle,
    VideoToggle,
    ZoomIn,
    ZoomOut,
    SwitchCamera,
    ExposureUp,
    ExposureDown,
    OpenLast,
    NextMode,
    PrevMode,
    CompareToggle,
    Back,

    /** Arrow Up/Down: zoom in Photo/Video, adjust the selected field in Pro. */
    StepUp,
    StepDown,

    /** Pro field selectors and reset. */
    SelectWb,
    SelectIso,
    SelectShutter,
    ProAuto,
    PauseToggle,
    NextCaptureMode,
    QualityNext,

    /** Show/hide the control legend (a dedicated help action, not a mode). */
    ToggleLegend
}

/**
 * A binding is a named key plus an optional Alt, pressed or held. Names are core-level, not
 * Android keycodes, so the map is JVM-testable. A held binding fires once the key has been down
 * for the platform long-press time; the plain binding of the same key then fires on release
 * instead of on press (see [HoldTracker]).
 */
data class Binding(val key: String, val alt: Boolean = false, val hold: Boolean = false)

/**
 * Standard bindings use only common keys (Space/Enter shutter per the org's standard binding
 * table). Device-specific keys (Func1/2, programmable key) are added through
 * [CameraDeviceProfile.keyOverrides] once captured; nothing is guessed.
 */
object CameraKeyMap {
    val standard: Map<Binding, CameraAction> = mapOf(
        Binding("space") to CameraAction.Shutter, Binding("enter") to CameraAction.Shutter,
        Binding("camera") to CameraAction.Shutter,
        Binding("f") to CameraAction.Autofocus,
        Binding("f", hold = true) to CameraAction.AfLockToggle,
        Binding("e", hold = true) to CameraAction.AeLockToggle,
        Binding("v") to CameraAction.VideoToggle,
        Binding("up") to CameraAction.StepUp, Binding("down") to CameraAction.StepDown,
        Binding("plus") to CameraAction.ZoomIn, Binding("minus") to CameraAction.ZoomOut,
        Binding("c") to CameraAction.SwitchCamera, Binding("tab") to CameraAction.SwitchCamera,
        Binding("e") to CameraAction.ExposureUp,
        Binding("e", alt = true) to CameraAction.ExposureDown,
        Binding("g") to CameraAction.OpenLast, Binding("b") to CameraAction.CompareToggle,
        Binding("right") to CameraAction.NextMode, Binding("left") to CameraAction.PrevMode,
        Binding("w") to CameraAction.SelectWb, Binding("i") to CameraAction.SelectIso,
        Binding("s") to CameraAction.SelectShutter,
        Binding("a") to CameraAction.ProAuto, Binding("p") to CameraAction.PauseToggle,
        Binding("m") to CameraAction.NextCaptureMode,
        Binding("q") to CameraAction.QualityNext,
        Binding("h") to CameraAction.ToggleLegend, Binding("slash") to CameraAction.ToggleLegend,
        Binding("escape") to CameraAction.Back, Binding("back") to CameraAction.Back
    )

    fun resolve(
        key: String,
        alt: Boolean,
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown,
        hold: Boolean = false
    ): CameraAction? {
        val b = Binding(key.lowercase(), alt, hold)
        return profile.keyOverrides[b] ?: standard[b]
    }

    fun resolve(b: Binding, profile: CameraDeviceProfile = CameraDeviceProfile.Unknown) =
        resolve(b.key, b.alt, profile, b.hold)

    /** True when [key] (with [alt]) has a held meaning, so its press must wait for release. */
    fun hasHold(
        key: String,
        alt: Boolean,
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown
    ): Boolean = resolve(key, alt, profile, hold = true) != null

    /** Bindings for [a] under the standard map (touch/legend parity checks). */
    fun keys(a: CameraAction): List<Binding> = standard.filterValues { it == a }.keys.toList()

    /** Actions each profile can reach; used by tests/validation to prove nothing in the required list is unbound. */
    fun boundActions(
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown
    ): Set<CameraAction> = (standard + profile.keyOverrides).values.toSet()
}

/**
 * Keys of the quick review. It is a separate layer over the viewfinder, so Left/Right, S and F mean
 * something else here; the viewfinder map is untouched. While a delete is being confirmed only
 * [confirm] applies.
 */
object ReviewKeyMap {
    val browse: Map<Binding, ReviewAction> = mapOf(
        Binding("left") to ReviewAction.Older, Binding("right") to ReviewAction.Newer,
        Binding("backspace") to ReviewAction.Delete, Binding("delete") to ReviewAction.Delete,
        Binding("s") to ReviewAction.Share,
        Binding("f") to ReviewAction.ShowInFiles,
        Binding("enter") to ReviewAction.Open,
        Binding("escape") to ReviewAction.Close, Binding("back") to ReviewAction.Close,
        Binding("g") to ReviewAction.Close
    )

    /** Deleting needs Enter or Y; Del again does not confirm, so a double press cannot delete. */
    val confirm: Map<Binding, ReviewAction> = mapOf(
        Binding("enter") to ReviewAction.Confirm,
        Binding("y") to ReviewAction.Confirm,
        Binding("n") to ReviewAction.Cancel,
        Binding("escape") to ReviewAction.Cancel,
        Binding("back") to ReviewAction.Cancel
    )

    fun resolve(key: String, alt: Boolean, confirming: Boolean): ReviewAction? =
        (if (confirming) confirm else browse)[Binding(key.lowercase(), alt)]

    fun keys(a: ReviewAction, confirming: Boolean): List<Binding> =
        (if (confirming) confirm else browse).filterValues { it == a }.keys.toList()
}

/** What one key-down means for a key that has both a press and a hold meaning. */
enum class HoldStep {
    /** First down: wait, the press fires on release unless the key is held. */
    Wait,

    /** Held past the long-press time: run the hold action now. */
    Hold,

    /** Further auto-repeats of a key already handled: nothing. */
    Ignore
}

/**
 * Tells a press from a hold for keys such as F and E. Android reports a held key as auto-repeated
 * key-downs; the first repeat arrives after the long-press timeout. Pure, so it is unit-tested.
 */
class HoldTracker {
    private enum class Phase { Pending, Held, Refused }

    private val down = mutableMapOf<String, Phase>()

    fun down(key: String, repeatCount: Int): HoldStep = when {
        repeatCount == 0 -> {
            down[key] = Phase.Pending
            HoldStep.Wait
        }

        down[key] == Phase.Pending -> {
            down[key] = Phase.Held
            HoldStep.Hold
        }

        else -> HoldStep.Ignore
    }

    /** The hold could not run (gated off): the release fires the ordinary press instead. */
    fun holdRefused(key: String) {
        if (key in down) down[key] = Phase.Refused
    }

    /** Key released: true when the ordinary press action should run now. */
    fun up(key: String): Boolean {
        val phase = down.remove(key)
        return phase == Phase.Pending || phase == Phase.Refused
    }

    fun clear() = down.clear()
}
