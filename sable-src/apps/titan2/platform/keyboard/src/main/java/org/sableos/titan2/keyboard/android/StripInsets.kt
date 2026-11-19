package org.sableos.titan2.keyboard.android

import android.inputmethodservice.InputMethodService
import android.view.View

/**
 * With only the candidates strip showing (physical keyboard), the system default lets the strip float
 * over the app, on top of the field being typed in. This tells the app to end above the strip instead,
 * so the field stays visible and the suggestions sit directly under it.
 */
object StripInsets {
    fun raise(insets: InputMethodService.Insets, strip: View?, inputViewShown: Boolean) {
        if (inputViewShown || strip == null || !strip.isShown) return
        val at = IntArray(2)
        strip.getLocationInWindow(at)
        insets.contentTopInsets = at[1]
        insets.visibleTopInsets = at[1]
    }
}
