package org.sableos.titan2.displaycompat.core

import org.sableos.titan2.displaycompat.core.DisplayCompatText.t

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
                    "MINI_WINDOW",
                    t("mini_mode_opens_the_app")
                )
            )
        }
        checkOrientation(traits, p, out)
        checkCanvas(traits, p, out)
        return Validation(out)
    }

    /** Tall canvas changes the whole display while the app is in front. */
    private fun checkCanvas(traits: AppTraits, p: DisplayProfile, out: MutableList<Issue>) {
        if (p.canvas == TallCanvas.Native) return
        if (traits.isDialer) {
            out.block(
                "DIALER_CANVAS",
                t("the_phone_app_must_keep")
            )
        }
        if (traits.isAuthenticatorOrPayment) {
            out.warn(
                "AUTH_PAYMENT_CANVAS",
                t("check_codes_and_prompts_stay"),
                ack = true
            )
        }
        if (traits.isCamera) {
            out.warn("CAMERA_CANVAS", t("the_camera_preview_is_shown"))
        }
        if (p.profile != AspectProfile.Native) {
            out.warn(
                "CANVAS_WITH_ASPECT",
                t("tall_canvas_already_gives_a")
            )
        }
        out.add(
            Issue(
                Severity.Info,
                "CANVAS_LIVE",
                t("tall_canvas_while_this_app")
            )
        )
    }

    private fun checkOrientation(traits: AppTraits, p: DisplayProfile, out: MutableList<Issue>) {
        if (p.orientation.locksOrientation() && traits.isCamera) {
            out.warn(
                "CAMERA_ORIENTATION",
                t("locking_orientation_can_rotate_the")
            )
        }
        if (p.orientation == OrientationPref.Landscape) {
            out.warn(
                "LANDSCAPE_SIDEWAYS",
                t("landscape_turns_the_app_sideways"),
                ack = true
            )
        }
        if (p.orientation == OrientationPref.Auto) {
            out.add(
                Issue(
                    Severity.Info,
                    "AUTO_LIVE",
                    t("auto_turns_auto_rotate_on")
                )
            )
        }
        if (p.orientation == OrientationPref.Landscape && DisplayPolicy.usesAspectRatio(p)) {
            out.warn(
                "ASPECT_KEEPS_PORTRAIT",
                t("with_an_aspect_ratio_android")
            )
        }
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
                t("system_ui_settings_keyboards_accessibility")
            )
        }
        if (traits.isDialer && reshaping) {
            out.block(
                "DIALER_RESHAPE",
                t("the_phone_app_must_keep_2")
            )
        }
        if (traits.isAuthenticatorOrPayment && distorting) {
            out.block(
                "AUTH_PAYMENT_DISTORT",
                t("authenticator_and_payment_apps_cannot")
            )
        }
        if (traits.isAuthenticatorOrPayment && reshaping && !distorting) {
            out.warn(
                "AUTH_PAYMENT_RESHAPE",
                t("check_codes_and_prompts_stay_2"),
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
            out.block("CAMERA_DISTORT", t("camera_preview_must_not_be"))
        }
        // reshaping already excludes Fullscreen media, which is the profile the hint recommends.
        if (traits.isMedia && reshaping && p.fullscreenMediaException) {
            out.warn(
                "MEDIA_HINT",
                t("media_apps_usually_look_best")
            )
        }
    }

    private fun checkEffects(p: DisplayProfile, out: MutableList<Issue>) {
        if (p.profile == AspectProfile.Ratio16x9Fill) {
            out.warn(
                "FILL_CROP",
                t("fill_can_crop_edges_use"),
                ack = true
            )
        }
        if (p.stretch) {
            out.warn(
                "STRETCH",
                t("stretch_distorts_text_maps_camera"),
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
                    t("custom_ratios_are_an_advanced")
                )

                r == null -> out.block("CUSTOM_MISSING", t("choose_a_custom_ratio"))

                r.value < Ratio.MIN || r.value > Ratio.MAX -> out.block(
                    "CUSTOM_RANGE",
                    t("custom_ratio_must_be_between", Ratio.MIN, Ratio.MAX)
                )
            }
        } else if (p.customRatio != null) {
            out.warn("CUSTOM_IGNORED", t("a_custom_ratio_is_stored"))
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
            "org.sableos.launcher",
            // Setup, the Recents host and this app's own screens are never reshaped either.
            "org.sableos.titan2.setup",
            "org.lineageos.setupwizard",
            "com.android.provision",
            "com.android.launcher3",
            "org.sableos.titan2.displaycompat"
        )
    fun isProtectedSystemPackage(pkg: String): Boolean = protectedPrefixes.any {
        pkg == it ||
            pkg.startsWith("$it.")
    }
}
