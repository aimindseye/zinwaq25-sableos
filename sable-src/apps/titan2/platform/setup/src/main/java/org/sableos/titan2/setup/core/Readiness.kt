package org.sableos.titan2.setup.core

/**
 * What the helper can honestly say about one row. Only [Satisfied] and [ActionRequired] are conclusions
 * the evidence supports; [Unavailable] and [Unknown] are never folded into either of them.
 */
enum class Readiness {
    /** Read succeeded and the observed value meets the row's stated check. */
    Satisfied,

    /** Read succeeded and the observed value does not meet the check; the user can act on it. */
    ActionRequired,

    /** Shown for information only; never counted as done or not done. */
    Informational,

    /** The screen or app this row would open cannot be resolved on this device. */
    Unavailable,

    /** The read failed or returned nothing usable, so no conclusion is drawn. */
    Unknown
}

/** The outcome of reading one value. A failed read is its own case, never "absent". */
sealed interface Observed {
    data class Value(val text: String) : Observed

    /** The read worked and reported that nothing is set. */
    object Absent : Observed

    /** The read threw, was denied, or the service behind it is missing. */
    object Failed : Observed
}

/** Where a row's button goes. Every destination is an Android screen the user confirms in; nothing is mutated. */
enum class Action(val label: String) {
    None(""),
    HomeSettings("Home settings"),
    DefaultApps("Default apps settings"),
    KeyboardPicker("Keyboard picker"),
    DisplayCompat("Display compatibility"),
    RadioDiag("Radio diagnostics"),
    NetworkSettings("Network settings")
}

/** Whether the destination of an [Action] could be resolved before it was offered. */
enum class TargetStatus { Resolvable, Missing, Unverifiable }

data class Item(
    val id: String,
    val title: String,
    val detail: String,
    val state: Readiness,
    val action: Action = Action.None
)
