package org.sableos.titan2.radiodiag.android

/** Reads a system property through reflection (SystemProperties is hidden). Returns "" when unreadable. */
object SysProps {
    fun get(key: String): String = try {
        Class.forName(
            "android.os.SystemProperties"
        ).getMethod("get", String::class.java).invoke(null, key) as? String
            ?: ""
    } catch (_: Throwable) {
        ""
    }
}
