package org.sableos.phone.core

/** Dial pad codes the Phone app handles itself instead of calling: the IMEI code and `*#*#code#*#*` secret codes. */
object SpecialCodes {
    sealed interface Code {
        /** `*#06#`: show the IMEI (and MEID) of every SIM slot. */
        data object ShowImei : Code

        /** `*#*#1234#*#*`: sent to the apps listening for that secret code (sendDialerSpecialCode). */
        data class Secret(val code: String) : Code
    }

    private val SECRET = Regex("""^\*#\*#([0-9]+)#\*#\*$""")

    /** The code [typed] is as soon as it is complete, or null for an ordinary number (MMI and USSD codes are calls). */
    fun parse(typed: String): Code? = when {
        typed == "*#06#" -> Code.ShowImei
        else -> SECRET.matchEntire(typed)?.let { Code.Secret(it.groupValues[1]) }
    }
}
