package org.sableos.notes

import android.app.Application
import org.sableos.notes.core.NotesText

/** Binds [NotesText] to the app's string resources once per process, so the phone's language is used. */
class NotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotesText.t.bindTo(this)
    }
}
