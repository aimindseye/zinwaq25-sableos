package org.sableos.start.model

/**
 * The look first-boot setup chose (SetupWizard's Look step, org.sableos.start.action.SET_LOOK):
 * BlackBerry 10 style or BlackBerry 7 style, which sets Start's home style; setup sets pure black
 * itself. There is no Plain look (owner decision 2026-11-05). Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 */
enum class StartLook(
    val stableValue: String,
    val homeStyle: HomeStyle,
) {
    Bb10("bb10", HomeStyle.Bb10),
    Bb7("bb7", HomeStyle.Bb7),
    ;

    companion object {
        /** null for a missing or unknown value, so a bad request changes nothing. */
        fun fromStableValue(value: String?): StartLook? = entries.firstOrNull { it.stableValue == value }
    }
}
