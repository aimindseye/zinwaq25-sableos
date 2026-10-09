package org.sableos.tools.core

/** The kind of field a case uses, mapped to an Android inputType by the UI. */
enum class FieldKind { TEXT, NUMBER, NUMBER_PASSWORD, VISIBLE_PASSWORD }

/** A modifier a case must exercise; [metaMask] is the Android KeyEvent META_* bit. */
enum class Modifier(val metaMask: Int) {
    ALT(KeyCommands.META_ALT_ON),
    SYM(KeyCommands.META_SYM_ON),
    FN(KeyCommands.META_FUNCTION_ON)
}

data class TextEntryCase(
    val id: String,
    val title: String,
    val prompt: String,
    val field: FieldKind,
    /** Exact text required, or null when the case is judged from key events ([modifier]). */
    val expected: String? = null,
    val modifier: Modifier? = null,
    /** Shows the software keyboard instead of relying on the hardware keyboard. */
    val softwareKeyboard: Boolean = false
)

enum class CaseResult { NOT_RUN, PASS, FAIL }

/** One key event the field received while a case ran (meta state and produced character). */
data class TypedKey(val meta: Int, val unicode: Int)

/**
 * HARDWARE_DIAGNOSTICS_AND_DIALER_CODES "Critical text-entry test". Results record pass/fail only: the typed text is
 * never stored, logged or exported.
 */
object TextEntryTest {
    val CASES: List<TextEntryCase> = listOf(
        TextEntryCase("letters", "Letters", "Type: sable", FieldKind.TEXT, expected = "sable"),
        TextEntryCase("numbers", "Numbers", "Type: 2468", FieldKind.NUMBER, expected = "2468"),
        TextEntryCase(
            "symbols",
            "Symbols",
            "Type: @#\$%&*!?",
            FieldKind.TEXT,
            expected = "@#\$%&*!?"
        ),
        TextEntryCase(
            "alt",
            "Alt layer",
            "Hold or lock Alt and type any key printed with a second character",
            FieldKind.TEXT,
            modifier = Modifier.ALT
        ),
        TextEntryCase(
            "sym",
            "Sym",
            "Use Sym to enter any symbol",
            FieldKind.TEXT,
            modifier = Modifier.SYM
        ),
        TextEntryCase(
            "fn",
            "Fn",
            "Use Fn with any key that has an Fn character",
            FieldKind.TEXT,
            modifier = Modifier.FN
        ),
        TextEntryCase(
            "bt_pairing",
            "Bluetooth pairing code",
            "Simulated pairing dialog. Enter: 482915",
            FieldKind.NUMBER,
            expected = "482915"
        ),
        TextEntryCase(
            "wifi_password",
            "Wi-Fi password",
            "Simulated Wi-Fi password. Enter: Tr4il-mix#26!",
            FieldKind.VISIBLE_PASSWORD,
            expected = "Tr4il-mix#26!"
        ),
        TextEntryCase(
            "pin",
            "Lockscreen PIN",
            "Simulated PIN pad. Enter: 7391",
            FieldKind.NUMBER_PASSWORD,
            expected = "7391"
        ),
        TextEntryCase(
            "software_fallback",
            "Software keyboard fallback",
            "Use the on-screen keyboard to type: ok",
            FieldKind.TEXT,
            expected = "ok",
            softwareKeyboard = true
        )
    )

    fun check(case: TextEntryCase, entered: String, keys: List<TypedKey>): CaseResult {
        val expected = case.expected
        val m = case.modifier
        val pass = when {
            expected != null -> entered == expected

            m == null -> false

            else -> keys.filter { it.meta and m.metaMask != 0 && it.unicode > 0 }
                .map { String(Character.toChars(it.unicode)) }
                .any { entered.contains(it) }
        }
        return if (pass) CaseResult.PASS else CaseResult.FAIL
    }

    /** "7/10 PASS, 1 FAIL, 2 NOT_RUN" plus per-case lines; safe for reports (no typed text). */
    fun summary(results: Map<String, CaseResult>): List<ReportRow> {
        val rows = CASES.map { ReportRow(it.title, (results[it.id] ?: CaseResult.NOT_RUN).name) }
        val pass = rows.count { it.value == CaseResult.PASS.name }
        val fail = rows.count { it.value == CaseResult.FAIL.name }
        return listOf(
            ReportRow(
                "critical text entry",
                "$pass/${CASES.size} PASS, $fail FAIL, ${CASES.size - pass - fail} NOT_RUN"
            )
        ) +
            rows
    }
}

/** Curated SubScreen companion modes Sable Tools may offer (no arbitrary mirroring, no text entry). */
object SubscreenModes {
    data class Mode(val name: String, val needs: Capability?)

    val CURATED = listOf(
        Mode("Compass", Capability.COMPASS),
        Mode("Media quick control", null),
        Mode("Safe IR quick control", Capability.IR_REMOTE),
        Mode("Camera / magnifier preview", Capability.MAGNIFIER),
        Mode("Battery and device status", null)
    )

    /** Modes available on this profile: none at all without a proven SubScreen. */
    fun available(res: Map<Capability, Resolution>): List<String> {
        if (res[Capability.SUBSCREEN_COMPANION]?.visibility !=
            Visibility.VISIBLE
        ) {
            return emptyList()
        }
        return CURATED.filter { m ->
            m.needs == null ||
                res[m.needs]?.visibility == Visibility.VISIBLE
        }.map { it.name }
    }
}
