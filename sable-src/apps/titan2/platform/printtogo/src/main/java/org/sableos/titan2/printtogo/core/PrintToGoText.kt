package org.sableos.titan2.printtogo.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sable Print To Go shows, in English, by string resource name. res/values/strings.xml repeats
 * them (PrintToGoTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 */
object PrintToGoText {
    val EN: Map<String, String> =
        mapOf(
            "intro" to
                "Print from your computer to this phone. While Print To Go is on, the " +
                "phone is a printer on your Wi-Fi, and each print is saved as a PDF in " +
                "Documents/Print To Go.",
            "start" to "Start receiving",
            "stop" to "Stop",
            "off" to "Off. Nothing can print to this phone.",
            "ready" to
                "Ready as %1\$s. Choose it in your computer's print dialog. It turns off " +
                "after 10 minutes without a print.",
            "no_wifi" to
                "Connect to Wi-Fi first. Print To Go only listens on Wi-Fi, never on " +
                "mobile data.",
            "start_failed" to "Print To Go could not start. Try again.",
            "saved" to "Saved: %1\$s",
            "save_failed" to "%1\$s could not be saved.",
            "open_folder" to "Open saved prints",
            "running" to "Print To Go is on",
            "channel_running" to "Print To Go running",
            "channel_saved" to "Saved prints",
            "idle_off" to "Print To Go turned off after 10 minutes without a print.",
            "hint" to "Enter starts or stops · O opens saved prints · Back leaves"
        )

    val t = SableTexts(EN)
}
