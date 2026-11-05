package org.sableos.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the browser screen shows; the controller writes it, the screen reads it. */
class BrowserState {
    var url by mutableStateOf<String?>(null)
    var progress by mutableIntStateOf(FULL)
    var failed by mutableStateOf(false)

    /** The trackpad pointer: on (M turns it off), shown (hidden after a touch), where, over a link. */
    var pointerOn by mutableStateOf(true)
    var pointerShown by mutableStateOf(false)
    var pointerX by mutableFloatStateOf(0f)
    var pointerY by mutableFloatStateOf(0f)
    var overLink by mutableStateOf(false)

    /** A text field on the page has focus: keys type into it until Esc or Back. */
    var editing by mutableStateOf(false)

    /** The address bar has focus; [addressRequest] changes when L asks for it. */
    var addressFocused by mutableStateOf(false)
    var addressRequest by mutableIntStateOf(0)

    /** A short line in place of the key hint (pointer on/off, a download, an error). */
    var status by mutableStateOf<String?>(null)

    companion object {
        const val FULL = 100
    }
}
