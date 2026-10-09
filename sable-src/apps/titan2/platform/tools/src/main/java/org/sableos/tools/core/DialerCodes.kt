package org.sableos.tools.core

/** HARDWARE_DIAGNOSTICS_AND_DIALER_CODES "Sable Diagnostics categories". */
enum class DiagCategory(val title: String) {
    DISPLAY("Display"),
    KEYBOARD_POINTER("Keyboard & pointer"),
    AUDIO("Audio"),
    SENSORS("Sensors"),
    RADIO("Radio"),
    POWER("Power"),
    ATTENTION("Attention"),
    ENGINEERING("Engineering")
}

/** What a factory-test item maps to in Sable Tools. */
sealed interface DiagTarget {
    /** A Sable-owned read-only diagnostic or utility. */
    data class Open(val tool: Tool) : DiagTarget

    /** Owned by another surface (Settings, Sable Media): Tools explains and may deep-link. */
    data class Elsewhere(val owner: String, val link: SettingsLink?) : DiagTarget

    /** Calibration/logging/aging: engineering-gated, never a one-tap action. */
    data class Gated(val why: String) : DiagTarget
}

/** One observed factory-test item (Titan 2 *#*#3377#*#* evidence) and its Sable mapping. */
data class FactoryItem(val name: String, val category: DiagCategory, val target: DiagTarget)

/**
 * Factory-test concepts mapped into Sable categories, and the dialer-code / search bridge. The raw factory surface
 * is never launched directly by default (RAW_FACTORY_TEST_DIRECT_LAUNCH=NO_BY_DEFAULT).
 */
object DialerCodes {
    const val FACTORY_TEST_CODE = "3377"
    const val FACTORY_TEST_DIAL = "*#*#3377#*#*"

    private val CALIBRATION = DiagTarget.Gated(
        "calibration changes device state (CALIBRATION_ACTIONS=GATED)"
    )
    private val LOGGING = DiagTarget.Gated("vendor logging (LOGGING_ACTIONS=GATED)")
    private val AGING = DiagTarget.Gated(
        "aging test stresses hardware (AGING_TEST_FROM_NORMAL_SETTINGS=NO_BY_DEFAULT)"
    )
    private val BATTERY = DiagTarget.Elsewhere("Settings > Battery", SettingsLink.BATTERY)

    val ITEMS: List<FactoryItem> = listOf(
        FactoryItem("LCD", DiagCategory.DISPLAY, DiagTarget.Open(Tool.DISPLAY_INPUT)),
        FactoryItem(
            "BackLED",
            DiagCategory.DISPLAY,
            DiagTarget.Elsewhere("Settings > Display", SettingsLink.DISPLAY)
        ),
        FactoryItem("TouchPanel", DiagCategory.KEYBOARD_POINTER, DiagTarget.Open(Tool.POINTER)),
        FactoryItem("TouchPad", DiagCategory.KEYBOARD_POINTER, DiagTarget.Open(Tool.POINTER)),
        FactoryItem("Key", DiagCategory.KEYBOARD_POINTER, DiagTarget.Open(Tool.KEY_VIEWER)),
        FactoryItem("KB light", DiagCategory.KEYBOARD_POINTER, DiagTarget.Open(Tool.ATTENTION)),
        FactoryItem(
            "LoudSpeaker",
            DiagCategory.AUDIO,
            DiagTarget.Elsewhere("Settings > Sound", SettingsLink.SOUND)
        ),
        FactoryItem(
            "Receiver",
            DiagCategory.AUDIO,
            DiagTarget.Elsewhere("Settings > Sound", SettingsLink.SOUND)
        ),
        FactoryItem("Microphone1", DiagCategory.AUDIO, DiagTarget.Open(Tool.NOISE_METER)),
        FactoryItem("Microphone2", DiagCategory.AUDIO, DiagTarget.Open(Tool.NOISE_METER)),
        FactoryItem("Smartpa calib", DiagCategory.AUDIO, CALIBRATION),
        FactoryItem("Gravity Sensor", DiagCategory.SENSORS, DiagTarget.Open(Tool.SENSORS)),
        FactoryItem("Gyro", DiagCategory.SENSORS, DiagTarget.Open(Tool.SENSORS)),
        FactoryItem("Compass", DiagCategory.SENSORS, DiagTarget.Open(Tool.COMPASS)),
        FactoryItem("Gravity Calibration", DiagCategory.SENSORS, CALIBRATION),
        FactoryItem("Distance Calibration", DiagCategory.SENSORS, CALIBRATION),
        FactoryItem("RF information", DiagCategory.RADIO, DiagTarget.Open(Tool.RADIO)),
        FactoryItem("Ygps", DiagCategory.RADIO, DiagTarget.Open(Tool.SPEEDOMETER)),
        FactoryItem("Power slow charge", DiagCategory.POWER, BATTERY),
        FactoryItem("Two lights flash", DiagCategory.ATTENTION, DiagTarget.Open(Tool.ATTENTION)),
        FactoryItem("Vibrator", DiagCategory.ATTENTION, DiagTarget.Open(Tool.ATTENTION)),
        FactoryItem("Mtklog", DiagCategory.ENGINEERING, LOGGING),
        FactoryItem("Aging Test", DiagCategory.ENGINEERING, AGING),
        FactoryItem(
            "Factory Test bridge",
            DiagCategory.ENGINEERING,
            DiagTarget.Open(Tool.FACTORY_BRIDGE)
        )
    )

    fun byCategory(): Map<DiagCategory, List<FactoryItem>> =
        DiagCategory.entries.associateWith { c ->
            ITEMS.filter { it.category == c }
        }

    /** Where a search/command query lands. */
    sealed interface QueryResult {
        data class Category(val tool: Tool) : QueryResult
        data class Bridge(val decision: BridgeDecision) : QueryResult
        data object NoMatch : QueryResult
    }

    private val FRIENDLY = mapOf(
        "factory test" to Tool.HARDWARE_TESTS,
        "hardware test" to Tool.HARDWARE_TESTS,
        "single test" to Tool.HARDWARE_TESTS,
        "key test" to Tool.KEY_VIEWER,
        "keyboard test" to Tool.KEY_VIEWER,
        "keyboard light" to Tool.ATTENTION,
        "kb light" to Tool.ATTENTION,
        "touchpad test" to Tool.POINTER,
        "text entry test" to Tool.TEXT_ENTRY
    )

    /** Strips spaces so "*#*# 3377 #*#*" from a search box still matches. */
    fun normalise(q: String): String = q.lowercase().filterNot { it.isWhitespace() }

    /** friendly query -> Sable Diagnostics category; raw dialer code query -> Diagnostics > Factory test bridge. */
    fun resolveQuery(query: String, bridge: BridgeDecision): QueryResult {
        val n = normalise(query)
        val friendly = FRIENDLY[query.trim().lowercase().replace(Regex("\\s+"), " ")]
        return when {
            n == FACTORY_TEST_DIAL || n == FACTORY_TEST_CODE -> QueryResult.Bridge(bridge)
            friendly != null -> QueryResult.Category(friendly)
            else -> QueryResult.NoMatch
        }
    }

    /** Whether the bridge opens, and if not the reason Settings/Tools must show instead of failing silently. */
    data class BridgeDecision(val opensBridge: Boolean, val reason: String)

    /**
     * The bridge screen is shown only in developer/service mode and only when the profile marks the factory test as
     * at least diagnostic-only. Otherwise the caller lands on Hardware test categories with [BridgeDecision.reason].
     */
    fun bridge(resolution: Resolution, developerMode: Boolean): BridgeDecision = when {
        resolution.declared == Declared.UNSUPPORTED ->
            BridgeDecision(
                false,
                "This device has no factory test surface. Sable hardware tests are shown instead."
            )

        resolution.visibility == Visibility.HIDDEN ->
            BridgeDecision(
                false,
                "The factory test bridge is not proven on this device profile. Sable hardware tests are shown instead."
            )

        !developerMode ->
            BridgeDecision(
                false,
                "The factory test bridge needs developer mode. Sable hardware tests are shown instead."
            )

        else -> BridgeDecision(
            true,
            "Engineering surface: calibration, logging and aging actions stay gated."
        )
    }

    /** The secret-code receiver answers only when Sable owns the code: never alongside a vendor handler. */
    fun receiverEnabled(vendorHandlerPresent: Boolean): Boolean = !vendorHandlerPresent

    /** Parses android_secret_code://NNNN. */
    fun secretCodeFromUri(uri: String?): String? =
        uri?.removePrefix("android_secret_code://")?.takeIf {
            it != uri && it.all(Char::isDigit) &&
                it.isNotEmpty()
        }
}
