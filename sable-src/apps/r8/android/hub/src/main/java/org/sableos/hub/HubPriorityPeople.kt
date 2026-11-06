package org.sableos.hub

import android.content.Context

/**
 * Priority Hub senders ([org.sableos.hub.policy.HubPriority.personKey]), set from the row menu
 * and kept in Hub's own preferences on the phone. A whole app's priority is its Connected apps
 * Hub priority instead.
 */
class HubPriorityPeople(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            ConnectedAppsRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun load(): Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()

    /** Adds or removes [key]; Hub reloads its rows. */
    fun set(
        key: String,
        priority: Boolean,
    ) {
        val people = load()
        preferences.edit().putStringSet(KEY, if (priority) people + key else people - key).apply()
        appContext.contentResolver.notifyChange(ConnectedAppsRepository.HISTORY_URI, null)
    }

    private companion object {
        const val KEY = "priority_people"
    }
}
