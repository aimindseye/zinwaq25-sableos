package org.sableos.titan2.displaycompat.core

/**
 * Compact `key=value;key=value` codec so the store needs no JSON dependency.
 * Unknown keys and bad values fall back to defaults.
 */
object ProfileCodec {
    fun encode(p: DisplayProfile): String = buildString {
        append("v=1;p=").append(p.profile.id)
        append(";o=").append(p.orientation.name)
        append(";s=").append(p.scaling.name)
        append(";b=").append(p.letterboxBackground.name)
        append(";k=").append(p.keyboardSafeArea.name)
        append(";n=").append(p.statusNav.name)
        append(";m=").append(if (p.fullscreenMediaException) 1 else 0)
        append(";x=").append(if (p.stretch) 1 else 0)
        p.customRatio?.let { append(";r=").append(it.w).append(':').append(it.h) }
    }

    fun decode(s: String?): DisplayProfile {
        val fields = parseFields(s)
        return if (fields == null) DisplayProfile.NATIVE else fromFields(fields)
    }

    /** Null means "not a v1 payload", which decodes to the native profile. */
    private fun parseFields(s: String?): Map<String, String>? {
        if (s.isNullOrBlank()) return null
        val m = s.split(';').mapNotNull { kv ->
            kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        return if (m["v"] == "1") m else null
    }

    private fun parseRatio(raw: String?): Ratio? {
        val parts = raw?.split(':')?.takeIf { it.size == 2 } ?: return null
        val w = parts[0].toIntOrNull()
        val h = parts[1].toIntOrNull()
        return if (w != null && h != null) usableRatio(w, h) else null
    }

    /** Only a positive ratio inside the range the validator accepts; anything else is dropped. */
    private fun usableRatio(w: Int, h: Int): Ratio? {
        if (w <= 0 || h <= 0) return null
        val r = Ratio(w, h)
        return if (r.value in Ratio.MIN..Ratio.MAX) r else null
    }

    /**
     * A payload whose profile id is missing or unknown is not trusted at all: decoding it field by field would keep
     * its orientation or stretch settings on an otherwise Native profile. A Custom profile without a usable ratio
     * is not a valid profile either.
     */
    private fun fromFields(m: Map<String, String>): DisplayProfile {
        val id = m["p"]
        val known =
            AspectProfile.entries.firstOrNull { it.id == id } ?: return DisplayProfile.NATIVE
        val decoded = build(m, known)
        val unusable = known == AspectProfile.Custom && decoded.customRatio == null
        return if (unusable) DisplayProfile.NATIVE else decoded
    }

    private fun build(m: Map<String, String>, known: AspectProfile): DisplayProfile =
        DisplayProfile(
            profile = known,
            orientation = m.enumOr("o", OrientationPref.Default),
            scaling = m.enumOr("s", ScalingPref.Default),
            letterboxBackground = m.enumOr("b", LetterboxBackground.System),
            keyboardSafeArea = m.enumOr("k", Tri.Default),
            statusNav = m.enumOr("n", BarsPref.Default),
            fullscreenMediaException = m["m"] != "0",
            customRatio = parseRatio(m["r"]),
            stretch = m["x"] == "1"
        )
}

private inline fun <reified E : Enum<E>> Map<String, String>.enumOr(key: String, d: E): E =
    enumValues<E>().firstOrNull { it.name == this[key] } ?: d
