package org.sableos.messages

import android.app.Application
import org.sableos.design.DesignText
import org.sableos.messages.core.MessagesText

/** Binds [MessagesText] to the app's string resources once per process, for screens, receivers and notifications. */
class MessagesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        MessagesText.t.bindTo(this)
    }
}
