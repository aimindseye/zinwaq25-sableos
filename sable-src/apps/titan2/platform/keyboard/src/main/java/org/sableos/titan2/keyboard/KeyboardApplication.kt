package org.sableos.titan2.keyboard

import android.app.Application
import org.sableos.titan2.keyboard.core.KeyboardText
import org.sableos.titan2.theme.bindTo

/** Binds [KeyboardText] to the app's string resources once per process, so the phone's language is used. */
class KeyboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KeyboardText.t.bindTo(this)
    }
}
