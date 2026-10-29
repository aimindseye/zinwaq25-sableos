package org.sableos.titan2.displaycompat.android

import android.content.ContentResolver
import android.graphics.Point
import android.os.Process
import android.provider.Settings
import org.sableos.titan2.displaycompat.core.CurrentDisplay
import org.sableos.titan2.displaycompat.core.DisplayCompatText.t
import org.sableos.titan2.displaycompat.core.DisplayPort
import org.sableos.titan2.displaycompat.core.DisplayStep
import org.sableos.titan2.displaycompat.core.SizePx

/**
 * The default display through `IWindowManager` (LineageOS 23.2 / Android 16), the calls `wm size`,
 * `wm density` and `wm set-ignore-orientation-request` make, plus the two rotation settings the
 * Quick Settings auto-rotate tile writes. The system UID holds WRITE_SECURE_SETTINGS (forced size
 * and density), SET_ORIENTATION (ignore orientation request) and WRITE_SETTINGS (rotation).
 */
class WmDisplayPort(private val resolver: ContentResolver) : DisplayPort {
    private val wm: Any by lazy {
        requireNotNull(
            Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService").invoke(null)
        ) { t("window_manager_unavailable") }
    }

    private fun method(name: String, vararg types: Class<*>) = WM_INTERFACE.getMethod(name, *types)

    private fun size(name: String): SizePx {
        val p = Point()
        method(name, INT, Point::class.java).invoke(wm, DEFAULT_DISPLAY, p)
        return SizePx(p.x, p.y)
    }

    private fun density(name: String): Int = method(name, INT).invoke(wm, DEFAULT_DISPLAY) as Int

    override fun initialSize(): SizePx = size("getInitialDisplaySize")

    override fun initialDensity(): Int = density("getInitialDisplayDensity")

    override fun current(): CurrentDisplay {
        val base = size("getBaseDisplaySize")
        val baseDensity = density("getBaseDisplayDensity")
        val ignore = method(
            "getIgnoreOrientationRequest",
            INT
        ).invoke(wm, DEFAULT_DISPLAY) as Boolean
        return CurrentDisplay(
            forcedSize = base.takeIf { it != initialSize() },
            forcedDensity = baseDensity.takeIf { it != initialDensity() },
            autoRotate =
                Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
            userRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, 0),
            ignoreOrientationRequest = ignore
        )
    }

    override fun run(step: DisplayStep) {
        when (step) {
            is DisplayStep.SetSize -> method("setForcedDisplaySize", INT, INT, INT)
                .invoke(wm, DEFAULT_DISPLAY, step.size.w, step.size.h)

            DisplayStep.ClearSize -> method(
                "clearForcedDisplaySize",
                INT
            ).invoke(wm, DEFAULT_DISPLAY)

            is DisplayStep.SetDensity -> method("setForcedDisplayDensityForUser", INT, INT, INT)
                .invoke(wm, DEFAULT_DISPLAY, step.density, userId())

            DisplayStep.ClearDensity -> method("clearForcedDisplayDensityForUser", INT, INT)
                .invoke(wm, DEFAULT_DISPLAY, userId())

            is DisplayStep.SetRotation -> {
                // The rotation first, so a lock never shows the old rotation for a frame.
                Settings.System.putInt(
                    resolver,
                    Settings.System.USER_ROTATION,
                    step.rotation.userRotation
                )
                Settings.System.putInt(
                    resolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    if (step.rotation.autoRotate) 1 else 0
                )
            }

            is DisplayStep.SetIgnoreOrientationRequest ->
                method(
                    "setIgnoreOrientationRequest",
                    INT,
                    BOOL
                ).invoke(wm, DEFAULT_DISPLAY, step.ignore)
        }
    }

    private fun userId(): Int = Process.myUid() / PER_USER_RANGE

    private companion object {
        const val DEFAULT_DISPLAY = 0
        const val PER_USER_RANGE = 100_000
        val INT: Class<*> = Int::class.javaPrimitiveType!!
        val BOOL: Class<*> = Boolean::class.javaPrimitiveType!!
        val WM_INTERFACE: Class<*> by lazy { Class.forName("android.view.IWindowManager") }
    }
}
