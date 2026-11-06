package org.sableos.assistant.platform

import android.content.Context
import org.sableos.assistant.core.AssistantProvider
import org.sableos.assistant.core.AssistantSettings

/**
 * Settings in Cosgrove's own preferences. The app is never backed up (allowBackup=false), so an
 * API key stays on this phone. Conversations are never stored.
 */
class AssistantStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): AssistantSettings =
        AssistantSettings(
            name = preferences.getString(KEY_NAME, null) ?: AssistantSettings.DEFAULT_NAME,
            provider = AssistantProvider.byName(preferences.getString(KEY_PROVIDER, null)),
            address = preferences.getString(KEY_ADDRESS, null).orEmpty(),
            apiKey = preferences.getString(KEY_API_KEY, null).orEmpty(),
            model = preferences.getString(KEY_MODEL, null).orEmpty(),
        )

    fun save(settings: AssistantSettings) {
        preferences
            .edit()
            .putString(KEY_NAME, settings.name)
            .putString(KEY_PROVIDER, settings.provider?.name)
            .putString(KEY_ADDRESS, settings.address)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_MODEL, settings.model)
            .apply()
        // Start's Command reads the name for its /word.
        appContext.contentResolver.notifyChange(PersonaProvider.URI, null)
    }

    private companion object {
        const val NAME = "assistant"
        const val KEY_NAME = "name"
        const val KEY_PROVIDER = "provider"
        const val KEY_ADDRESS = "address"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
    }
}
