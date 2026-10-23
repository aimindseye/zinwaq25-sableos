package org.sableos.titan2.displaycompat.core

/** System-wide rotation, in Display tools. Follow apps leaves the user's own setting alone. */
enum class SystemRotation(val id: String, val label: String) {
    FollowApps("follow", "Follow apps"),
    Auto("auto", "Auto-rotate"),
    Portrait("portrait", "Portrait"),
    Landscape("landscape", "Landscape");

    companion object {
        fun fromId(id: String?): SystemRotation = entries.firstOrNull { it.id == id } ?: FollowApps
    }
}

/** `Surface.ROTATION_*` values written to `Settings.System.USER_ROTATION`. */
object Rotations {
    const val ROTATION_0 = 0

    /** Reverse landscape, the way the per-app Landscape compat override turns apps. */
    const val ROTATION_270 = 3
}

/** A rotation the display is held at: auto-rotate on, or locked to [userRotation]. */
data class RotationTarget(val autoRotate: Boolean, val userRotation: Int = Rotations.ROTATION_0) {
    companion object {
        val AUTO = RotationTarget(autoRotate = true)
        val PORTRAIT = RotationTarget(autoRotate = false, userRotation = Rotations.ROTATION_0)
        val LANDSCAPE = RotationTarget(autoRotate = false, userRotation = Rotations.ROTATION_270)
    }
}

/** What the glue reads from the device. [frontProfile] is already validated (NATIVE if blocked). */
data class Situation(
    val gateOpen: Boolean,
    val setupComplete: Boolean,
    /** Screen on and keyguard not showing. */
    val interactive: Boolean,
    val frontPackage: String?,
    val frontProfile: DisplayProfile,
    val systemRotation: SystemRotation,
    /** Some stored profile asks for a platform aspect ratio (see [DisplayPolicy]). */
    val anyAspectProfile: Boolean
)

/** Facts about the default display, in its natural orientation. */
data class DisplayFacts(val native: SizePx, val densityDpi: Int)

/** Each field null means "not held by Display Compat: the user's own value". */
data class DisplayTarget(
    val size: SizePx? = null,
    val density: Int? = null,
    val rotation: RotationTarget? = null,
    val ignoreOrientationRequest: Boolean? = null
) {
    companion object {
        val NONE = DisplayTarget()
    }
}

/**
 * Restore decisions in one place:
 *  - Tall canvas only while its app is in front, the screen is on and unlocked and setup is done;
 *    any other app, the lock screen, a dead app (its task leaves the front) restore the display.
 *  - Per-app orientation is held only while the app is in front; otherwise the system rotation.
 *  - The display ignores apps' orientation requests while a rotation is locked (so a fixed-orientation
 *    app cannot turn it) and while any app has an aspect ratio profile: on Android 16,
 *    `AppCompatAspectRatioOverrides.shouldEnableUserAspectRatioSettings` honours user aspect ratios
 *    only on a display that ignores orientation requests, which a phone display does not by default.
 *  - A closed gate or unfinished setup holds nothing.
 */
object DisplayPolicy {
    fun target(s: Situation, display: DisplayFacts): DisplayTarget {
        if (!s.gateOpen || !s.setupComplete) return DisplayTarget.NONE
        val front = if (s.interactive &&
            s.frontPackage != null
        ) {
            s.frontProfile
        } else {
            DisplayProfile.NATIVE
        }
        val size = front.canvas.size(display.native)
        val rotation = appRotation(front.orientation) ?: systemRotation(s.systemRotation)
        val locked = rotation != null && !rotation.autoRotate
        return DisplayTarget(
            size = size,
            density = size?.let { CanvasMath.compactDensity(it, display.densityDpi) },
            rotation = rotation,
            ignoreOrientationRequest = (locked || s.anyAspectProfile).takeIf { it }
        )
    }

    private fun appRotation(o: OrientationPref): RotationTarget? = when (o) {
        OrientationPref.Default -> null
        OrientationPref.Portrait -> RotationTarget.PORTRAIT
        OrientationPref.Landscape -> RotationTarget.LANDSCAPE
        OrientationPref.Auto -> RotationTarget.AUTO
    }

    private fun systemRotation(r: SystemRotation): RotationTarget? = when (r) {
        SystemRotation.FollowApps -> null
        SystemRotation.Auto -> RotationTarget.AUTO
        SystemRotation.Portrait -> RotationTarget.PORTRAIT
        SystemRotation.Landscape -> RotationTarget.LANDSCAPE
    }

    /** True when the profile sends a user aspect ratio the platform applies (not unset). */
    fun usesAspectRatio(p: DisplayProfile): Boolean =
        PlatformMapping.request(p).minAspectRatio != UserMinAspectRatio.UNSET

    /** The service must follow the foreground app only for profiles that change while it is in front. */
    fun needsForegroundTracking(profiles: Collection<DisplayProfile>): Boolean =
        profiles.any { it.canvas != TallCanvas.Native || it.orientation != OrientationPref.Default }
}
