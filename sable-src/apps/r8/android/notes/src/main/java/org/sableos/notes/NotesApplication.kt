package org.sableos.notes

import android.app.Application
import org.sableos.design.DesignText
import org.sableos.notes.core.NotesText

/**
 * Binds this app's text and the shared design library's to the string resources once per process, so the
 * phone's language is used.
 */
class NotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        NotesText.t.bindTo(this)
    }
}
