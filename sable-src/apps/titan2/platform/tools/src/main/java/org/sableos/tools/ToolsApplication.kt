package org.sableos.tools

import android.app.Application
import org.sableos.titan2.theme.bindTo
import org.sableos.tools.core.ToolsText

/** Binds [ToolsText] to the app's string resources once per process, so the phone's language is used. */
class ToolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ToolsText.t.bindTo(this)
    }
}
