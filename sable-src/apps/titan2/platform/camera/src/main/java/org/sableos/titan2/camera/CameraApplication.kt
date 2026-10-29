package org.sableos.titan2.camera

import android.app.Application
import org.sableos.titan2.camera.core.CameraText
import org.sableos.titan2.theme.bindTo

/** Binds [CameraText] to the app's string resources once per process, so the phone's language is used. */
class CameraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CameraText.t.bindTo(this)
    }
}
