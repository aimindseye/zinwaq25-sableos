package org.sableos.titan2.vault

import android.app.Application
import org.sableos.titan2.theme.bindTo
import org.sableos.titan2.vault.core.VaultLines

/** Binds [VaultLines] to the app's string resources once per process, so the phone's language is used. */
class VaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VaultLines.t.bindTo(this)
    }
}
