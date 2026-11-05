package org.sableos.titan2.spaces.core

/** Where the Work space stands, seen from the main user. */
enum class SpaceState { NotAllowed, None, On, Paused }

/** An app the user can add to the Work space. */
data class AppChoice(val packageName: String, val label: String)

/** What Sable Spaces does with the Work space, without Android, so it can be tested. */
object WorkSpace {
    /** Sable Setup's Work space row opens this. */
    const val ACTION_OPEN = "org.sableos.spaces.action.WORK_SPACE"

    /** Forwarded from the main user into the Work space; both are confirmed there first. */
    const val ACTION_ADD_APP = "org.sableos.spaces.action.ADD_APP"
    const val ACTION_REMOVE = "org.sableos.spaces.action.REMOVE"
    const val EXTRA_PACKAGE = "org.sableos.spaces.extra.PACKAGE"
    const val EXTRA_LABEL = "org.sableos.spaces.extra.LABEL"

    private const val MAX_PACKAGE_LENGTH = 255
    private const val MAX_LABEL_LENGTH = 80
    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    fun state(hasWorkSpace: Boolean, paused: Boolean, allowed: Boolean): SpaceState = when {
        hasWorkSpace && paused -> SpaceState.Paused
        hasWorkSpace -> SpaceState.On
        allowed -> SpaceState.None
        else -> SpaceState.NotAllowed
    }

    /** The line under the title for [state]. */
    fun detailKey(state: SpaceState): String = when (state) {
        SpaceState.NotAllowed -> "not_allowed"
        SpaceState.None -> "intro"
        SpaceState.On -> "ready"
        SpaceState.Paused -> "paused"
    }

    /** A request from another profile names a package; anything that is not a package name is refused. */
    fun isPackageName(value: String?): Boolean =
        value != null && value.length <= MAX_PACKAGE_LENGTH && PACKAGE_NAME.matches(value)

    /** The caller's label is shown beside the package name, never instead of it, and kept short. */
    fun shownLabel(label: String?, packageName: String): String =
        label?.trim()?.take(MAX_LABEL_LENGTH)?.takeIf { it.isNotEmpty() } ?: packageName

    /** Apps the user can still add: not Sable Spaces itself, not already in Work, one row per app, by name. */
    fun addable(apps: List<AppChoice>, ownPackage: String, inWork: Set<String>): List<AppChoice> =
        apps
            .filter { it.packageName != ownPackage && it.packageName !in inWork }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    /** The selection after Up or Down, kept on the list. */
    fun move(selected: Int, delta: Int, size: Int): Int =
        if (size == 0) 0 else (selected + delta).coerceIn(0, size - 1)
}
