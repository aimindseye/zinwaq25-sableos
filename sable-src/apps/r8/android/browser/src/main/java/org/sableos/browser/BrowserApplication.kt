package org.sableos.browser

import android.app.Application
import org.sableos.browser.core.BrowserText
import org.sableos.design.DesignText

/** Binds this app's text and the design library's to the string resources, so the phone's language is used. */
class BrowserApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        BrowserText.t.bindTo(this)
    }
}
