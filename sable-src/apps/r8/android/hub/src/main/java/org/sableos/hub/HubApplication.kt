package org.sableos.hub

import android.app.Application

/** Binds [HubText] to the app's string resources once per process, so the phone's language is used. */
class HubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HubText.t.bindTo(this)
    }
}
