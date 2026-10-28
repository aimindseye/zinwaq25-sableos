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

/**
 * Where a row's button goes. Every destination is an Android screen the user confirms in; nothing
 * is mutated, except the Weather city the user explicitly picks, which Sable Weather stores.
 */
enum class Action(private val labelKey: String) {
    None(""),

    /** Sable Start's own chooser: BlackBerry 10 or BlackBerry 7 style. */
    HomeStyle("action_home_style"),
    HomeSettings("action_home_settings"),
    DefaultApps("action_default_apps"),
    KeyboardPicker("action_keyboard_picker"),

    /** Android's keyboard list, where Gboard or another installed keyboard is turned on. */
    KeyboardSettings("action_keyboard_settings"),
    DisplayCompat("action_display_compat"),
    RadioDiag("action_radio_diag"),
    NetworkSettings("action_network_settings"),

    /** Opens Setup's own Weather city step; the only action whose choice is written (by Sable Weather). */
    WeatherCity("action_weather_city");

    /** What the button opens, for a launch failure line; empty for [None]. */
    val label: String get() = if (labelKey.isEmpty()) "" else SetupText.t(labelKey)
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
