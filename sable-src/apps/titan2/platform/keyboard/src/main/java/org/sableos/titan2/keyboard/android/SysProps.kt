package org.sableos.titan2.keyboard.android

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
