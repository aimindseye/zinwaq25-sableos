package org.sableos.titan2.keyboard.android

import android.content.Context
import android.content.SharedPreferences
import org.sableos.titan2.keyboard.core.KeyboardConfig

/** Enter performs the field's Send action (core KeyShortcuts.enterSends); off by default. */
internal const val ENTER_SENDS = "enterSends"

/**
 * Stored in device-protected storage so the keyboard works at the lockscreen and during direct boot
 * (critical text entry).
 */
class KeyboardPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences(
            "sable_keyboard",
            Context.MODE_PRIVATE
        )

    fun load(): KeyboardConfig {
        val d = KeyboardConfig()
        return KeyboardConfig(
            autoCap = prefs.getBoolean(
                "autoCap",
                d.autoCap
            ),
            doubleSpacePeriod = prefs.getBoolean("doubleSpace", d.doubleSpacePeriod),
            longPressAlt = prefs.getBoolean(
                "longPressAlt",
                d.longPressAlt
            ),
            numericAutoAlt = prefs.getBoolean("numericAutoAlt", d.numericAutoAlt),
            navModeEnabled = prefs.getBoolean(
                "navMode",
                d.navModeEnabled
            ),
            altSpaceTogglesNav = prefs.getBoolean("altSpaceNav", d.altSpaceTogglesNav),
            doubleTapMs = prefs.getLong(
                "doubleTapMs",
                d.doubleTapMs
            ),
            oneShotTimeoutMs = prefs.getLong("oneShotTimeoutMs", d.oneShotTimeoutMs),
            holdCancelMs = prefs.getLong("holdCancelMs", d.holdCancelMs)
        )
    }

    var forceSoftKeyboard: Boolean
        get() = prefs.getBoolean("forceSoft", false)
        set(
            v
        ) {
            prefs.edit().putBoolean("forceSoft", v).apply()
        }

    /** Show the on-screen fallback for numeric/phone/numeric-password fields (Bluetooth pairing codes, PINs). */
    var softForNumeric: Boolean
        get() = prefs.getBoolean("softForNumeric", true)
        set(
            v
        ) {
            prefs.edit().putBoolean("softForNumeric", v).apply()
        }

    /**
     * Show the compact status strip (language, Shift/Alt/Sym/Nav, candidates) while the on-screen keyboard is
     * hidden. Turning it off restores the previous candidates-only behaviour.
     */
    var compactStrip: Boolean
        get() = prefs.getBoolean("compactStrip", true)
        set(v) {
            prefs.edit().putBoolean("compactStrip", v).apply()
        }

    fun setBool(key: String, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply()
    }
    fun getBool(key: String, d: Boolean) = prefs.getBoolean(key, d)
}
