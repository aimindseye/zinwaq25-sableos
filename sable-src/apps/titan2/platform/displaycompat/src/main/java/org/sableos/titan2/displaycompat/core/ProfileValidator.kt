package org.sableos.titan2.displaycompat.core

enum class Severity { Info, Warning, Blocked }

data class Issue(
    val severity: Severity,
    val code: String,
    val message: String,
    val needsAck: Boolean = false
)

data class Validation(val issues: List<Issue>) {
    val blocked: Boolean get() = issues.any { it.severity == Severity.Blocked }
    val ackRequired: List<Issue> get() = issues.filter { it.needsAck }
    val ok: Boolean get() = !blocked
}

/**
 * Safety rules from APP_DISPLAY_COMPATIBILITY_PROFILES_UX: text input always wins, and lockscreen, authenticator,
 * payment, camera preview and accessibility must not be broken by a profile.
 * Anything not Native is checked here before it is saved.
 */
object ProfileValidator {
    fun validate(
        traits: AppTraits,
        p: DisplayProfile,
        advancedEnabled: Boolean = false
    ): Validation {
        if (p.isNative) return Validation(emptyList())
        val out = mutableListOf<Issue>()
        checkAppProtection(traits, p, out)
        checkEffects(p, out)
        checkCustomRatio(p, advancedEnabled, out)
        if (p.profile == AspectProfile.MiniMode) {
            out.add(
                Issue(
                    Severity.Info,
                    "MINI_SIZE",
                    "Mini mode applies only once this device's mini size has been captured."
                )
            )
        }
        if (p.orientation.locksOrientation() && traits.isCamera) {
            out.warn(
                "CAMERA_ORIENTATION",
                "Locking orientation can rotate the camera preview unexpectedly."
            )
        }
        return Validation(out)
    }

    private fun MutableList<Issue>.block(code: String, msg: String) =
        add(Issue(Severity.Blocked, code, msg))

    private fun MutableList<Issue>.warn(code: String, msg: String, ack: Boolean = false) =
        add(Issue(Severity.Warning, code, msg, ack))

    private fun OrientationPref.locksOrientation(): Boolean =
        this != OrientationPref.Default && this != OrientationPref.Auto

    private fun AppTraits.isProtectedApp(): Boolean =
        isSystemUiOrSettings || isInputMethod || isAccessibilityService || isHome

    private fun DisplayProfile.isReshaping(): Boolean = when (profile) {
        AspectProfile.Native,
        AspectProfile.FullscreenMedia,
        AspectProfile.SquareSafe,
        AspectProfile.KeyboardSafe -> false

        else -> true
    }

    private fun DisplayProfile.isDistorting(): Boolean =
        profile == AspectProfile.Ratio16x9Fill || profile == AspectProfile.Custom || stretch

    private fun checkAppProtection(traits: AppTraits, p: DisplayProfile, out: MutableList<Issue>) {
        val reshaping = p.isReshaping()
        val distorting = p.isDistorting()
        if (traits.isProtectedApp()) {
            out.block(
                "PROTECTED_APP",
                "System UI, Settings, keyboards, accessibility services and the launcher cannot use display profiles."
            )
        }
        if (traits.isDialer && reshaping) {
            out.block(
                "DIALER_RESHAPE",
                "The phone app must keep the full display so calls and emergency dialling stay reachable."
            )
        }
        if (traits.isAuthenticatorOrPayment && distorting) {
            out.block(
                "AUTH_PAYMENT_DISTORT",
                "Authenticator and payment apps cannot be cropped, stretched or given a custom ratio."
            )
        }
        if (traits.isAuthenticatorOrPayment && reshaping && !distorting) {
            out.warn(
                "AUTH_PAYMENT_RESHAPE",
                "Check codes and prompts stay fully visible after applying this profile.",
                ack = true
            )
        }
        checkCameraAndMedia(traits, p, reshaping, out)
    }

    private fun checkCameraAndMedia(
        traits: AppTraits,
        p: DisplayProfile,
        reshaping: Boolean,
        out: MutableList<Issue>
    ) {
        if (traits.isCamera && (p.profile == AspectProfile.Ratio16x9Fill || p.stretch)) {
            out.block("CAMERA_DISTORT", "Camera preview must not be cropped or stretched.")
        }
        // reshaping already excludes Fullscreen media, which is the profile the hint recommends.
        if (traits.isMedia && reshaping && p.fullscreenMediaException) {
            out.warn(
                "MEDIA_HINT",
                "Media apps usually look best with Fullscreen media instead of bars."
            )
        }
    }

    private fun checkEffects(p: DisplayProfile, out: MutableList<Issue>) {
        if (p.profile == AspectProfile.Ratio16x9Fill) {
            out.warn(
                "FILL_CROP",
                "Fill can crop edges. Use letterbox if buttons or text are missing.",
                ack = true
            )
        }
        if (p.stretch) {
            out.warn(
                "STRETCH",
                "Stretch distorts text, maps, camera previews and games. It is off by default.",
                ack = true
            )
        }
    }

    private fun checkCustomRatio(
        p: DisplayProfile,
        advancedEnabled: Boolean,
        out: MutableList<Issue>
    ) {
        if (p.profile == AspectProfile.Custom) {
            val r = p.customRatio
            when {
                !advancedEnabled -> out.block(
                    "CUSTOM_GATED",
                    "Custom ratios are an advanced setting. Enable advanced profiles first."
                )

                r == null -> out.block("CUSTOM_MISSING", "Choose a custom ratio.")

                r.value < Ratio.MIN || r.value > Ratio.MAX -> out.block(
                    "CUSTOM_RANGE",
                    "Custom ratio must be between ${Ratio.MIN} and ${Ratio.MAX}."
                )
            }
        } else if (p.customRatio != null) {
            out.warn("CUSTOM_IGNORED", "A custom ratio is stored but not used by this profile.")
        }
    }
}

/**
 * Conservative name heuristic for apps that must not be distorted.
 * Over-matching is intentional: it only adds protection.
 */
object TraitHeuristics {
    private val sensitive =
        Regex(
            "authenticator|otp|2fa|totp|wallet|bank|banking|payment|paypal|venmo|\\.pay\\b|\\.pay\\."
        )
    fun nameLooksSensitive(pkg: String): Boolean = sensitive.containsMatchIn(pkg.lowercase())
    private val protectedPrefixes =
        listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.keyguard",
            "org.sableos.launcher"
        )
    fun isProtectedSystemPackage(pkg: String): Boolean = protectedPrefixes.any {
        pkg == it ||
            pkg.startsWith("$it.")
    }
}
