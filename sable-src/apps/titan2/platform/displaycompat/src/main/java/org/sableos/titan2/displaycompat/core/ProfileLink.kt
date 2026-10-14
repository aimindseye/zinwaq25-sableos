package org.sableos.titan2.displaycompat.core

/**
 * Per-app entry from Settings (APP_DISPLAY_COMPATIBILITY_PROFILES_UX APP_INFO_PATH:
 * Settings > Apps > <App> > Display compatibility): [ACTION] with a `package:<name>` URI opens
 * that app's profile. Pure Kotlin so the parsing is unit-tested on a JVM.
 */
object ProfileLink {
    const val ACTION = "org.sableos.titan2.displaycompat.action.APP_PROFILE"
    private const val MAX_PACKAGE_LENGTH = 255
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    /**
     * The package to open from an intent's action, URI scheme and scheme-specific part, or null
     * when the intent is not a well-formed profile link (then the app list opens as usual).
     */
    fun packageFrom(action: String?, scheme: String?, schemeSpecificPart: String?): String? {
        if (action != ACTION || scheme != "package") return null
        val pkg = schemeSpecificPart?.trim().orEmpty()
        return pkg.takeIf { it.length <= MAX_PACKAGE_LENGTH && packageName.matches(it) }
    }
}
