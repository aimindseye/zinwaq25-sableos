package org.sableos.titan2.keyboard.core

/**
 * Pure formatting for the Sable Key Probe so the log lines are testable. Raw ints are Android's KeyEvent
 * constants, copied as plain numbers.
 */
object KeyProbeFormat {
    private const val FIRST_PRINTABLE = 0x20
    private const val DELETE_CHAR = 0x7F
    private val META = listOf(
        0x1 to "SHIFT", 0x2 to "ALT", 0x4 to "SYM", 0x8 to "FN", 0x10 to "ALT_L", 0x20 to "ALT_R",
        0x40 to "SHIFT_L", 0x80 to "SHIFT_R",
        0x1000 to "CTRL", 0x10000 to "META", 0x100000 to "CAPS", 0x200000 to "NUM",
        0x400000 to "SCROLL"
    )

    fun metaNames(meta: Int): String = META.filter {
        meta and it.first != 0
    }.joinToString("+") { it.second }.ifEmpty { "-" }

    /**
     * U+0041 style; control and unmapped characters are shown as their code point, with a printable form when
     * there is one.
     */
    fun charText(unicode: Int): String = if (unicode ==
        0
    ) {
        "none"
    } else {
        "U+%04X".format(unicode) +
            if (unicode >= FIRST_PRINTABLE &&
                unicode != DELETE_CHAR
            ) {
                " '${String(Character.toChars(unicode))}'"
            } else {
                ""
            }
    }

    /** The physical key of a probed event: Android key name, key code and scan code. */
    data class ProbeKey(val name: String, val code: Int, val scan: Int)

    /** What the key produced: Android meta state bits and the unicode character (0 = none). */
    data class ProbeChar(val meta: Int, val unicode: Int)

    fun line(
        action: String,
        key: ProbeKey,
        produced: ProbeChar,
        deviceName: String,
        repeat: Int
    ): String {
        val meta = metaNames(produced.meta)
        val rep = if (repeat > 0) " rep=$repeat" else ""
        return "$action ${key.name}(${key.code}) scan=${key.scan} meta=$meta " +
            "char=${charText(produced.unicode)} dev=$deviceName$rep"
    }
}
