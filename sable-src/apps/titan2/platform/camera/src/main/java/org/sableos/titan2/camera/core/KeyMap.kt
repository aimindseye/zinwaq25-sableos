package org.sableos.titan2.camera.core

enum class CameraAction {
    Shutter,
    Autofocus,
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
 * A binding is a named key plus an optional Alt. Names are core-level, not Android keycodes, so
 * the map is JVM-testable.
 */
data class Binding(val key: String, val alt: Boolean = false)

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
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown
    ): CameraAction? {
        val b = Binding(key.lowercase(), alt)
        return profile.keyOverrides[b] ?: standard[b]
    }

    /** Bindings for [a] under the standard map (touch/legend parity checks). */
    fun keys(a: CameraAction): List<Binding> = standard.filterValues { it == a }.keys.toList()

    /** Actions each profile can reach; used by tests/validation to prove nothing in the required list is unbound. */
    fun boundActions(
        profile: CameraDeviceProfile = CameraDeviceProfile.Unknown
    ): Set<CameraAction> = (standard + profile.keyOverrides).values.toSet()
}
