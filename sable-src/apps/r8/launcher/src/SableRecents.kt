// SableOS: the BlackBerry 7 tray's Recent panel (owner, 2026-11-16): the apps opened last, newest
// first, so there is a way back to an app used a moment ago. Part of the SableOS home flavor.
package io.github.aimindseye.tenseven.model

/** The apps opened last as stable keys, newest first, at most [MAX]; saved beside the usage counts. */
object SableRecents {
    const val MAX = 12

    private const val SEPARATOR = "\n"

    /** What the Recent panel lists now; AppUsageRepository keeps it in step with what was saved. */
    @Volatile
    var keys: List<String> = emptyList()

    /** [recent] with [key] moved (or added) at the front; a blank key or one with a line break is ignored. */
    fun record(
        recent: List<String>,
        key: String,
    ): List<String> {
        if (key.isBlank() || key.any { it == '\n' || it == '\t' }) return recent
        return (listOf(key) + recent.filter { it != key }).take(MAX)
    }

    fun encode(recent: List<String>): String = recent.joinToString(SEPARATOR)

    /** Reads what [encode] wrote; blank lines and repeats are dropped, never thrown. */
    fun decode(text: String?): List<String> =
        text.orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.distinct().take(MAX)
}
