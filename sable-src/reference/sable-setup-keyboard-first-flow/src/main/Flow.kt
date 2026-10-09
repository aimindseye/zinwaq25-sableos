package org.sableos.reference.setupflow

enum class StepKind { Welcome, WifiList, WifiPassword, AccountSignIn, SimNetwork, KeyboardChoice }

enum class ControlKind { Button, TextField, ListItem, Switch }

data class Control(val id: String, val kind: ControlKind, val enabled: Boolean = true)

/**
 * One setup screen as the keyboard sees it: controls in focus order, which one is the primary (forward) action,
 * which one skips, and whether Back leaves the step. [skipId] and [canGoBack] are the exits that keep a step from
 * being a dead end.
 */
data class Step(
    val id: String,
    val kind: StepKind,
    val controls: List<Control>,
    val primaryId: String?,
    val skipId: String? = null,
    val canGoBack: Boolean = true
) {
    val hasExit: Boolean get() = skipId != null || canGoBack ||
        controls.any { it.id == primaryId && it.enabled }
}

/** What product policy allows to be skipped. SIM is not configurable: it can always be skipped. */
data class FlowPolicy(val wifiSkippable: Boolean = true, val accountSkippable: Boolean = true) {
    val simSkippable: Boolean get() = true
}

data class WifiNetwork(val ssid: String, val secured: Boolean)

object Steps {
    const val ADD_NETWORK = "wifi:add"
    const val SHOW_PASSWORD = "show_password"

    fun welcome() = Step(
        "welcome",
        StepKind.Welcome,
        listOf(Control("start", ControlKind.Button), Control("language", ControlKind.Button)),
        primaryId = "start",
        canGoBack = false
    )

    /** Every visible network is a keyboard-reachable list item, and adding a hidden network is always last. */
    fun wifiList(networks: List<WifiNetwork>, policy: FlowPolicy = FlowPolicy()) = Step(
        "wifi",
        StepKind.WifiList,
        networks.map { Control("wifi:${it.ssid}", ControlKind.ListItem) } +
            Control(ADD_NETWORK, ControlKind.ListItem) +
            Control("wifi_skip", ControlKind.Button, enabled = policy.wifiSkippable),
        primaryId = null,
        skipId = if (policy.wifiSkippable) "wifi_skip" else null
    )

    fun wifiPassword() = Step(
        "wifi_password",
        StepKind.WifiPassword,
        listOf(
            Control("password", ControlKind.TextField),
            Control(SHOW_PASSWORD, ControlKind.Switch),
            Control("connect", ControlKind.Button)
        ),
        primaryId = "connect"
    )

    fun account(policy: FlowPolicy = FlowPolicy()) = Step(
        "account",
        StepKind.AccountSignIn,
        listOf(
            Control("username", ControlKind.TextField),
            Control("account_password", ControlKind.TextField),
            Control("sign_in", ControlKind.Button),
            Control("account_skip", ControlKind.Button, enabled = policy.accountSkippable)
        ),
        primaryId = "sign_in",
        skipId = if (policy.accountSkippable) "account_skip" else null
    )

    fun sim() = Step(
        "sim",
        StepKind.SimNetwork,
        listOf(
            Control("sim_continue", ControlKind.Button),
            Control("sim_settings", ControlKind.Button)
        ),
        primaryId = "sim_continue",
        skipId = "sim_continue"
    )

    fun keyboardChoice() = Step(
        "keyboard",
        StepKind.KeyboardChoice,
        listOf(
            Control("keyboard_continue", ControlKind.Button),
            Control("keyboard_picker", ControlKind.Button),
            Control("keyboard_soft", ControlKind.Switch)
        ),
        primaryId = "keyboard_continue"
    )

    fun canonical(networks: List<WifiNetwork>, policy: FlowPolicy = FlowPolicy()): List<Step> =
        listOf(
            welcome(),
            keyboardChoice(),
            wifiList(networks, policy),
            wifiPassword(),
            account(policy),
            sim()
        )
}

/** Mobile network state as the SIM step may show it. Registration is never required to finish setup. */
enum class SimState { Registered, NotRegistered, NoSim, Unknown, Error }

object SimStep {
    const val SKIP_LABEL = "Continue without mobile network"

    /** Mobile-network trouble never blocks setup: every state, including a failed check, may continue. */
    fun canContinue(state: SimState): Boolean = when (state) {
        SimState.Registered,
        SimState.NotRegistered,
        SimState.NoSim,
        SimState.Unknown,
        SimState.Error -> true
    }

    fun message(state: SimState): String = when (state) {
        SimState.Registered -> "Mobile network is connected."

        SimState.NotRegistered ->
            "Mobile network is not connected yet. You can continue and set it up later."

        SimState.NoSim -> "No SIM card found. You can continue without one."

        SimState.Unknown -> "Mobile network status is not available. You can continue."

        SimState.Error -> "Could not check the mobile network. You can continue."
    }
}

/** Which keyboard setup the first-boot flow sees. Anything but a provisioned Sable keyboard falls back. */
enum class KeyboardAvailability { SableSelected, SableAvailableNotSelected, Unavailable, Unknown }

enum class TextKeyboard { Sable, StockSoftware }

data class TextEntryPlan(
    val keyboard: TextKeyboard,
    val showSoftOnFocus: Boolean,
    val deadEnd: Boolean
)

object TextEntry {
    /**
     * Sable keyboard provisioned: use it. Otherwise the normal Android software keyboard is used. A physical
     * keyboard never forces the soft one open; without a physical keyboard the soft one is always offered.
     */
    fun plan(availability: KeyboardAvailability, physicalKeyboard: Boolean): TextEntryPlan {
        val sable = availability == KeyboardAvailability.SableSelected
        val keyboard = if (sable) TextKeyboard.Sable else TextKeyboard.StockSoftware
        return TextEntryPlan(keyboard, showSoftOnFocus = !physicalKeyboard, deadEnd = false)
    }
}
