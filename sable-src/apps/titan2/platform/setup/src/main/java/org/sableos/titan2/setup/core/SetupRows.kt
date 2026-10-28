package org.sableos.titan2.setup.core

import org.sableos.titan2.setup.core.SetupText.t

/** Raw observations the Android side collected. The model turns them into rows without touching Android. */
data class Snapshot(
    val home: Observed,
    val keyboard: Observed,
    val dialer: Observed,
    val sms: Observed,
    /** Sable Weather's active city, read through its setup hand-off. */
    val weatherCity: Observed = Observed.Absent
)

object SetupRows {
    const val SABLE_KEYBOARD_PACKAGE = "org.sableos.titan2.keyboard"
    const val SABLE_START_PACKAGE = "org.sableos.launcher"

    /** Names for the packages this list commonly shows; anything else is shown as its package. */
    private val KNOWN_NAMES =
        mapOf(
            SABLE_START_PACKAGE to "Sable Start",
            "com.android.launcher3" to "Launcher3",
            SABLE_KEYBOARD_PACKAGE to "Sable keyboard",
            "com.google.android.inputmethod.latin" to "Gboard",
            "com.android.inputmethod.latin" to "Android keyboard (AOSP)",
            "com.touchtype.swiftkey" to "Microsoft SwiftKey"
        )

    fun displayName(pkg: String): String = KNOWN_NAMES[pkg] ?: pkg
    val RADIO_DETAIL get() = t("row_radio_detail")

    fun build(snapshot: Snapshot, targets: (Action) -> TargetStatus): List<Item> = listOf(
        Item(
            "style",
            t("row_style_title"),
            t("row_style_detail"),
            Readiness.Informational,
            Action.HomeStyle
        ),
        home(snapshot.home),
        keyboard(snapshot.keyboard),
        Item(
            "ime_add",
            t("row_ime_add_title"),
            t("row_ime_add_detail"),
            Readiness.Informational,
            Action.KeyboardSettings
        ),
        Item(
            "defaults",
            t("row_defaults_title"),
            t("row_defaults_detail"),
            Readiness.Informational,
            Action.DefaultApps
        ),
        defaultApp("dialer", t("row_dialer_title"), snapshot.dialer),
        defaultApp("sms", t("row_sms_title"), snapshot.sms),
        Item(
            "display",
            t("row_display_title"),
            t("row_display_detail"),
            Readiness.Informational,
            Action.DisplayCompat
        ),
        Item(
            "radio",
            t("row_radio_title"),
            RADIO_DETAIL,
            Readiness.Informational,
            Action.RadioDiag
        ),
        Item(
            "network",
            t("row_network_title"),
            t("row_network_detail"),
            Readiness.Informational,
            Action.NetworkSettings
        ),
        WeatherCityStep.row(snapshot.weatherCity)
    ).map { applyTarget(it, targets(it.action)) }

    /** HOME is owned by the canonical lane (IR-003); this row never judges it. */
    private fun home(o: Observed): Item = when (o) {
        is Observed.Value ->
            Item(
                "home",
                t("row_home_title"),
                t("row_home_in_use", displayName(o.text)),
                Readiness.Informational,
                Action.HomeSettings
            )

        Observed.Absent ->
            Item(
                "home",
                t("row_home_title"),
                t("row_home_none"),
                Readiness.Informational,
                Action.HomeSettings
            )

        Observed.Failed ->
            Item(
                "home",
                t("row_home_title"),
                t("row_home_failed"),
                Readiness.Unknown,
                Action.HomeSettings
            )
    }

    /**
     * Observation only: default-keyboard configuration stays canonical (IR-005). Any keyboard is a
     * valid choice (Sable keyboard, Gboard or another), so a readable value is informational.
     */
    private fun keyboard(o: Observed): Item {
        val title = t("row_keyboard_title")
        return when (o) {
            is Observed.Value ->
                Item(
                    "ime",
                    title,
                    t("row_keyboard_in_use", displayName(o.text.substringBefore('/'))),
                    Readiness.Informational,
                    Action.KeyboardPicker
                )

            Observed.Absent ->
                Item(
                    "ime",
                    title,
                    t("row_keyboard_none"),
                    Readiness.Unknown,
                    Action.KeyboardPicker
                )

            Observed.Failed ->
                Item(
                    "ime",
                    title,
                    t("row_keyboard_failed"),
                    Readiness.Unknown,
                    Action.KeyboardPicker
                )
        }
    }

    /** Only "a default app is configured" is tested here; no specific package is required. */
    private fun defaultApp(id: String, title: String, o: Observed): Item = when (o) {
        is Observed.Value ->
            Item(
                id,
                title,
                t("row_default_set", o.text),
                Readiness.Satisfied,
                Action.DefaultApps
            )

        Observed.Absent ->
            Item(
                id,
                title,
                t("row_default_none"),
                Readiness.ActionRequired,
                Action.DefaultApps
            )

        Observed.Failed ->
            Item(
                id,
                title,
                t("row_default_failed"),
                Readiness.Unknown,
                Action.DefaultApps
            )
    }

    /** A row never offers a button unless its destination resolved successfully. */
    fun applyTarget(item: Item, status: TargetStatus): Item = when {
        item.action == Action.None || status == TargetStatus.Resolvable -> item

        status == TargetStatus.Unverifiable ->
            item.copy(
                detail = t("row_unverifiable", item.detail),
                action = Action.None
            )

        item.state == Readiness.Satisfied || item.state == Readiness.Unknown ->
            item.copy(action = Action.None)

        item.state == Readiness.ActionRequired ->
            item.copy(
                state = Readiness.Unavailable,
                detail = t("row_needs_attention_unavailable", item.detail),
                action = Action.None
            )

        else ->
            item.copy(
                state = Readiness.Unavailable,
                detail = t("row_not_available"),
                action = Action.None
            )
    }

    private val CHOICES = setOf(Action.HomeStyle, Action.HomeSettings, Action.KeyboardPicker)

    /** Button text, or null when the row offers no action. */
    fun buttonLabel(item: Item): String? = when {
        item.action == Action.None || item.state == Readiness.Satisfied -> null
        item.state == Readiness.ActionRequired -> t("button_fix")
        item.action == Action.WeatherCity -> t("button_choose_city")
        item.action in CHOICES -> t("button_choose")
        else -> t("button_open")
    }
}
