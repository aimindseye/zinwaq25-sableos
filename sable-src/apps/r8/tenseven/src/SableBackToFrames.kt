package io.github.aimindseye.tenseven.frames

import android.app.ActivityManager
import android.content.Context

/**
 * SableOS (2.0 plan, BB10 Active Frames): Back at an app's root returns to Active Frames, as Home does.
 * Android moves the app's task to the back and brings the home forward without a new intent, so the home
 * notes, as it stops, whether another app took the screen, and shows Active Frames when it comes back for
 * that reason. Returning from the home's own screens, or from the screen turning off, changes nothing.
 * The home holds REAL_GET_TASKS (patch_frames), so it can see the task in front.
 */
object SableBackToFrames {
    /** True when the home stopped because [topPackage]'s app came in front of it. */
    fun leftForAnotherApp(
        ownPackage: String,
        topPackage: String?,
        changingConfigurations: Boolean,
    ): Boolean = !changingConfigurations && !topPackage.isNullOrEmpty() && topPackage != ownPackage

    /** The package of the activity in front now, or null when it cannot be read. */
    @Suppress("DEPRECATION")
    fun topPackage(context: Context): String? =
        runCatching {
            context
                .getSystemService(ActivityManager::class.java)
                ?.getRunningTasks(1)
                ?.firstOrNull()
                ?.topActivity
                ?.packageName
        }.getOrNull()
}
