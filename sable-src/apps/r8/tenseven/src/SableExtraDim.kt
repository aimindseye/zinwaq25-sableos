// SableOS: dimmer than the panel's lowest step (owner, 2026-11-12). The Q25 backlight has about 25 hardware
// steps, and its lowest is still bright. With the slider at the bottom, the home turns on Android's Extra dim
// (reduce bright colors) and turns it off again when the slider moves up. Extra dim that the person turned on
// themselves is never touched. Part of the SableOS home flavor.
package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

object SableExtraDim {
    /** The slider's bottom: Android's 0..255 brightness at or below this counts as "lowest". */
    const val BOTTOM = 2

    /** Extra dim strength used when the person never set one (0..100). */
    const val DEFAULT_LEVEL = 50

    const val PREFS = "sable_extra_dim"
    const val KEY_ENABLED = "enabled"
    private const val KEY_OURS = "ours"
    private const val ACTIVATED = "reduce_bright_colors_activated"
    private const val LEVEL = "reduce_bright_colors_level"

    enum class Action { TurnOn, TurnOff }

    /** What to do now. [ours] is true when the home itself turned Extra dim on. */
    fun decide(
        enabled: Boolean,
        manual: Boolean,
        brightness: Int,
        active: Boolean,
        ours: Boolean,
    ): Action? {
        val bottom = enabled && manual && brightness <= BOTTOM
        return when {
            bottom && !active -> Action.TurnOn
            !bottom && active && ours -> Action.TurnOff
            else -> null
        }
    }

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    apply(app)
                }
            }
        val resolver = app.contentResolver
        runCatching {
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, observer)
        }
        apply(app)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply(context.applicationContext)
    }

    fun apply(context: Context) {
        runCatching {
            val resolver = context.contentResolver
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val manual =
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            val brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 255)
            val active = Settings.Secure.getInt(resolver, ACTIVATED, 0) == 1
            val ours = prefs.getBoolean(KEY_OURS, false)
            when (decide(isEnabled(context), manual, brightness, active, ours)) {
                Action.TurnOn -> {
                    if (Settings.Secure.getString(resolver, LEVEL) == null) {
                        Settings.Secure.putInt(resolver, LEVEL, DEFAULT_LEVEL)
                    }
                    Settings.Secure.putInt(resolver, ACTIVATED, 1)
                    prefs.edit().putBoolean(KEY_OURS, true).apply()
                }
                Action.TurnOff -> {
                    Settings.Secure.putInt(resolver, ACTIVATED, 0)
                    prefs.edit().putBoolean(KEY_OURS, false).apply()
                }
                null -> if (!active && ours) prefs.edit().putBoolean(KEY_OURS, false).apply()
            }
        }
    }
}
