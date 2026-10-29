package org.sableos.start.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.sableos.start.model.LetterShortcuts

/**
 * The letters held on the home screen to open an app, kept in Start's own preferences (never leaves the phone).
 * Personal-profile apps only: Start offers the choice nowhere else.
 */
class LetterShortcutRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): LetterShortcuts = LetterShortcuts.decode(preferences.getString(KEY_SHORTCUTS, null))

    fun assign(
        letter: Char,
        component: ComponentName,
    ) = save(load().assign(letter, component.flattenToString()))

    fun remove(component: ComponentName) = save(load().remove(component.flattenToString()))

    /** Opens the app on [letter]; false when none is set or it can no longer be started. */
    fun launch(letter: Char): Boolean {
        val component = load().componentFor(letter)?.let(ComponentName::unflattenFromString) ?: return false
        return runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    private fun save(shortcuts: LetterShortcuts) {
        preferences.edit().putString(KEY_SHORTCUTS, shortcuts.encode()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "letter_shortcuts"
        const val KEY_SHORTCUTS = "shortcuts"
    }
}
