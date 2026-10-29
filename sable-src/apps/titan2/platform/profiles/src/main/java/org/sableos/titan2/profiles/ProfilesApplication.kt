package org.sableos.titan2.profiles

import android.app.Application
import org.sableos.titan2.profiles.core.ProfilesText
import org.sableos.titan2.theme.bindTo

/** Binds [ProfilesText] to the app's string resources once per process, so the phone's language is used. */
class ProfilesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProfilesText.t.bindTo(this)
    }
}
