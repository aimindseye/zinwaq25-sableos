package org.sableos.tools.core

import org.sableos.tools.core.ToolsText.t

/** The three DESIGN-KF-C destinations. Recents/favorites are a home row, not a fourth domain. */
enum class Section(private val titleKey: String) {
    UTILITIES("utilities"),
    DIAGNOSTICS("diagnostics"),
    REPORTS("reports");

    val title: String get() = t(titleKey)
}

/** How a tool interprets the local single-letter commands (C/R/H). */
enum class ToolKind {
    /** Live sensor reading: H holds, R resets, C calibrates when the capability is calibratable. */
    MEASUREMENT,

    /** A screen with actions but no reading (flashlight, remote). */
    ACTION,

    /** Read-only information list. */
    INFO,

    /** Captures text: every key belongs to the text field; no single-letter commands. */
    TEXT_TEST,

    /** Captures raw key events (the key viewer): keys are data, not commands. */
    KEY_CAPTURE,

    /** Report builder. */
    REPORT
}

/** Runtime permission names, as plain strings so this file stays free of android.*. */
object Perm {
    const val CAMERA = "android.permission.CAMERA"
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"

    /** Android 12+ requires COARSE to be requested together with FINE. */
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
}

enum class Tool(
    val section: Section,
    private val titleKey: String,
    val kind: ToolKind,
    /** Null: no hardware gate (always listed in its section, subject to [developerOnly]). */
    val capability: Capability? = null,
    /** Requested only when the tool is opened (PERMISSION_REQUEST_AT_FEATURE_USE). */
    val permissions: List<String> = emptyList(),
    /** Extra words for type-to-filter and Sable Command search. */
    val keywords: List<String> = emptyList()
) {
    // Utilities
    COMPASS(
        Section.UTILITIES,
        "compass",
        ToolKind.MEASUREMENT,
        Capability.COMPASS,
        keywords = listOf("heading", "north")
    ),
    BUBBLE_LEVEL(
        Section.UTILITIES,
        "bubble_level",
        ToolKind.MEASUREMENT,
        Capability.BUBBLE_LEVEL,
        keywords = listOf("spirit", "tilt")
    ),
    PLUMB_BOB(
        Section.UTILITIES,
        "plumb_bob",
        ToolKind.MEASUREMENT,
        Capability.PLUMB_BOB,
        keywords = listOf("vertical")
    ),
    PROTRACTOR(
        Section.UTILITIES,
        "protractor",
        ToolKind.MEASUREMENT,
        Capability.PROTRACTOR,
        keywords = listOf("angle")
    ),
    PICTURE_HANGING(
        Section.UTILITIES,
        "picture_hanging",
        ToolKind.MEASUREMENT,
        Capability.PICTURE_HANGING,
        keywords = listOf("level guide", "frame")
    ),
    FLASHLIGHT(
        Section.UTILITIES,
        "flashlight",
        ToolKind.ACTION,
        Capability.FLASHLIGHT,
        keywords = listOf("torch", "light")
    ),
    MAGNIFIER(
        Section.UTILITIES,
        "magnifier",
        ToolKind.ACTION,
        Capability.MAGNIFIER,
        listOf(Perm.CAMERA),
        keywords = listOf("zoom", "camera")
    ),
    HEIGHT_ESTIMATE(
        Section.UTILITIES,
        "height_estimate",
        ToolKind.MEASUREMENT,
        Capability.HEIGHT_ESTIMATE,
        keywords = listOf("measure", "tall")
    ),
    NOISE_METER(
        Section.UTILITIES,
        "noise_meter",
        ToolKind.MEASUREMENT,
        Capability.NOISE_METER,
        listOf(Perm.RECORD_AUDIO),
        keywords = listOf("decibel", "db", "sound")
    ),
    SPEEDOMETER(
        Section.UTILITIES,
        "speedometer",
        ToolKind.MEASUREMENT,
        Capability.SPEEDOMETER,
        listOf(Perm.FINE_LOCATION, Perm.COARSE_LOCATION),
        keywords = listOf("speed", "gps")
    ),
    PEDOMETER(
        Section.UTILITIES,
        "pedometer",
        ToolKind.MEASUREMENT,
        Capability.PEDOMETER,
        listOf(Perm.ACTIVITY_RECOGNITION),
        keywords = listOf("steps")
    ),
    IR_REMOTE(
        Section.UTILITIES,
        "ir_remote",
        ToolKind.ACTION,
        Capability.IR_REMOTE,
        keywords = listOf("infrared", "tv", "remote")
    ),

    // Diagnostics
    DEVICE_IDENTITY(
        Section.DIAGNOSTICS,
        "device_build_and_security",
        ToolKind.INFO,
        keywords = listOf("about", "fingerprint", "patch")
    ),
    KEY_VIEWER(
        Section.DIAGNOSTICS,
        "keyboard_event_viewer",
        ToolKind.KEY_CAPTURE,
        keywords = listOf("key test", "keycode", "scancode", "modifier", "meta")
    ),
    KEYBOARD_PROFILE(
        Section.DIAGNOSTICS,
        "keyboard_layout_and_profile",
        ToolKind.INFO,
        keywords = listOf("keylayout", "kcm", "keymap")
    ),
    POINTER(
        Section.DIAGNOSTICS,
        "pointer_and_touch_surface",
        ToolKind.KEY_CAPTURE,
        keywords = listOf("trackpad", "touchpad", "touch panel")
    ),
    DISPLAY_INPUT(
        Section.DIAGNOSTICS,
        "display_and_input_geometry",
        ToolKind.INFO,
        keywords = listOf("lcd", "density", "screen")
    ),
    CAMERA_REPORT(
        Section.DIAGNOSTICS,
        "camera_capabilities",
        ToolKind.INFO,
        keywords = listOf("camera")
    ),
    SENSORS(
        Section.DIAGNOSTICS,
        "sensor_inventory",
        ToolKind.INFO,
        keywords = listOf("gravity", "gyro", "proximity")
    ),
    RADIO(
        Section.DIAGNOSTICS,
        "radio_ims_and_fm_status",
        ToolKind.INFO,
        keywords = listOf("sim", "volte", "ims", "rf information", "fm")
    ),
    NETWORK(
        Section.DIAGNOSTICS,
        "network_ip_dns_and_vpn",
        ToolKind.INFO,
        keywords = listOf("wifi", "ip", "dns", "vpn")
    ),
    STORAGE(
        Section.DIAGNOSTICS,
        "storage",
        ToolKind.INFO,
        keywords = listOf("disk", "slot", "partition")
    ),
    APPS(
        Section.DIAGNOSTICS,
        "installed_apps",
        ToolKind.INFO,
        keywords = listOf("packages", "inventory")
    ),
    TEXT_ENTRY(
        Section.DIAGNOSTICS,
        "critical_text_entry_test",
        ToolKind.TEXT_TEST,
        keywords = listOf("pin", "password", "pairing code")
    ),
    ATTENTION(
        Section.DIAGNOSTICS,
        "attention_and_subscreen_status",
        ToolKind.INFO,
        keywords = listOf("vibrator", "kb light", "notification light", "subscreen")
    ),
    HARDWARE_TESTS(
        Section.DIAGNOSTICS,
        "hardware_test_categories",
        ToolKind.INFO,
        keywords = listOf("factory test", "hardware test", "single test")
    ),
    CAPABILITIES(
        Section.DIAGNOSTICS,
        "capability_status_2",
        ToolKind.INFO,
        keywords = listOf("profile", "unsupported")
    ),
    FACTORY_BRIDGE(
        Section.DIAGNOSTICS,
        "factory_test_bridge",
        ToolKind.INFO,
        Capability.FACTORY_TEST_BRIDGE,
        keywords = listOf("engineering", "3377")
    ),

    // Reports
    REPORT_DEVICE(Section.REPORTS, "device_report", ToolKind.REPORT),
    REPORT_INPUT(Section.REPORTS, "keyboard_and_input_report", ToolKind.REPORT),
    REPORT_RADIO(Section.REPORTS, "radio_and_network_report", ToolKind.REPORT),
    REPORT_APPS(Section.REPORTS, "app_inventory_report", ToolKind.REPORT),
    REPORT_CUSTOM(
        Section.REPORTS,
        "custom_report",
        ToolKind.REPORT,
        keywords = listOf("export", "share")
    ),
    BUGREPORT(
        Section.REPORTS,
        "system_bug_report",
        ToolKind.REPORT,
        keywords = listOf("bugreport", "logs")
    );

    val title: String get() = t(titleKey)

    /** Developer/service tier only (raw capability status, factory bridge, bugreport hand-off). */
    val developerOnly: Boolean get() = this in DEVELOPER_TIER

    /** Text matched by type-to-filter: title plus keywords, lower case. */
    val searchText: String get() = (listOf(title) + keywords).joinToString(" ").lowercase()

    companion object {
        private val DEVELOPER_TIER by lazy { setOf(CAPABILITIES, FACTORY_BRIDGE, BUGREPORT) }

        fun byName(name: String?): Tool? = entries.firstOrNull { it.name == name }
    }
}

/** Just-in-time permission planning. Nothing is ever asked for at app launch. */
object PermissionPlan {
    /** NO_BROAD_PERMISSION_REQUEST_ON_FIRST_LAUNCH=YES: the launch set is always empty. */
    val atLaunch: List<String> = emptyList()

    /** The runtime permissions to request when [tool] is opened, given what is already granted. */
    fun missingFor(tool: Tool, granted: Set<String>): List<String> = tool.permissions.filterNot {
        it in
            granted
    }

    /**
     * A tool may degrade instead of failing when a permission is refused: the pedometer falls back to the
     * accelerometer. Everything else needs its permission to read anything.
     */
    fun usableWithout(tool: Tool, refused: List<String>): Boolean = refused.isEmpty() ||
        (tool == Tool.PEDOMETER && refused == listOf(Perm.ACTIVITY_RECOGNITION))
}
