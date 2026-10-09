package org.sableos.titan2.displaycompat.core

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
        "Enforcement backend: CLOSED. Profiles are validated and stored only; nothing is enforced."
    ),
    OPEN_UNVERIFIED(
        "Enforcement backend: OPEN (gate set). Requests go to the platform; their effect is not verified here."
    )
}

/** User-facing wording. Nothing here may say a profile is "applied" or "enforced" without the qualifiers below. */
object EnforcementText {
    fun describe(state: EnforcementState, result: ApplyResult?): String = when (state) {
        EnforcementState.NOT_APPLICABLE_NATIVE -> "Native profile: nothing to enforce."

        EnforcementState.NOT_ENFORCED_BACKEND_CLOSED ->
            "Stored only. NOT enforced: the platform backend is closed until it is validated on a device."

        EnforcementState.NOT_ENFORCED_UNSUPPORTED ->
            "Stored only. NOT enforced: " +
                ((result as? ApplyResult.Unsupported)?.reason ?: "unsupported") +
                "."

        EnforcementState.PLATFORM_CALL_ACCEPTED_UNVERIFIED ->
            "Stored. The platform accepted the request (" +
                ((result as? ApplyResult.Applied)?.detail ?: "no detail") +
                "); its effect on the app is not verified."
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
        !storeCleared -> "Reset failed: the stored profile could not be removed."

        platform is ApplyResult.Unsupported ->
            "Reset to Native. The stored profile was removed, but the platform override " +
                "could not be cleared: " + platform.reason + "."

        else -> "Reset to Native. The app was not launched."
    }
}

data class ResetAllOutcome(val count: Int, val platformFailures: List<String>) {
    fun message(): String = if (platformFailures.isEmpty()) {
        "Reset $count profile(s) to Native."
    } else {
        "Reset $count profile(s) to Native. The platform override could not be cleared for " +
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
        "A stored profile is no longer allowed for this app and is ignored: $reason. Save or reset to clear it."
    } else {
        null
    }
}
