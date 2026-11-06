package org.sableos.phone.android

import android.content.Context
import org.sableos.phone.core.SpeedDial

/** The speed dial list, in Sable Phone's own preferences (device-protected, so it works before first unlock). */
class SpeedDialStore(context: Context) {
    private val prefs = context.applicationContext.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(): Map<Char, SpeedDial.Entry> = SpeedDial.parse(prefs.getString(KEY, ""))

    fun get(letter: Char): SpeedDial.Entry? = SpeedDial.normalize(letter)?.let { all()[it] }

    fun put(letter: Char, entry: SpeedDial.Entry) {
        val l = SpeedDial.normalize(letter) ?: return
        save(all() + (l to entry))
    }

    fun remove(letter: Char) {
        val l = SpeedDial.normalize(letter) ?: return
        save(all() - l)
    }

    private fun save(entries: Map<Char, SpeedDial.Entry>) {
        prefs.edit().putString(KEY, SpeedDial.format(entries)).apply()
    }

    private companion object {
        const val FILE = "speed_dial"
        const val KEY = "entries"
    }
}
