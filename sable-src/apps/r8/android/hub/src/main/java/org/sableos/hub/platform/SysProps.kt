package org.sableos.hub.platform

/** Read-only system property access (same pattern as the other Sable apps). */
internal object SysProps {
    fun get(key: String): String =
        runCatching {
            Class
                .forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, key) as? String
        }.getOrNull().orEmpty()
}
