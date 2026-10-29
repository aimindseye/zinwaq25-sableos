package org.sableos.calculator

import android.app.Application
import org.sableos.convert.ConvertText

/** Binds [CalculatorText] and [ConvertText] to the app's string resources once per process, so the phone's language is used. */
class CalculatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CalculatorText.t.bindTo(this)
        ConvertText.t.bindTo(this)
    }
}
