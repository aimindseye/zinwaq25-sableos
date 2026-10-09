package org.sableos.titan2.displaycompat.android

import android.app.Application
import android.content.Context
import org.sableos.titan2.displaycompat.core.ProfileController
import org.sableos.titan2.displaycompat.core.ProfileStore

class DisplayCompatApp : Application() {
    lateinit var store: ProfileStore
        private set
    lateinit var controller: ProfileController
        private set

    override fun onCreate() {
        super.onCreate()
        store = PrefsProfileStore(this)
        controller =
            ProfileController(
                store,
                GatedPlatformBackend(this),
                miniSize = MiniSizeEvidence.read(this)
            )
        // SAFE_MODE_RESET_ALL_DISPLAY_PROFILES: booting to safe mode clears every per-app profile
        // so a bad profile cannot trap the user.
        if (packageManager.isSafeMode) controller.resetAll()
    }
}

val Context.displayCompat: DisplayCompatApp get() = applicationContext as DisplayCompatApp
