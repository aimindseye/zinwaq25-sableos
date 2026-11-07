package org.sableos.textreader.privacy

/** The clear band of the Privacy Screen, in screen pixels: rows [top, bottom). */
data class Band(
    val top: Int,
    val bottom: Int,
)

/**
 * Where the Privacy Screen leaves the screen readable: a full-width band around the focused item,
 * at least [minHeight] tall and centred on it, kept inside the screen. With nothing focused the
 * band sits in the middle of the screen. Pure, so it is unit-tested off the phone.
 */
object PrivacyBand {
    /** The band for a focused item spanning rows [focusTop, focusBottom), or null for no focus. */
    fun bandFor(
        focusTop: Int?,
        focusBottom: Int?,
        screenHeight: Int,
        minHeight: Int,
        padding: Int,
    ): Band {
        val height = screenHeight.coerceAtLeast(1)
        val wanted =
            if (focusTop == null || focusBottom == null || focusBottom <= focusTop) {
                minHeight
            } else {
                maxOf(minHeight, focusBottom - focusTop + 2 * padding)
            }
        val size = wanted.coerceIn(1, height)
        val centre =
            if (focusTop == null || focusBottom == null || focusBottom <= focusTop) {
                height / 2
            } else {
                (focusTop + focusBottom) / 2
            }
        val top = (centre - size / 2).coerceIn(0, height - size)
        return Band(top, top + size)
    }

    /** Whether the screen may be dimmed now: never while locked or while a call screen is in front. */
    fun mayShow(
        on: Boolean,
        locked: Boolean,
        screenOn: Boolean,
        frontPackage: String?,
    ): Boolean = on && !locked && screenOn && frontPackage !in CALL_PACKAGES

    /** Call screens the Privacy Screen never covers. */
    val CALL_PACKAGES =
        setOf(
            "org.sableos.phone",
            "com.android.dialer",
            "com.android.server.telecom",
            "com.android.incallui",
        )
}
