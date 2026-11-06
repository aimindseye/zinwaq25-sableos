package org.sableos.phone.android

import android.content.Context

/** Sable Phone's own settings, in device-protected storage so the call screen reads them before first unlock. */
class PhoneSettings(context: Context) {
    private val prefs = context.applicationContext.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Space answers a ringing call (it never ends one). On by default, as on the Sable Lineage Dialer. */
    var spaceAnswers: Boolean
        get() = prefs.getBoolean(KEY_SPACE_ANSWERS, true)
        set(value) = prefs.edit().putBoolean(KEY_SPACE_ANSWERS, value).apply()

    /** The tab Phone opens on: 0 calls, 1 contacts, 2 dial pad, 3 favorites. */
    var tab: Int
        get() = prefs.getInt(KEY_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_TAB, value).apply()

    private companion object {
        const val FILE = "phone"
        const val KEY_SPACE_ANSWERS = "space_answers"
        const val KEY_TAB = "tab"
    }
}
