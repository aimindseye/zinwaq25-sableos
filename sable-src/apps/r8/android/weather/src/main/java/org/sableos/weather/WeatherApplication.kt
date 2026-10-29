package org.sableos.weather

import android.app.Application

/** Binds [WeatherText] to the app's string resources once per process, so the phone's language is used. */
class WeatherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WeatherText.t.bindTo(this)
    }
}
