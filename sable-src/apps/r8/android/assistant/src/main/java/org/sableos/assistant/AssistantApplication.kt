package org.sableos.assistant

import android.app.Application
import org.sableos.assistant.core.AssistantText
import org.sableos.design.DesignText

/** Binds this app's text and the design library's to the string resources, so the phone's language is used. */
class AssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        AssistantText.t.bindTo(this)
    }
}
