package org.sableos.titan2.radiodiag.core

/**
 * One read-only observation. Every way a read can fail is its own state, so the report never shows an
 * unavailable value as if it were data and never turns "could not read" into "absent" or "off".
 */
sealed interface Reading {
    data class Value(val text: String) : Reading
    data object NeedsPermission : Reading
    data class Unavailable(val reason: String) : Reading
    data class NotSupported(val reason: String) : Reading
    data object Unknown : Reading

    /** Report text. A blank [Value] stays blank; [RadioReport] renders blanks as `-`. */
    fun text(): String = when (this) {
        is Value -> text
        NeedsPermission -> "NEEDS_PERMISSION"
        is Unavailable -> "UNAVAILABLE ($reason)"
        is NotSupported -> "NOT_SUPPORTED ($reason)"
        Unknown -> "UNKNOWN"
    }

    /** True only for a real observation; the other states carry no data. */
    val isData: Boolean get() = this is Value
}

/**
 * Names for platform integer constants. The numbers are the stable public TelephonyManager values, kept here so the
 * mapping is testable without Android and an unrecognised number is shown as such instead of being guessed.
 */
object RadioNames {
    private val sim = mapOf(
        0 to "UNKNOWN", 1 to "ABSENT", 2 to "PIN_REQUIRED", 3 to "PUK_REQUIRED",
        4 to "NETWORK_LOCKED",
        5 to "READY", 6 to "NOT_READY", 7 to "PERM_DISABLED", 8 to "CARD_IO_ERROR",
        9 to "CARD_RESTRICTED",
        10 to "LOADED", 11 to "PRESENT"
    )
    private val network = mapOf(
        0 to "UNKNOWN", 1 to "GPRS", 2 to "EDGE", 3 to "UMTS", 4 to "CDMA", 5 to "EVDO_0",
        6 to "EVDO_A",
        7 to "1xRTT", 8 to "HSDPA", 9 to "HSUPA", 10 to "HSPA", 11 to "IDEN", 12 to "EVDO_B",
        13 to "LTE",
        14 to "EHRPD", 15 to "HSPAP", 16 to "GSM", 17 to "TD_SCDMA", 18 to "IWLAN", 20 to "NR"
    )
    private val data = mapOf(
        -1 to "UNKNOWN",
        0 to "DISCONNECTED",
        1 to "CONNECTING",
        2 to "CONNECTED",
        3 to "SUSPENDED",
        4 to "DISCONNECTING",
        5 to "HANDOVER_IN_PROGRESS"
    )

    fun simState(v: Int): String = sim[v] ?: "UNRECOGNISED($v)"
    fun networkType(v: Int): String = network[v] ?: "UNRECOGNISED($v)"
    fun dataState(v: Int): String = data[v] ?: "UNRECOGNISED($v)"
}

/**
 * Cross-checks between observations that cannot all be true. A note means "the sources disagree or one is hidden",
 * never "the radio is broken": this app only sees public read-only state.
 */
object Consistency {
    private const val BASE = "telephony"
    private const val PHONE_PACKAGE = "com.android.phone"

    fun notes(features: Map<String, Boolean>, packages: Map<String, Boolean>): List<String> {
        val out = mutableListOf<String>()
        val base = features[BASE]
        features.filter { (name, on) ->
            on && name != BASE && name.startsWith("$BASE.")
        }.keys.sorted().forEach {
            if (base == false) out.add("$it is reported while $BASE is not")
        }
        if (base == true && packages[PHONE_PACKAGE] == false) {
            out.add("$BASE is reported but $PHONE_PACKAGE is missing or not visible to this app")
        }
        if (base == false && packages[PHONE_PACKAGE] == true) {
            out.add("$PHONE_PACKAGE is present but $BASE is not reported")
        }
        return out
    }
}
