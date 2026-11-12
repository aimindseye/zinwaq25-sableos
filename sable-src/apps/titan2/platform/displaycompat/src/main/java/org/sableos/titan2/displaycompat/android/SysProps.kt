package org.sableos.titan2.displaycompat.android

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

    /** Sets a property (system UID); false when the platform refused. */
    fun set(key: String, value: String): Boolean = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("set", String::class.java, String::class.java).invoke(null, key, value)
        get(key) == value
    } catch (_: Throwable) {
        false
    }
}
