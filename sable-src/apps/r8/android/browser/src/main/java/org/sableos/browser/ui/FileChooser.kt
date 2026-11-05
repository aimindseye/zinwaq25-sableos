package org.sableos.browser.ui

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback

/** Opens a page's file picker; the activity owns the result. */
fun interface FileChooser {
    fun choose(
        intent: Intent,
        callback: ValueCallback<Array<Uri>>,
    ): Boolean
}
