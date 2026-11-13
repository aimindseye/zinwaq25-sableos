package org.sableos.textreader.privacy

import android.content.Context
import android.content.SharedPreferences

/**
 * How dark the Privacy Screen makes everything outside the clear band (owner, 2026-11-13). Kept in
 * the app's own preferences; the shade reads it whenever it changes. Pure apart from [prefs].
 */
object PrivacyDarkness {
    enum class Level(
        val alpha: Int,
    ) {
        /** Dim: the rest of the screen stays faintly readable to you. */
        Dim(170),

        /** Dark: the default since Sable 2.0. */
        Dark(240),

        /** Black: nothing but the band shows. */
        Black(255),
    }

    val DEFAULT = Level.Dark

    const val PREFS = "sable_privacy_screen"
    const val KEY = "darkness"

    /** The stored level, or [DEFAULT] for a missing or unknown name. */
    fun parse(name: String?): Level = Level.entries.firstOrNull { it.name == name } ?: DEFAULT

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): Level = parse(prefs(context).getString(KEY, null))

    fun write(
        context: Context,
        level: Level,
    ) {
        prefs(context).edit().putString(KEY, level.name).apply()
    }
}
