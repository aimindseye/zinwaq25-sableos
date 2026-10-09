package org.sableos.tools.core

/**
 * DESIGN-KF-C privilege model, checked against the app's own AndroidManifest.xml by a unit test:
 * ```
 * READ_ONLY_BY_DEFAULT=YES            NO_AMBIENT_ROOT_DAEMON=YES
 * NO_SHARED_UID_FOR_CONVENIENCE=YES   NO_ACCESSIBILITY_AUTOMATION_FOR_PLATFORM_ACTIONS=YES
 * NO_ARBITRARY_SHELL_FRONTEND=YES     NETWORK_REQUIRED_FOR_REMOTE=NO
 * ```
 */
object PrivilegePolicy {
    /** Permissions Sable Tools may declare. Runtime ones are requested only at feature use. */
    val ALLOWED = setOf(
        Perm.CAMERA,
        Perm.RECORD_AUDIO,
        Perm.FINE_LOCATION,
        Perm.COARSE_LOCATION,
        Perm.ACTIVITY_RECOGNITION,
        "android.permission.TRANSMIT_IR",
        "android.permission.READ_BASIC_PHONE_STATE",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.VIBRATE",
        "android.permission.HIGH_SAMPLING_RATE_SENSORS"
    )

    /** Never: they would turn a read-only tool into a mutation console, a network service or a root helper. */
    val FORBIDDEN = setOf(
        "android.permission.INTERNET",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.WRITE_SETTINGS",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.READ_LOGS",
        "android.permission.DUMP",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.READ_PRIVILEGED_PHONE_STATE",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.FOREGROUND_SERVICE"
    )

    private val USES_PERMISSION = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")

    fun violations(manifestXml: String): List<String> {
        val out = mutableListOf<String>()
        val declared = USES_PERMISSION.findAll(manifestXml).map { it.groupValues[1] }.toList()
        declared.filter { it in FORBIDDEN }.forEach { out += "forbidden permission $it" }
        declared.filter { it !in ALLOWED && it !in FORBIDDEN }.forEach {
            out +=
                "unreviewed permission $it"
        }
        if ("android:sharedUserId" in manifestXml) out += "sharedUserId"
        if ("<service" in
            manifestXml
        ) {
            out += "service declared (no background/daemon component allowed)"
        }
        if ("android.accessibilityservice" in manifestXml) out += "accessibility service"
        if ("android:persistent=\"true\"" in manifestXml) out += "persistent app"
        return out
    }
}
