package org.sableos.calendar

import android.app.Application

/** Binds [CalendarText] to the app's string resources once per process, so the phone's language is used. */
class CalendarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CalendarText.t.bindTo(this)
    }
}
