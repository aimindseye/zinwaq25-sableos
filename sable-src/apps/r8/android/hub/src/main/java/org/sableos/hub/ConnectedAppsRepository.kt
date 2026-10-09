package org.sableos.hub

import android.content.Context
import android.net.Uri

class ConnectedAppsRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun loadPolicies(): List<ConnectedAppPolicy> {
        val loaded =
            preferences
                .getStringSet(KEY_RECORDS, emptySet())
                .orEmpty()
                .mapNotNull(ConnectedAppPolicyCodec::decode)
                .sortedWith(
                    compareBy<ConnectedAppPolicy> { it.key.userSerial }
                        .thenBy { it.key.packageName },
                )
        updateCachedPolicies(loaded)
        return loaded
    }

    fun policyFor(key: ConnectedAppKey): ConnectedAppPolicy =
        cachedPolicyFor(key)
            ?: loadPolicies().firstOrNull { it.key == key }
            ?: ConnectedAppPolicy(key = key)

    fun cachedPolicyFor(key: ConnectedAppKey): ConnectedAppPolicy? = cachedPolicies[key]

    fun cachedPolicySnapshot(): Map<ConnectedAppKey, ConnectedAppPolicy> = cachedPolicies

    @Synchronized
    fun update(policy: ConnectedAppPolicy) {
        val normalized = policy.normalized()
        val records =
            preferences
                .getStringSet(KEY_RECORDS, emptySet())
                .orEmpty()
                .toMutableSet()

        records.removeAll { encoded ->
            ConnectedAppPolicyCodec.decode(encoded)?.key == normalized.key
        }

        if (!normalized.isDefaultDisabled()) {
            records += ConnectedAppPolicyCodec.encode(normalized)
        }

        preferences
            .edit()
            .putStringSet(KEY_RECORDS, records)
            .apply()

        updateCachedPolicy(normalized)

        appContext.contentResolver.notifyChange(POLICY_URI, null)
        appContext.contentResolver.notifyChange(HIDDEN_URI, null)
    }

    fun isIncluded(key: ConnectedAppKey): Boolean = policyFor(key).includeInMessages

    fun allowsQuickReply(key: ConnectedAppKey): Boolean = policyFor(key).allowQuickReply

    fun hiddenKeys(): Set<ConnectedAppKey> =
        loadPolicies()
            .asSequence()
            .filter { it.hideFromLauncher }
            .map { it.key }
            .toSet()

    private fun updateCachedPolicy(policy: ConnectedAppPolicy) {
        synchronized(CACHE_LOCK) {
            val next = cachedPolicies.toMutableMap()
            if (policy.isDefaultDisabled()) {
                next.remove(policy.key)
            } else {
                next[policy.key] = policy
            }
            cachedPolicies = next.toMap()
        }
    }

    private fun updateCachedPolicies(policies: List<ConnectedAppPolicy>) {
        synchronized(CACHE_LOCK) {
            cachedPolicies =
                policies.associateBy { policy ->
                    policy.key
                }
        }
    }

    companion object {
        @Volatile
        private var cachedPolicies: Map<ConnectedAppKey, ConnectedAppPolicy> =
            emptyMap()
        private val CACHE_LOCK = Any()

        const val AUTHORITY = "org.sableos.hub.connected_apps"
        const val COLUMN_PACKAGE_NAME = "package_name"
        const val COLUMN_USER_SERIAL = "user_serial"

        val POLICY_URI: Uri =
            Uri.parse("content://$AUTHORITY/policy")
        val HIDDEN_URI: Uri =
            Uri.parse("content://$AUTHORITY/hidden")
        val HISTORY_URI: Uri =
            Uri.parse("content://$AUTHORITY/history")

        private const val PREFERENCES_NAME = "sable_connected_apps"
        private const val KEY_RECORDS = "records"
    }
}
