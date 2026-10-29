package org.sableos.hub

import android.app.Application
import org.sableos.design.DesignText

/**
 * Binds this app's text and the shared design library's to the string resources once per process, so the
 * phone's language is used.
 */
class HubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        HubText.t.bindTo(this)
    }
}
