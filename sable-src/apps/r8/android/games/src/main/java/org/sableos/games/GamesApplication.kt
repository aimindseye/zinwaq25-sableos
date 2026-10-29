package org.sableos.games

import android.app.Application

/** Binds [GamesText] to the app's string resources once per process, so the phone's language is used. */
class GamesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GamesText.t.bindTo(this)
    }
}
