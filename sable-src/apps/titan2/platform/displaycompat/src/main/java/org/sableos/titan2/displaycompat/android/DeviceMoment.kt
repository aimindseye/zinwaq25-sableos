package org.sableos.titan2.displaycompat.android

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import org.sableos.titan2.displaycompat.core.Moment

/** Reads [Moment] from the device: the gate, setup, screen and lock state and the app in front. */
object DeviceMoment {
    private const val DEFAULT_DISPLAY = 0
    private const val TASKS = 5

    fun read(context: Context): Moment {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val interactive = power?.isInteractive == true && keyguard?.isKeyguardLocked != true
        return Moment(
            gateOpen = GatedPlatformBackend.isOpen(),
            setupComplete =
                Settings.Secure.getInt(context.contentResolver, USER_SETUP_COMPLETE, 0) == 1,
            interactive = interactive,
            frontPackage = frontPackage(context)
        )
    }

    /**
     * Package of the visible top task on the default display. The system UID sees every task
     * (REAL_GET_TASKS); `displayId` and `isVisible` are hidden fields, read by reflection with the
     * default display and "visible" as fallbacks.
     */
    @Suppress("DEPRECATION")
    fun frontPackage(context: Context): String? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val tasks = runCatching { am.getRunningTasks(TASKS) }.getOrNull().orEmpty()
        return tasks.firstOrNull {
            field(it, "displayId", DEFAULT_DISPLAY) == DEFAULT_DISPLAY &&
                field(it, "isVisible", true)
        }
            ?.let { (it.topActivity ?: it.baseActivity)?.packageName }
    }

    private inline fun <reified T> field(o: Any, name: String, default: T): T = runCatching {
        o.javaClass.getField(name).get(o) as? T
    }.getOrNull() ?: default

    /** `Settings.Secure.USER_SETUP_COMPLETE` (hidden constant). */
    private const val USER_SETUP_COMPLETE = "user_setup_complete"
}
