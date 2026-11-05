package org.sableos.tasks

import android.app.Application
import org.sableos.design.DesignText
import org.sableos.tasks.core.TasksText

/** Binds this app's text and the design library's to the string resources, so the phone's language is used. */
class TasksApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        TasksText.t.bindTo(this)
    }
}
