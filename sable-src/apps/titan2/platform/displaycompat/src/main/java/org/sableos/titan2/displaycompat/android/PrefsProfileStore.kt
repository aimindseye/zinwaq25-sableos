package org.sableos.titan2.displaycompat.android

import android.content.Context
import org.sableos.titan2.displaycompat.core.DisplayProfile
import org.sableos.titan2.displaycompat.core.ProfileCodec
import org.sableos.titan2.displaycompat.core.ProfileStore

/** SharedPreferences are per user, which gives the per-user per-package scope for free. Native is never stored. */
class PrefsProfileStore(context: Context) : ProfileStore {
    private val prefs = context.getSharedPreferences("display_profiles", Context.MODE_PRIVATE)
    override fun get(pkg: String): DisplayProfile = ProfileCodec.decode(prefs.getString(pkg, null))
    override fun put(pkg: String, p: DisplayProfile) {
        if (p.isNative) {
            prefs.edit().remove(
                pkg
            ).apply()
        } else {
            prefs.edit().putString(pkg, ProfileCodec.encode(p)).apply()
        }
    }
    override fun reset(pkg: String) {
        prefs.edit().remove(pkg).apply()
    }
    override fun resetAll() {
        prefs.edit().clear().apply()
    }
    override fun packages(): Set<String> = prefs.all.keys.toSet()
}
