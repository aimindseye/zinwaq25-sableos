package org.sableos.titan2.compass

import android.app.Application
import org.sableos.titan2.compass.core.CompassText
import org.sableos.titan2.theme.bindTo

/** Binds [CompassText] to the app's string resources once per process, so the phone's language is used. */
class CompassApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CompassText.t.bindTo(this)
    }
}
