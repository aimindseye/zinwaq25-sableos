package org.sableos.titan2.displaycompat.android

import android.content.Context
import android.content.pm.PackageManager
import org.sableos.titan2.displaycompat.core.ApplyResult
import org.sableos.titan2.displaycompat.core.AspectProfile
import org.sableos.titan2.displaycompat.core.BackendAvailability
import org.sableos.titan2.displaycompat.core.CanvasPlan
import org.sableos.titan2.displaycompat.core.DisplayBackend
import org.sableos.titan2.displaycompat.core.DisplayProfile

/**
 * Backend using the platform per-app minimum-aspect-ratio override
 * (Android 14+ `PackageManager.setUserMinAspectRatio`).
 *
 * It is closed unless `persist.sable.displaycompat.backend=validated`, so a fresh image never changes app windows.
 * The call is reached by reflection and any SecurityException/NoSuchMethod is reported as
 * [ApplyResult.Unsupported]: whether a platform-signed app may call it on Android 16 is a gate to validate on
 * device, not an assumption. No privapp grant is added here.
 * Only profiles with a real platform equivalent are mapped; everything else stays validated-and-stored but unapplied.
 */
class GatedPlatformBackend(private val context: Context) : DisplayBackend {
    override val name = "platform min-aspect-ratio (gated)"

    /** Closed unless the gate property is exactly `validated`; read on every call so a closed gate stays closed. */
    override val availability: BackendAvailability
        get() = if (open()) BackendAvailability.OPEN_UNVERIFIED else BackendAvailability.CLOSED

    private fun open() = SysProps.get("persist.sable.displaycompat.backend") == "validated"

    override fun apply(pkg: String, plan: CanvasPlan, p: DisplayProfile): ApplyResult {
        val value = UserMinAspectRatio.forProfile(p.profile)
        return when {
            !open() -> ApplyResult.NoOp

            value == null -> ApplyResult.Unsupported(
                "${p.profile.id}: no platform equivalent; stored only"
            )

            else -> set(pkg, value, "min aspect ratio=$value")
        }
    }

    override fun clear(pkg: String): ApplyResult =
        if (!open()) ApplyResult.NoOp else set(pkg, UserMinAspectRatio.UNSET, "unset")

    private fun set(pkg: String, value: Int, detail: String): ApplyResult = try {
        PackageManager::class.java.getMethod(
            "setUserMinAspectRatio",
            String::class.java,
            Int::class.javaPrimitiveType
        )
            .invoke(context.packageManager, pkg, value)
        ApplyResult.Applied(detail)
    } catch (t: ReflectiveOperationException) {
        unsupported(t)
    } catch (t: SecurityException) {
        unsupported(t)
    } catch (t: IllegalArgumentException) {
        unsupported(t)
    }

    private fun unsupported(t: Throwable): ApplyResult = ApplyResult.Unsupported(
        (t.cause ?: t).javaClass.simpleName + ": " + ((t.cause ?: t).message ?: "no detail")
    )
}

/**
 * Values of the hidden `PackageManager.USER_MIN_ASPECT_RATIO_*` constants (AOSP 14+: UNSET 0, SPLIT_SCREEN 1,
 * DISPLAY_SIZE 2, 4_3 3, 16_9 4, 3_2 5, FULLSCREEN 6, APP_DEFAULT 7). The public SDK does not expose them, so they are
 * literals here. Note 4:3 is 3 and 16:9 is 4 (an earlier version had them swapped, which would have put a 16:9 profile
 * in a 4:3 window and vice versa). Re-check against the Android 16 source before the backend gate is opened.
 */
internal object UserMinAspectRatio {
    const val UNSET = 0
    const val DISPLAY_SIZE = 2
    const val RATIO_4_3 = 3
    const val RATIO_16_9 = 4
    const val FULLSCREEN = 6

    /** Platform value for a profile with a real platform equivalent, else null (stored only). */
    fun forProfile(profile: AspectProfile): Int? = when (profile) {
        AspectProfile.Ratio16x9Letterbox -> RATIO_16_9
        AspectProfile.Ratio4x3Letterbox -> RATIO_4_3
        AspectProfile.FullscreenMedia -> FULLSCREEN
        AspectProfile.SquareSafe -> DISPLAY_SIZE
        else -> null
    }
}
