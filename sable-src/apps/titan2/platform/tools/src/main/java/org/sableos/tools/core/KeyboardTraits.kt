package org.sableos.tools.core

/** Where a profile's keyboard backlight state can be read from. */
enum class KeyboardLightSource {
    /** Public `InputDevice.getLightsManager()` lights (generic Android keyboards). */
    INPUT_LIGHTS,

    /**
     * Sable keyboard light properties (Settings 0901 writes them; `product/q25/init/sable-keyboard-backlight.rc` copies
     * them to the bbqX0kbd driver). No lights HAL is involved.
     */
    SABLE_PROPERTIES,

    /** The profile declares no keyboard light. */
    NONE
}

/**
 * Keyboard facts a profile declares for the diagnostics: which modifier keys exist (the critical text-entry test only
 * asks for these), whether digits live on the Alt layer, and how the keyboard light is driven.
 */
data class KeyboardTraits(
    val modifiers: Set<Modifier>,
    val digitsOnAltLayer: Boolean = false,
    val light: KeyboardLightSource = KeyboardLightSource.INPUT_LIGHTS
) {
    companion object {
        /**
         * Profiles with no keyboard evidence keep the spec's full list (HARDWARE_DIAGNOSTICS_AND_DIALER_CODES:
         * Alt, Sym, Fn) and the public lights API.
         */
        val GENERIC = KeyboardTraits(setOf(Modifier.ALT, Modifier.SYM, Modifier.FN))

        /** BlackBerry Classic layout: Alt, Shift, Sym, digits on the Alt layer, no Fn; light via Sable properties. */
        val BLACKBERRY_CLASSIC = KeyboardTraits(
            setOf(Modifier.ALT, Modifier.SHIFT, Modifier.SYM),
            digitsOnAltLayer = true,
            light = KeyboardLightSource.SABLE_PROPERTIES
        )
    }
}

enum class KeyboardLightState { ON, OFF, PRESENT, NOT_SUPPORTED, UNKNOWN }

data class KeyboardLightReading(val state: KeyboardLightState, val detail: String)

/** Keyboard light diagnostic, driven by [KeyboardTraits.light]. Pure: the Android layer passes the readings in. */
object KeyboardLight {
    /** Set to true by a product layer whose keyboard driver honours the properties. */
    const val PROP_SUPPORTED = "ro.sable.keyboard_backlight"

    /** "0" keeps the keys dark; anything else (including unset) is on, matching the driver default. */
    const val PROP_ENABLED = "persist.sys.sable.kbd_backlight"

    /** Driver brightness 1-255; unset or invalid reads as full brightness. */
    const val PROP_LEVEL = "persist.sys.sable.kbd_backlight_level"
    const val MAX_LEVEL = 255

    /**
     * @param prop system property reader ("" when unset or unreadable)
     * @param inputLights names of lights reported by input devices
     */
    fun read(
        source: KeyboardLightSource,
        prop: (String) -> String,
        inputLights: List<String>
    ): KeyboardLightReading = when (source) {
        KeyboardLightSource.NONE -> KeyboardLightReading(
            KeyboardLightState.NOT_SUPPORTED,
            "this profile declares no keyboard light"
        )

        KeyboardLightSource.INPUT_LIGHTS -> if (inputLights.isEmpty()) {
            KeyboardLightReading(
                KeyboardLightState.UNKNOWN,
                "no light exposed by any input device"
            )
        } else {
            KeyboardLightReading(KeyboardLightState.PRESENT, inputLights.joinToString(", "))
        }

        KeyboardLightSource.SABLE_PROPERTIES -> readProperties(prop)
    }

    private fun readProperties(prop: (String) -> String): KeyboardLightReading {
        val stored = prop(PROP_LEVEL).trim().toIntOrNull()
        val level = if (stored != null && stored in 1..MAX_LEVEL) stored else MAX_LEVEL
        return when {
            prop(PROP_SUPPORTED).trim() != "true" -> KeyboardLightReading(
                KeyboardLightState.UNKNOWN,
                "keyboard light control not declared by this build ($PROP_SUPPORTED unset)"
            )

            prop(PROP_ENABLED).trim() == "0" ->
                KeyboardLightReading(KeyboardLightState.OFF, "off (Settings > System > Keyboard)")

            else -> KeyboardLightReading(
                KeyboardLightState.ON,
                "on while the screen is on, level $level/$MAX_LEVEL (Settings > System > Keyboard)"
            )
        }
    }
}
