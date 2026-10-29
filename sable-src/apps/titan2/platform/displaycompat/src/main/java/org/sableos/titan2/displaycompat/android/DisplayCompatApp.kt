package org.sableos.titan2.displaycompat.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import org.sableos.titan2.displaycompat.core.AppStyleController
import org.sableos.titan2.displaycompat.core.DisplayCompatText
import org.sableos.titan2.displaycompat.core.DisplayTools
import org.sableos.titan2.displaycompat.core.ProfileController
import org.sableos.titan2.displaycompat.core.ProfileStore
import org.sableos.titan2.theme.bindTo

/**
 * Everything is created on first use: the boot receiver runs before the first unlock, when
 * credential-encrypted preferences cannot be opened, and touches only [HeldStore] there.
 */
class DisplayCompatApp : Application() {
    val store: ProfileStore by lazy { PrefsProfileStore(this) }
    val controller: ProfileController by lazy { ProfileController(store, GatedPlatformBackend()) }
    val appStyleStore: AppearanceStyleStore by lazy { AppearanceStyleStore(this) }
    val appStyle: AppStyleController by lazy { AppStyleController(appStyleStore) }
    val toolsSettings: PrefsToolsSettings by lazy { PrefsToolsSettings(this) }
    val tools: DisplayTools by lazy {
        DisplayTools(WmDisplayPort(contentResolver), toolsSettings, store, controller) { pkg ->
            TraitsResolver(this).resolve(pkg)
        }
    }

    override fun onCreate() {
        super.onCreate()
        DisplayCompatText.t.bindTo(this)
        // SAFE_MODE_RESET_ALL_DISPLAY_PROFILES: booting to safe mode clears every per-app profile
        // so a bad profile cannot trap the user. Before the first unlock the boot receiver does it.
        if (packageManager.isSafeMode && unlocked()) {
            controller.resetAll()
            tools.restoreAll()
        }
    }

    fun unlocked(): Boolean = getSystemService(UserManager::class.java)?.isUserUnlocked != false

    /**
     * Brings the display in line with the profiles and settings (after a save, a reset, a Display
     * tools change or the hotkey) and starts or stops [DisplayToolsService] as needed.
     */
    fun refreshDisplay() {
        if (reconcileNow()) {
            startService(Intent(this, DisplayToolsService::class.java))
        } else {
            stopService(Intent(this, DisplayToolsService::class.java))
        }
    }

    /** One reconcile; true while [DisplayToolsService] is needed. */
    fun reconcileNow(): Boolean {
        if (!unlocked()) return false
        runCatching { tools.reconcile(DeviceMoment.read(this)) }
            .onFailure { Log.w(TAG, "display reconcile failed", it) }
        return tools.needsService(GatedPlatformBackend.isOpen())
    }

    private companion object {
        const val TAG = "SableDisplayTools"
    }
}

val Context.displayCompat: DisplayCompatApp get() = applicationContext as DisplayCompatApp
