package org.sableos.browser.core

import org.sableos.design.SableTexts

/**
 * Every line Sable Browser shows, in English, by string resource name. res/values/strings.xml
 * repeats them (BrowserTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 */
object BrowserText {
    val EN: Map<String, String> =
        mapOf(
            "address_hint" to "Search or type a web address",
            "keys_hint" to "L address · Space page · T top · B bottom · R reload · M pointer",
            "pointer_on" to "Trackpad pointer on",
            "pointer_off" to "Pointer off · the trackpad scrolls the page",
            "page_failed" to "This page couldn’t load. R tries again.",
            "no_app_for_link" to "No app can open this link.",
            "share_page" to "Share page",
            "downloading" to "Downloading %1\$s",
            "download_failed" to "This download couldn’t start.",
            "choose_file" to "Choose a file",
        )

    val t = SableTexts(EN)
}
