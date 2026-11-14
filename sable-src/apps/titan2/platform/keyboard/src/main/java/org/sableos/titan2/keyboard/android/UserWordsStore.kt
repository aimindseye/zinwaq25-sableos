package org.sableos.titan2.keyboard.android

import android.content.Context
import org.sableos.titan2.keyboard.core.UserWords

/**
 * The user's learned words and phrases, one entry per typing language in the keyboard's device-protected preferences
 * (so the keyboard works before first unlock). Never sent anywhere, and removable at any time with [clearAll]. The
 * keyboard service and the settings screen share one process, so they share this cache.
 */
object UserWordsStore {
    private val cache = HashMap<String, UserWords>()

    fun forLanguage(context: Context, lang: String): UserWords = cache.getOrPut(lang) {
        UserWords().also { it.load(KeyboardPrefs(context).userWords(lang)) }
    }

    /** Writes every language that changed since it was last written. */
    fun save(context: Context) {
        val prefs = KeyboardPrefs(context)
        cache.filterValues { it.dirty }.forEach { (lang, words) ->
            prefs.setUserWords(lang, words.serialize())
            words.markSaved()
        }
    }

    /** Forgets everything learned, in memory and in the preferences. */
    fun clearAll(context: Context) {
        cache.values.forEach { it.clear() }
        cache.clear()
        KeyboardPrefs(context).clearUserWords()
    }
}
