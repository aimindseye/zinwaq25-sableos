package org.sableos.media

import android.app.Application
import org.sableos.design.DesignText

/**
 * Binds this app's text and the shared design library's to the string resources once per process, so the
 * phone's language is used.
 */
class MediaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        MediaText.t.bindTo(this)
    }
}
