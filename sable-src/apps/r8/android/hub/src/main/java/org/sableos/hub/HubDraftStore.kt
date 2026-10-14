package org.sableos.hub

import android.content.Context
import org.sableos.hub.policy.HubDrafts

/**
 * Unsent Hub drafts in app-private storage (backup is off), so a draft survives Back, leaving Hub
 * and process death. Keys come from [HubDrafts]; the text never leaves this device.
 */
class HubDraftStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): Map<String, String> {
        val order =
            preferences
                .getString(KEY_ORDER, null)
                .orEmpty()
                .split(ORDER_SEPARATOR)
                .filter(String::isNotEmpty)
        val drafts = LinkedHashMap<String, String>()
        order.forEach { key ->
            preferences.getString(PREFIX + key, null)?.takeIf(String::isNotEmpty)?.let { drafts[key] = it }
        }
        return drafts
    }

    fun save(drafts: Map<String, String>) {
        val editor = preferences.edit().clear()
        drafts.forEach { (key, body) -> editor.putString(PREFIX + key, body) }
        editor.putString(KEY_ORDER, drafts.keys.joinToString(ORDER_SEPARATOR))
        editor.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hub_drafts"
        const val KEY_ORDER = "order"
        const val PREFIX = "draft."

        // Keys are "sms:<digits>", "app:<id>" or "compose:<digits>": never contain a newline.
        const val ORDER_SEPARATOR = "\n"
    }
}
