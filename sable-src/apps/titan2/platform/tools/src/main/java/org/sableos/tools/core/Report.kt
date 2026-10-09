package org.sableos.tools.core

enum class Sensitivity {
    /** Device facts with identifiers masked: included by default. */
    STANDARD,

    /** Can reveal personal habits or addresses: only when the user selects it explicitly. */
    SENSITIVE
}

enum class ReportCategory(
    val title: String,
    val sensitivity: Sensitivity,
    val warning: String? = null
) {
    DEVICE("Device, build and security", Sensitivity.STANDARD),
    INPUT("Keyboard and input", Sensitivity.STANDARD),
    DISPLAY("Display geometry", Sensitivity.STANDARD),
    SENSORS("Sensors", Sensitivity.STANDARD),
    CAMERA("Camera capabilities", Sensitivity.STANDARD),
    RADIO("Radio and SIM state", Sensitivity.STANDARD),
    STORAGE("Storage summary", Sensitivity.STANDARD),
    CAPABILITIES("Tools capability status", Sensitivity.STANDARD),
    NETWORK(
        "Network addresses, DNS and VPN",
        Sensitivity.SENSITIVE,
        "Includes partially masked IP and DNS addresses, which can hint at your network or location."
    ),
    APPS(
        "Installed app list",
        Sensitivity.SENSITIVE,
        "Your app list can reveal your bank, health, dating or work apps."
    ),
    SESSION_LOG(
        "Diagnostic session log",
        Sensitivity.SENSITIVE,
        "Key events captured in this session can contain anything you typed while the key viewer was open."
    )
}

/**
 * Never collected by Sable Tools, so a report cannot contain them whatever is selected (DESIGN-KF-C
 * "Default report excludes"). Shown on the export screen so the user knows.
 */
val NEVER_INCLUDED = listOf(
    "message bodies",
    "contact contents",
    "photos and media",
    "authentication secrets",
    "full private logs",
    "precise location history",
    "provider credentials"
)

data class ReportRow(val key: String, val value: String)

data class ReportSection(val category: ReportCategory, val title: String, val rows: List<ReportRow>)

/** What the export screen shows before anything leaves the app. */
data class ExportPlan(
    val included: List<ReportCategory>,
    val warnings: List<String>,
    val notSelected: List<ReportCategory>,
    val neverIncluded: List<String> = NEVER_INCLUDED
) {
    val isEmpty: Boolean get() = included.isEmpty()
}

object ReportPolicy {
    /** Preset selection for each Reports entry. Presets never pre-select a sensitive category. */
    fun preset(tool: Tool): Set<ReportCategory> = when (tool) {
        Tool.REPORT_DEVICE -> setOf(
            ReportCategory.DEVICE,
            ReportCategory.DISPLAY,
            ReportCategory.STORAGE,
            ReportCategory.SENSORS
        )

        Tool.REPORT_INPUT -> setOf(
            ReportCategory.DEVICE,
            ReportCategory.INPUT,
            ReportCategory.DISPLAY
        )

        Tool.REPORT_RADIO -> setOf(ReportCategory.DEVICE, ReportCategory.RADIO)

        Tool.REPORT_APPS -> setOf(ReportCategory.DEVICE)

        else -> ReportCategory.entries.filter { it.sensitivity == Sensitivity.STANDARD }.toSet() -
            ReportCategory.CAPABILITIES
    }

    /** Categories a preset offers for opt-in (shown unselected with their warning). */
    fun offered(tool: Tool): List<ReportCategory> = when (tool) {
        Tool.REPORT_RADIO -> listOf(ReportCategory.NETWORK)
        Tool.REPORT_APPS -> listOf(ReportCategory.APPS)
        Tool.REPORT_INPUT -> listOf(ReportCategory.SESSION_LOG)
        else -> emptyList()
    }

    fun plan(selected: Set<ReportCategory>): ExportPlan {
        val included = ReportCategory.entries.filter { it in selected }
        return ExportPlan(
            included = included,
            warnings = included.mapNotNull { it.warning },
            notSelected = ReportCategory.entries.filter {
                it !in selected &&
                    it.sensitivity == Sensitivity.SENSITIVE
            }
        )
    }

    /**
     * Renders only the sections whose category is selected (a caller cannot leak an unselected section by passing
     * it in), with every value passed through [Redactor].
     */
    fun render(
        header: String,
        profileLine: String,
        selected: Set<ReportCategory>,
        sections: List<ReportSection>
    ): String {
        val plan = plan(selected)
        return buildString {
            appendLine(header)
            appendLine(profileLine)
            appendLine(
                "Read-only report from Sable Tools. Identifiers are masked. Review before sharing."
            )
            appendLine(
                "Included: " + plan.included.joinToString(", ") {
                    it.title
                }.ifEmpty { "nothing" }
            )
            appendLine("Never included: " + plan.neverIncluded.joinToString(", "))
            sections.filter { it.category in selected }.forEach { s ->
                appendLine()
                appendLine("## ${Redactor.text(s.title)}")
                s.rows.forEach { r ->
                    appendLine("${r.key}: ${Redactor.text(r.value).ifBlank { "-" }}")
                }
            }
        }
    }
}

/**
 * Masks identifiers in any free text before it is shown in a report or copied. Applied to every value, not only the
 * ones a caller remembered to mask.
 */
object Redactor {
    private const val VISIBLE_TAIL = 4
    private const val OCTET_MAX = 255
    private const val FULLY_MASKED_MAX_LENGTH = 6

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val MAC = Regex("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")
    private val SECRET =
        Regex(
            "(?i)\\b(password|passwd|passphrase|pin|puk|token|secret|psk|api[_-]?key)(\\s*[:=]\\s*)\\S+"
        )
    private val COORDS = Regex("-?\\d{1,3}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}")
    private val PHONE = Regex("\\+\\d[\\d ()-]{6,}\\d")
    private val IPV4 = Regex("\\b(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b")
    private val IPV6 =
        Regex("\\b([0-9A-Fa-f]{1,4}):([0-9A-Fa-f]{1,4})((?::[0-9A-Fa-f]{0,4}){2,7})(%\\w+)?")
    private val LONG_DIGITS = Regex("\\d{11,}")

    /** Keeps the last 4 characters of a long identifier (ICCID, IMSI, IMEI, number); short values are fully masked. */
    fun id(s: String?): String {
        val v = s?.trim().orEmpty()
        if (v.isEmpty()) return "-"
        return if (v.length <=
            FULLY_MASKED_MAX_LENGTH
        ) {
            "*".repeat(v.length)
        } else {
            "*".repeat(v.length - VISIBLE_TAIL) +
                v.takeLast(VISIBLE_TAIL)
        }
    }

    fun text(s: String): String {
        var t = s
        t = SECRET.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + "[redacted]" }
        t = EMAIL.replace(t, "[email]")
        t = MAC.replace(t, "[mac]")
        t = COORDS.replace(t, "[location]")
        t = PHONE.replace(t, "[phone]")
        t = IPV6.replace(t) { m -> "${m.groupValues[1]}:${m.groupValues[2]}:…" }
        t = IPV4.replace(t) { m ->
            val parts = m.groupValues.drop(1)
            // Versions like 16.0.0.1 are rare in values; masking the host octet is the safe direction anyway.
            if (parts.all { it.toInt() <= OCTET_MAX }) {
                "${parts[0]}.${parts[1]}.${parts[2]}.*"
            } else {
                m.value
            }
        }
        t =
            LONG_DIGITS.replace(t) { m ->
                "*".repeat(m.value.length - VISIBLE_TAIL) +
                    m.value.takeLast(VISIBLE_TAIL)
            }
        return t
    }
}
