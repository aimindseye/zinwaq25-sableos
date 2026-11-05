package org.sableos.titan2.spaces

import android.app.Application
import org.sableos.titan2.spaces.core.SpacesText
import org.sableos.titan2.theme.bindTo

/** Binds [SpacesText] to the app's string resources once per process, so the phone's language is used. */
class SpacesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SpacesText.t.bindTo(this)
    }
}
