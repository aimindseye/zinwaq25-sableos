package org.sableos.titan2.keyboard.core

/**
 * Layer data. `verified=false` means the printed key legends of the target device have not been captured yet, so
 * Alt/Sym contents are a provisional convention, not the device's printed symbols
 * (KEYBOARD_AND_POINTER_PROFILE_MODEL: never guess across devices).
 */
data class KeyLayout(
    val id: String,
    val alt: Map<Char, String>,
    val sym: Map<Char, String>,
    val shiftPunct: Map<Char, Char>,
    val navMap: Map<Char, Key>,
    val verified: Boolean
) {
    companion object {
        private fun row(letters: String, out: String): Map<Char, String> =
            letters.toList().zip(out.toList()).associate { (k, v) ->
                k to
                    v.toString()
            }

        val SableProvisional = KeyLayout(
            id = "sable-provisional-qwerty",
            alt =
                row("qwertyuiop", "1234567890") + row("asdfghjkl", "@#$%&*-+=") +
                    row("zxcvbnm", "()!?:;/"),
            sym =
                row("qwertyuiop", "~`|\\{}[]<>") + row("asdfghjkl", "!^_\"'.,:;") +
                    row("zxcvbnm", "=+-?/*&"),
            shiftPunct = mapOf(
                ',' to '<', '.' to '>', '/' to '?', ';' to ':', '\'' to '"',
                '-' to '_', '=' to '+', '[' to '{', ']' to '}', '\\' to '|', '`' to '~'
            ),
            // Nav mode: IJKL diamond, U/O = Home/End, Y/H = PageUp/PageDown, N = Tab, M = Escape
            navMap = mapOf(
                'i' to Key.Up, 'k' to Key.Down, 'j' to Key.Left, 'l' to Key.Right,
                'u' to Key.Home, 'o' to Key.End,
                'y' to Key.PageUp, 'h' to Key.PageDown, 'n' to Key.Tab, 'm' to Key.Escape
            ),
            verified = false
        )

        /**
         * Titan 2 Alt layer transcribed from the printed secondary legends (owner photo + manual p.6).
         * All legends are read and owner-confirmed (Alt+U = -, Alt+I = _), but the layout stays unverified until a
         * device run, so they fall through to the platform's own Alt char (nativeAlt) instead of a guess.
         * Sym layer is not mapped: no printed Sym legends have been confirmed.
         */
        val Titan2PhotoRead = SableProvisional.copy(
            id = "titan2-printed-legends-partial",
            alt = mapOf(
                'q' to "0", 'w' to "1", 'e' to "2", 'r' to "3", 't' to "(", 'y' to ")",
                'a' to "@", 's' to "4", 'd' to "5", 'f' to "6", 'g' to "*", 'h' to "#", 'j' to "+",
                'k' to "\"", 'l' to "'",
                'z' to "!", 'x' to "7", 'c' to "8", 'v' to "9", 'm' to "?",
                'o' to "/", 'p' to ":", 'b' to ".", 'n' to ",",
                'u' to "-", 'i' to "_"
            ),
            sym = emptyMap(),
            verified = false
        )

        /**
         * Zinwa Q25 Alt layer transcribed from the device's own key character map (LineageOS device/xelex/Q25
         * configs/keychars/Q25_keyboard.kcm), which matches the printed BlackBerry Classic legends. Not yet run on a
         * Q25, so it stays unverified; the platform's own Alt char (nativeAlt) still wins when the kcm delivers one.
         * Sym is not mapped: the Classic Sym key's behaviour has not been captured.
         */
        val ZinwaQ25Kcm = SableProvisional.copy(
            id = "zinwa-q25-kcm-alt",
            alt = mapOf(
                'q' to "#", 'w' to "1", 'e' to "2", 'r' to "3", 't' to "(", 'y' to ")",
                'u' to "_", 'i' to "-", 'o' to "+", 'p' to "@",
                'a' to "*", 's' to "4", 'd' to "5", 'f' to "6", 'g' to "/", 'h' to ":", 'j' to ";",
                'k' to "'", 'l' to "\"",
                'z' to "7", 'x' to "8", 'c' to "9", 'v' to "?", 'b' to "!", 'n' to ",", 'm' to "."
            ),
            sym = emptyMap(),
            verified = false
        )

        /**
         * Layout for a Sable profile id (`ro.sable.profile.id`). Profiles never inherit each other's evidence: only
         * "titan2" gets the Titan 2 legends and only "zinwa-q25" gets the Q25 legends; Elite, Q27 and unknown devices
         * stay on the provisional convention until captured separately.
         */
        fun forProfile(profileId: String?): KeyLayout = when (profileId) {
            "titan2" -> Titan2PhotoRead
            "zinwa-q25" -> ZinwaQ25Kcm
            else -> SableProvisional
        }
    }
}
