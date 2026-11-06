package org.sableos.phone

import android.app.Application
import org.sableos.phone.core.PhoneText
import org.sableos.titan2.theme.bindTo

/** Binds [PhoneText] to the app's string resources once per process, so the phone's language is used. */
class PhoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneText.t.bindTo(this)
    }
}
