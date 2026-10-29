package org.sableos.calculator

import android.app.Application
import org.sableos.convert.ConvertText
import org.sableos.design.DesignText

/**
 * Binds this app's text and the shared design library's to the string resources once per process, so the
 * phone's language is used.
 */
class CalculatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignText.t.bindTo(this)
        CalculatorText.t.bindTo(this)
        ConvertText.t.bindTo(this)
    }
}
