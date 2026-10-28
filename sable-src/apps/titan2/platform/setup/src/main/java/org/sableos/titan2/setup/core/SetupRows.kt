package org.sableos.titan2.setup.core

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
    private const val HOME_TITLE = "Home app"

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
    const val RADIO_DETAIL =
        "Read-only SIM and network diagnostics; IMS registration visibility requires canonical IR-007"

    fun build(snapshot: Snapshot, targets: (Action) -> TargetStatus): List<Item> = listOf(
        Item(
            "style",
            "Home screen style",
            "Sable Start in the BlackBerry 10 or BlackBerry 7 style",
            Readiness.Informational,
            Action.HomeStyle
        ),
        home(snapshot.home),
        keyboard(snapshot.keyboard),
        Item(
            "ime_add",
            "Add a keyboard",
            "Turn on Gboard or another keyboard you installed, then choose it above",
            Readiness.Informational,
            Action.KeyboardSettings
        ),
        Item(
            "defaults",
            "Default apps",
            "Browser, phone, messages and the other apps Android opens by default",
            Readiness.Informational,
            Action.DefaultApps
        ),
        defaultApp("dialer", "Phone app", snapshot.dialer),
        defaultApp("sms", "Messages app", snapshot.sms),
        Item(
            "display",
            "Display compatibility",
            "Open to choose a screen profile for apps that do not fit",
            Readiness.Informational,
            Action.DisplayCompat
        ),
        Item(
            "radio",
            "Radio diagnostics",
            RADIO_DETAIL,
            Readiness.Informational,
            Action.RadioDiag
        ),
        Item(
            "network",
            "Network and SIM settings",
            "Opens Android Settings",
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
                HOME_TITLE,
                "In use: ${displayName(o.text)} · choose Sable Start or another launcher",
                Readiness.Informational,
                Action.HomeSettings
            )

        Observed.Absent ->
            Item(
                "home",
                HOME_TITLE,
                "No home app resolved",
                Readiness.Informational,
                Action.HomeSettings
            )

        Observed.Failed ->
            Item(
                "home",
                HOME_TITLE,
                "Could not read the home app",
                Readiness.Unknown,
                Action.HomeSettings
            )
    }

    /**
     * Observation only: default-keyboard configuration stays canonical (IR-005). Any keyboard is a
     * valid choice (Sable keyboard, Gboard or another), so a readable value is informational.
     */
    private fun keyboard(o: Observed): Item {
        val title = "Keyboard"
        return when (o) {
            is Observed.Value ->
                Item(
                    "ime",
                    title,
                    "In use: ${displayName(o.text.substringBefore('/'))}",
                    Readiness.Informational,
                    Action.KeyboardPicker
                )

            Observed.Absent ->
                Item(
                    "ime",
                    title,
                    "No keyboard value reported",
                    Readiness.Unknown,
                    Action.KeyboardPicker
                )

            Observed.Failed ->
                Item(
                    "ime",
                    title,
                    "Could not read the keyboard setting",
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
                "A default app is configured: ${o.text} " + "(not checked against a specific app)",
                Readiness.Satisfied,
                Action.DefaultApps
            )

        Observed.Absent ->
            Item(
                id,
                title,
                "No default app is configured",
                Readiness.ActionRequired,
                Action.DefaultApps
            )

        Observed.Failed ->
            Item(
                id,
                title,
                "Could not read the default app",
                Readiness.Unknown,
                Action.DefaultApps
            )
    }

    /** A row never offers a button unless its destination resolved successfully. */
    fun applyTarget(item: Item, status: TargetStatus): Item = when {
        item.action == Action.None || status == TargetStatus.Resolvable -> item

        status == TargetStatus.Unverifiable ->
            item.copy(
                detail = "${item.detail} · could not verify that this screen is available",
                action = Action.None
            )

        item.state == Readiness.Satisfied || item.state == Readiness.Unknown ->
            item.copy(action = Action.None)

        item.state == Readiness.ActionRequired ->
            item.copy(
                state = Readiness.Unavailable,
                detail =
                    "${item.detail} · needs attention, " +
                        "but its screen is not available on this device",
                action = Action.None
            )

        else ->
            item.copy(
                state = Readiness.Unavailable,
                detail = "Not available on this device",
                action = Action.None
            )
    }

    private val CHOICES = setOf(Action.HomeStyle, Action.HomeSettings, Action.KeyboardPicker)

    /** Button text, or null when the row offers no action. */
    fun buttonLabel(item: Item): String? = when {
        item.action == Action.None || item.state == Readiness.Satisfied -> null
        item.state == Readiness.ActionRequired -> "Fix"
        item.action == Action.WeatherCity -> "Choose city"
        item.action in CHOICES -> "Choose"
        else -> "Open"
    }
}
