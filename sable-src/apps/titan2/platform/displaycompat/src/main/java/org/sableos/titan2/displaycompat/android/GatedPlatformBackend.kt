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
        val value = platformValue(p.profile)
        return when {
            !open() -> ApplyResult.NoOp

            value == null -> ApplyResult.Unsupported(
                "${p.profile.id}: no platform equivalent; stored only"
            )

            else -> set(pkg, value, "min aspect ratio=$value")
        }
    }

    private fun platformValue(profile: AspectProfile): Int? = when (profile) {
        AspectProfile.Ratio16x9Letterbox -> ASPECT_16_9
        AspectProfile.Ratio4x3Letterbox -> ASPECT_4_3
        AspectProfile.FullscreenMedia -> ASPECT_FULLSCREEN
        AspectProfile.SquareSafe -> ASPECT_DISPLAY_SIZE
        else -> null
    }

    override fun clear(pkg: String): ApplyResult =
        if (!open()) ApplyResult.NoOp else set(pkg, ASPECT_UNSET, "unset")

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

    private companion object {
        // Values of the hidden PackageManager.USER_MIN_ASPECT_RATIO_* constants as of Android 14.
        // The public SDK does not expose them, so they are literals here.
        // VERIFY against the Android 16 source before the backend gate is opened.
        const val ASPECT_UNSET = 0
        const val ASPECT_DISPLAY_SIZE = 2
        const val ASPECT_16_9 = 3
        const val ASPECT_4_3 = 4
        const val ASPECT_FULLSCREEN = 6
    }
}
