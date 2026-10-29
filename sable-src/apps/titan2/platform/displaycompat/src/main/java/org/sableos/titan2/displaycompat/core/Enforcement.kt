package org.sableos.titan2.displaycompat.core

import org.sableos.titan2.displaycompat.core.DisplayCompatText.t

/**
 * What can honestly be said about a profile after a save. There is deliberately no "enforced" state: this app has no
 * way to read the platform back, so the strongest claim is that the platform accepted the request. Effect on the
 * app window is validated on a device by the canonical lane (IR-006, KL-015).
 */
enum class EnforcementState {
    NOT_APPLICABLE_NATIVE,
    NOT_ENFORCED_BACKEND_CLOSED,
    NOT_ENFORCED_UNSUPPORTED,
    PLATFORM_CALL_ACCEPTED_UNVERIFIED;

    companion object {
        fun of(nativeProfile: Boolean, result: ApplyResult?): EnforcementState = when {
            nativeProfile -> NOT_APPLICABLE_NATIVE
            result is ApplyResult.Applied -> PLATFORM_CALL_ACCEPTED_UNVERIFIED
            result is ApplyResult.Unsupported -> NOT_ENFORCED_UNSUPPORTED
            else -> NOT_ENFORCED_BACKEND_CLOSED
        }
    }
}

/** Whether the enforcement backend will send requests to the platform at all. Closed is the fail-closed default. */
enum class BackendAvailability(val banner: String) {
    CLOSED(
        t("enforcement_backend_closed_profiles_are")
    ),
    OPEN_UNVERIFIED(
        t("enforcement_backend_open_gate_set")
    )
}

/** User-facing wording. Nothing here may say a profile is "applied" or "enforced" without the qualifiers below. */
object EnforcementText {
    fun describe(state: EnforcementState, result: ApplyResult?): String = when (state) {
        EnforcementState.NOT_APPLICABLE_NATIVE -> t("native_profile_nothing_to_enforce")

        EnforcementState.NOT_ENFORCED_BACKEND_CLOSED ->
            t("stored_only_not_enforced_the")

        EnforcementState.NOT_ENFORCED_UNSUPPORTED ->
            t("stored_only_not_enforced") +
                ((result as? ApplyResult.Unsupported)?.reason ?: "unsupported") +
                "."

        EnforcementState.PLATFORM_CALL_ACCEPTED_UNVERIFIED ->
            t("stored_the_platform_accepted_the") +
                ((result as? ApplyResult.Applied)?.detail ?: t("no_detail")) +
                t("its_effect_on_the_app")
    }
}

/** Package names are used as store keys and as the argument of a platform call, so they are checked first. */
object PackageNames {
    private const val MAX_LENGTH = 255
    private val valid = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")

    fun isValid(pkg: String): Boolean = pkg.length <= MAX_LENGTH && valid.matches(pkg)
}

enum class SaveStatus { BLOCKED, NEEDS_ACK, STORAGE_FAILED, STORED }

/** Result of removing a profile. A platform override that could not be cleared is reported, never hidden. */
data class ResetOutcome(val storeCleared: Boolean, val platform: ApplyResult) {
    fun message(): String = when {
        !storeCleared -> t("reset_failed_the_stored_profile")

        platform is ApplyResult.Unsupported ->
            t("reset_to_native_the_stored") + platform.reason + "."

        else -> t("reset_to_native_the_app")
    }
}

data class ResetAllOutcome(val count: Int, val platformFailures: List<String>) {
    fun message(): String = if (platformFailures.isEmpty()) {
        t("reset_profile_s_to_native", count)
    } else {
        t("reset_profile_s_to_native_2", count) +
            platformFailures.size + " app(s): " + platformFailures.joinToString(", ") + "."
    }
}

enum class StoredState { NONE, STORED, STORED_BLOCKED_NOT_EFFECTIVE }

/**
 * A stored profile as it should be treated now. A profile that passed validation when it was saved can stop being
 * allowed (the app became a keyboard, the launcher, an authenticator), and then it must not be applied.
 */
data class LoadedProfile(val profile: DisplayProfile, val state: StoredState, val reason: String?) {
    fun message(): String? = if (state == StoredState.STORED_BLOCKED_NOT_EFFECTIVE) {
        t("a_stored_profile_is_no", reason)
    } else {
        null
    }
}
