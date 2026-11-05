package org.sableos.titan2.printtogo

import android.app.Application
import org.sableos.titan2.printtogo.core.PrintToGoText
import org.sableos.titan2.theme.bindTo

/** Binds [PrintToGoText] to the app's string resources once per process, so the phone's language is used. */
class PrintToGoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrintToGoText.t.bindTo(this)
    }
}
