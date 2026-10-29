package org.sableos.tools.core

import org.sableos.tools.core.ToolsText.t

/** The kind of field a case uses, mapped to an Android inputType by the UI. */
enum class FieldKind { TEXT, NUMBER, NUMBER_PASSWORD, VISIBLE_PASSWORD }

/** A modifier a case must exercise; [metaMask] is the Android KeyEvent META_* bit. */
enum class Modifier(val metaMask: Int) {
    SHIFT(KeyCommands.META_SHIFT_ON),
    ALT(KeyCommands.META_ALT_ON),
    SYM(KeyCommands.META_SYM_ON),
    FN(KeyCommands.META_FUNCTION_ON)
}

data class TextEntryCase(
    val id: String,
    val title: String,
    val prompt: String,
    val field: FieldKind,
    /**
     * Exact text required, or null when the case is judged from key events ([modifier]). With both set, the text must
     * match and every typed character must have been produced with [modifier] held or locked.
     */
    val expected: String? = null,
    /** The key this case exercises; the case is offered only when the profile's keyboard declares it. */
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
    private val SHIFT_CASE = TextEntryCase(
        "shift",
        t("shift"),
        t("hold_or_lock_shift_and"),
        FieldKind.TEXT,
        modifier = Modifier.SHIFT
    )
    private val ALT_DIGITS_CASE = TextEntryCase(
        "alt_digits",
        t("alt_layer_digits"),
        t("hold_or_lock_alt_and"),
        FieldKind.TEXT,
        expected = "2468",
        modifier = Modifier.ALT
    )

    /** The spec's list (letters, numbers, symbols, Alt, Sym, Fn, ...), used as-is for [KeyboardTraits.GENERIC]. */
    val CASES: List<TextEntryCase> = listOf(
        TextEntryCase("letters", t("letters"), t("type_sable"), FieldKind.TEXT, expected = "sable"),
        TextEntryCase("numbers", t("numbers"), t("type_2468"), FieldKind.NUMBER, expected = "2468"),
        TextEntryCase(
            "symbols",
            t("symbols"),
            t("type"),
            FieldKind.TEXT,
            expected = "@#\$%&*!?"
        ),
        TextEntryCase(
            "alt",
            t("alt_layer"),
            t("hold_or_lock_alt_and_2"),
            FieldKind.TEXT,
            modifier = Modifier.ALT
        ),
        TextEntryCase(
            "sym",
            t("sym"),
            t("use_sym_to_enter_any"),
            FieldKind.TEXT,
            modifier = Modifier.SYM
        ),
        TextEntryCase(
            "fn",
            t("fn"),
            t("use_fn_with_any_key"),
            FieldKind.TEXT,
            modifier = Modifier.FN
        ),
        TextEntryCase(
            "bt_pairing",
            t("bluetooth_pairing_code"),
            t("simulated_pairing_dialog_enter_482915"),
            FieldKind.NUMBER,
            expected = "482915"
        ),
        TextEntryCase(
            "wifi_password",
            t("wi_fi_password"),
            t("simulated_wi_fi_password_enter"),
            FieldKind.VISIBLE_PASSWORD,
            expected = "Tr4il-mix#26!"
        ),
        TextEntryCase(
            "pin",
            t("lockscreen_pin"),
            t("simulated_pin_pad_enter_7391"),
            FieldKind.NUMBER_PASSWORD,
            expected = "7391"
        ),
        TextEntryCase(
            "software_fallback",
            t("software_keyboard_fallback"),
            t("use_the_on_screen_keyboard"),
            FieldKind.TEXT,
            expected = "ok",
            softwareKeyboard = true
        )
    )

    /**
     * Cases for one keyboard: a case tied to a key appears only when the keyboard declares that key, so a release gate
     * never asks for a key the device lacks (no Fn case on the Q25). Shift and Alt-layer digits are added where
     * declared.
     */
    fun casesFor(keyboard: KeyboardTraits): List<TextEntryCase> {
        val extra = listOfNotNull(
            SHIFT_CASE.takeIf { Modifier.SHIFT in keyboard.modifiers },
            ALT_DIGITS_CASE.takeIf {
                keyboard.digitsOnAltLayer && Modifier.ALT in keyboard.modifiers
            }
        )
        val all = CASES.take(EXTRA_INSERT_AT) + extra + CASES.drop(EXTRA_INSERT_AT)
        return all.filter { it.modifier == null || it.modifier in keyboard.modifiers }
    }

    /** Shift and Alt-layer digits go after letters, numbers and symbols. */
    private const val EXTRA_INSERT_AT = 3

    fun check(case: TextEntryCase, entered: String, keys: List<TypedKey>): CaseResult {
        val expected = case.expected
        val m = case.modifier
        val pass = when {
            expected != null && m != null ->
                entered == expected &&
                    keys.any { it.unicode > 0 } &&
                    keys.filter { it.unicode > 0 }.all { it.meta and m.metaMask != 0 }

            expected != null -> entered == expected

            m == null -> false

            else -> keys.filter { it.meta and m.metaMask != 0 && it.unicode > 0 }
                .map { String(Character.toChars(it.unicode)) }
                .any { entered.contains(it) }
        }
        return if (pass) CaseResult.PASS else CaseResult.FAIL
    }

    /** "7/10 PASS, 1 FAIL, 2 NOT_RUN" plus per-case lines; safe for reports (no typed text). */
    fun summary(
        results: Map<String, CaseResult>,
        cases: List<TextEntryCase> = CASES
    ): List<ReportRow> {
        val rows = cases.map { ReportRow(it.title, (results[it.id] ?: CaseResult.NOT_RUN).name) }
        val pass = rows.count { it.value == CaseResult.PASS.name }
        val fail = rows.count { it.value == CaseResult.FAIL.name }
        return listOf(
            ReportRow(
                t("critical_text_entry"),
                t("pass_fail_not_run", pass, cases.size, fail, cases.size - pass - fail)
            )
        ) +
            rows
    }
}

/** Curated SubScreen companion modes Sable Tools may offer (no arbitrary mirroring, no text entry). */
object SubscreenModes {
    data class Mode(val name: String, val needs: Capability?)

    val CURATED = listOf(
        Mode(t("compass"), Capability.COMPASS),
        Mode(t("media_quick_control"), null),
        Mode(t("safe_ir_quick_control"), Capability.IR_REMOTE),
        Mode(t("camera_magnifier_preview"), Capability.MAGNIFIER),
        Mode(t("battery_and_device_status"), null)
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
