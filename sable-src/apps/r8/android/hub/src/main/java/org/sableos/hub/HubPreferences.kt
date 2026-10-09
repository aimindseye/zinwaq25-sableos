package org.sableos.hub

import android.content.Context
import org.sableos.hub.policy.AttentionSelection
import org.sableos.hub.policy.AttentionSelectionCodec

/**
 * Hub preferences that are not per-app Hub policy: Sable Attention selections and Hub private
 * mode. Attention is stored apart from Hub inclusion so that changing one never changes the
 * other, and neither touches Android delivery policy.
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
    }

    private companion object {
        const val KEY_ATTENTION_RECORDS = "attention_records"
        const val KEY_PRIVATE_MODE = "private_mode"
    }
}
