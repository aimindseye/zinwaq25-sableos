package org.sableos.start.model

/**
 * R9 daily-driver core apps across Android bases.
 *
 * Sable Start keys its approved glyphs, identity accents, Live rows and
 * default Start order by one canonical package per core role (the
 * GrapheneOS-era package names SableOS R9 was designed on). Other bases ship
 * a different app for the same role: LineageOS has Glimpse for Photos,
 * Aperture for Camera and Jelly for the browser. This table maps those to the
 * canonical key so the same Sable presentation applies, without branching on
 * a device model.
 *
 * Only identity is shared: the app keeps its own label (a Jelly browser is
 * never called Vanadium), component, launch and permissions.
 *
 * Pure Kotlin, no android.* imports; unit tested in
 * apps/r8/android/sablestart-presentation-check.
 */
object CoreAppIdentity {
    const val PHOTOS = "com.android.gallery3d"
    const val CAMERA = "app.grapheneos.camera"
    const val BROWSER = "app.vanadium.browser"
    const val FILES = "com.android.documentsui"
    const val CLOCK = "com.android.deskclock"

    /** Equivalent package -> canonical package. Canonical packages map to themselves implicitly. */
    private val EQUIVALENTS: Map<String, String> =
        mapOf(
            // LineageOS 23.2 (Q25): Gallery2 stays installed for crop and the
            // editor but its launcher entry is disabled; Glimpse is the gallery.
            "org.lineageos.glimpse" to PHOTOS,
            "org.lineageos.aperture" to CAMERA,
            "org.lineageos.jelly" to BROWSER,
        )

    /** The canonical core package for [packageName], or [packageName] itself. */
    fun canonicalPackage(packageName: String): String = EQUIVALENTS[packageName] ?: packageName

    /** True when [packageName] is the canonical package or a known equivalent of it. */
    fun isRole(packageName: String, canonical: String): Boolean = canonicalPackage(packageName) == canonical
}
