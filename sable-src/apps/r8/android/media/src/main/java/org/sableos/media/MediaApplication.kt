package org.sableos.media

import android.app.Application

/** Binds [MediaText] to the app's string resources once per process, so the phone's language is used. */
class MediaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MediaText.t.bindTo(this)
    }
}
