package org.sableos.titan2.compass.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sable Compass shows, in English, by string resource name. res/values/strings.xml repeats
 * them (CompassTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 */
object CompassText {
    val EN: Map<String, String> =
        mapOf(
            "n" to "North",
            "ne" to "Northeast",
            "e" to "East",
            "se" to "Southeast",
            "s" to "South",
            "sw" to "Southwest",
            "w" to "West",
            "nw" to "Northwest",
            "dial_n" to "N",
            "dial_e" to "E",
            "dial_s" to "S",
            "dial_w" to "W",
            "true_mark" to "T",
            "degrees" to "%1\$d°",
            "magnetic" to "Magnetic north",
            "true_north" to "True north",
            "true_waiting" to
                "Finding your rough location for true north. Showing magnetic north " +
                "until then.",
            "true_denied" to "True north needs your rough location. Showing magnetic north.",
            "accuracy_high" to "Accuracy: high",
            "accuracy_medium" to "Accuracy: medium",
            "accuracy_low" to "Accuracy: low. Press C to calibrate.",
            "accuracy_unreliable" to "Accuracy: unreliable. Press C to calibrate.",
            "no_sensor" to "This phone has no compass sensor.",
            "flat_or_upright" to "Hold the phone flat, or upright facing the way you look.",
            "held" to "Held. H lets go.",
            "calibrate" to "Calibrate",
            "calibrate_body" to
                "Move the phone in a figure 8 a few times, turning it as you go. Keep " +
                "away from metal, magnets and speakers.",
            "calibrate_done" to "Calibrated.",
            "hint" to "T true north · C calibrate · H hold · Back leaves",
            "calibrate_hint" to "C or Back returns to the compass"
        )

    val t = SableTexts(EN)
}
