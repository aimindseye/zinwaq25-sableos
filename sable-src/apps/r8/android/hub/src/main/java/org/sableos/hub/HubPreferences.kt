package org.sableos.hub

import android.content.Context
import org.sableos.hub.policy.AttentionSelection
import org.sableos.hub.policy.AttentionSelectionCodec
import org.sableos.hub.policy.HubMenuOptions

/**
 * Hub preferences that are not per-app Hub policy: Sable Attention selections, Hub private
 * mode and combined conversations. Attention is stored apart from Hub inclusion so that changing
 * one never changes the other, and neither touches Android delivery policy.
 */
class HubPreferences(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            ConnectedAppsRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    /** Sable Attention selections (Sable-driven outputs only), per Android user/profile + package. */
    fun loadAttentionSelections(): Map<ConnectedAppKey, AttentionSelection> =
        preferences
            .getStringSet(KEY_ATTENTION_RECORDS, emptySet())
            .orEmpty()
            .mapNotNull(AttentionSelectionCodec::decode)
            .associateBy(AttentionSelection::key)

    @Synchronized
    fun updateAttention(selection: AttentionSelection) {
        val records =
            preferences
                .getStringSet(KEY_ATTENTION_RECORDS, emptySet())
                .orEmpty()
                .filterNot { AttentionSelectionCodec.decode(it)?.key == selection.key }
                .toMutableSet()
        records += AttentionSelectionCodec.encode(selection)
        preferences.edit().putStringSet(KEY_ATTENTION_RECORDS, records).apply()
    }

    /** Hub private mode: hide senders and message text in Hub (DESIGN-KF-A "Private mode"). */
    fun privateMode(): Boolean = preferences.getBoolean(KEY_PRIVATE_MODE, false)

    fun setPrivateMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PRIVATE_MODE, enabled).apply()
        appContext.contentResolver.notifyChange(ConnectedAppsRepository.HISTORY_URI, null)
        appContext.contentResolver.notifyChange(HubSnapshotProvider.SNAPSHOT_URI, null)
        appContext.contentResolver.notifyChange(HubPrivacyProvider.STATE_URI, null)
    }

    /** Combine conversations: roll adjacent rows from the same person into one thread (default on). */
    fun combineConversations(): Boolean = preferences.getBoolean(KEY_COMBINE_CONVERSATIONS, true)

    fun setCombineConversations(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_COMBINE_CONVERSATIONS, enabled).apply()
        appContext.contentResolver.notifyChange(ConnectedAppsRepository.HISTORY_URI, null)
    }

    /** Each message on its own: one inbox row per received message (default off). */
    fun eachMessage(): Boolean = preferences.getBoolean(KEY_EACH_MESSAGE, false)

    fun setEachMessage(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EACH_MESSAGE, enabled).apply()
        appContext.contentResolver.notifyChange(ConnectedAppsRepository.HISTORY_URI, null)
    }

    /**
     * Row menu extras (Snooze, Remind me): both off until the user turns them on in Hub's
     * settings, which Sable Setup opens.
     */
    fun menuOptions(): HubMenuOptions =
        HubMenuOptions(
            snooze = preferences.getBoolean(KEY_MENU_SNOOZE, false),
            remindMe = preferences.getBoolean(KEY_MENU_REMIND_ME, false),
        )

    fun setMenuOptions(options: HubMenuOptions) {
        preferences
            .edit()
            .putBoolean(KEY_MENU_SNOOZE, options.snooze)
            .putBoolean(KEY_MENU_REMIND_ME, options.remindMe)
            .apply()
    }

    private companion object {
        const val KEY_ATTENTION_RECORDS = "attention_records"
        const val KEY_PRIVATE_MODE = "private_mode"
        const val KEY_COMBINE_CONVERSATIONS = "combine_conversations"
        const val KEY_EACH_MESSAGE = "each_message"
        const val KEY_MENU_SNOOZE = "menu_snooze"
        const val KEY_MENU_REMIND_ME = "menu_remind_me"
    }
}
