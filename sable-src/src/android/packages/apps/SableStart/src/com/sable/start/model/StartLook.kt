package org.sableos.start.model

/**
 * The look first-boot setup chose (SetupWizard's Look step, org.sableos.start.action.SET_LOOK):
 * BlackBerry 10 style, BlackBerry 7 style or Plain. Each sets Start's home style and whether the
 * BB10 home shows its status dials; setup sets pure black itself. Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 */
enum class StartLook(
    val stableValue: String,
    val homeStyle: HomeStyle,
    private val dials: Boolean,
) {
    Bb10("bb10", HomeStyle.Bb10, dials = true),
    Bb7("bb7", HomeStyle.Bb7, dials = true),

    /** Start's BB10 layout without the status dials. */
    Plain("plain", HomeStyle.Bb10, dials = false),
    ;

    /** The BB10 home's hidden modules after this look, keeping the user's other choices. */
    fun hiddenSections(stored: Set<String>?): Set<String> {
        val hidden = HomeLayout.normalizeHidden(stored)
        return if (dials) hidden - HomeLayout.STATUS_RINGS else hidden + HomeLayout.STATUS_RINGS
    }

    companion object {
        /** null for a missing or unknown value, so a bad request changes nothing. */
        fun fromStableValue(value: String?): StartLook? = entries.firstOrNull { it.stableValue == value }
    }
}
