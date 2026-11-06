package org.sableos.tools.core

import org.sableos.tools.core.ToolsText.t

/** How one privacy check came out. [UNKNOWN] is never counted against the phone: the app could not tell. */
enum class CheckState { GOOD, NEEDS_ATTENTION, UNKNOWN }

/** The checks, in the order they are listed and in the order a fix is offered. */
enum class PrivacyCheckId(
    private val titleKey: String,
    /** A phone without this is exposed to anyone who picks it up, so it weighs more than the rest. */
    val critical: Boolean,
    val link: SettingsLink
) {
    SCREEN_LOCK("check_screen_lock", true, SettingsLink.SECURITY),
    ENCRYPTION("check_encryption", true, SettingsLink.SECURITY),
    INSTALL_APPS("check_install_apps", false, SettingsLink.INSTALL_UNKNOWN),
    SENSITIVE_APPS("check_sensitive_apps", false, SettingsLink.PRIVACY),
    PRIVATE_SPACE("check_private_space", false, SettingsLink.SECURITY),
    USB_DEBUGGING("check_usb_debugging", false, SettingsLink.DEVELOPER),
    SECURITY_PATCH("check_security_patch", false, SettingsLink.ABOUT);

    val title: String get() = t(titleKey)
}

/** One row of the privacy check: what was checked, how it came out, and the short value to show. */
data class PrivacyCheck(val id: PrivacyCheckId, val state: CheckState, val detail: String)

enum class PrivacyRating(private val labelKey: String) {
    GOOD("privacy_good"),
    FAIR("privacy_fair"),
    POOR("privacy_poor");

    val label: String get() = t(labelKey)
}

/**
 * The DTEK-style rating. Pure and read-only: it rates what the platform layer read and never changes a setting.
 *
 * Good: nothing needs attention. Poor: the screen lock or encryption needs attention, or three or more checks do.
 * Fair: anything else.
 */
object PrivacyScore {
    /** More apps than this holding location, microphone or camera needs a look. */
    const val SENSITIVE_APPS_LIMIT = 8

    /** A security patch older than this many days needs attention. */
    const val PATCH_MAX_AGE_DAYS = 180

    private const val POOR_COUNT = 3

    fun rate(checks: List<PrivacyCheck>): PrivacyRating {
        val bad = checks.filter { it.state == CheckState.NEEDS_ATTENTION }
        return when {
            bad.isEmpty() -> PrivacyRating.GOOD
            bad.any { it.id.critical } || bad.size >= POOR_COUNT -> PrivacyRating.POOR
            else -> PrivacyRating.FAIR
        }
    }

    /** The one fix to offer first: the first check, in list order, that needs attention. */
    fun nextFix(checks: List<PrivacyCheck>): PrivacyCheck? =
        checks.sortedBy { it.id.ordinal }.firstOrNull { it.state == CheckState.NEEDS_ATTENTION }

    fun sensitiveAppsState(count: Int?): CheckState = when {
        count == null -> CheckState.UNKNOWN
        count > SENSITIVE_APPS_LIMIT -> CheckState.NEEDS_ATTENTION
        else -> CheckState.GOOD
    }

    /** Apps that may install other apps: none is good; any is worth a look, so the user sees which. */
    fun installAppsState(count: Int?): CheckState = when {
        count == null -> CheckState.UNKNOWN
        count > 0 -> CheckState.NEEDS_ATTENTION
        else -> CheckState.GOOD
    }

    fun patchState(ageDays: Long?): CheckState = when {
        ageDays == null -> CheckState.UNKNOWN
        ageDays > PATCH_MAX_AGE_DAYS -> CheckState.NEEDS_ATTENTION
        else -> CheckState.GOOD
    }

    fun flagState(isOn: Boolean?, goodWhenOn: Boolean): CheckState = when (isOn) {
        null -> CheckState.UNKNOWN
        goodWhenOn -> CheckState.GOOD
        else -> CheckState.NEEDS_ATTENTION
    }
}
