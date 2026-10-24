package org.sableos.start.model

/**
 * Sable Start in the BlackBerry 10 layout (owner request, after the BB10 UI
 * Guidelines): Hub | Active Frames | app grid pages, with Phone · Search ·
 * Camera along the bottom. Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 */

/** One recent task shown as an Active Frame. Keys are plain values so the policy stays pure. */
data class ActiveFrame(
    val taskId: Int,
    val packageName: String,
    /** The task's top (else base) activity class, when known. */
    val className: String?,
    /** Android user id of the task (the same number AppEntry.user hashes to). */
    val userId: Int,
    /** False for a task Android kept in Recents but whose process is gone (after a reboot). */
    val running: Boolean,
)

object ActiveFrames {
    /** "Up to 8 recent/running apps." */
    const val MAX = 8

    /** Two columns; two rows fill the square screen, the rest scroll vertically. */
    const val COLUMNS = 2
    const val ROWS_PER_SCREEN = 2

    private const val KEYCODE_X = 52
    private const val KEYCODE_DEL = 67
    private const val MODIFIERS = 0x2 or 0x8 or 0x1000 or 0x10000

    /**
     * Recent tasks, most recent first, without Start itself, Recents and
     * System UI, one frame per task, at most [MAX]. Tasks of [hiddenUsers]
     * (the Private Space profile) never become frames, as in All Apps.
     */
    fun select(
        tasks: List<ActiveFrame>,
        excludedPackages: Set<String>,
        hiddenUsers: Set<Int> = emptySet(),
    ): List<ActiveFrame> =
        tasks
            .filterNot { it.packageName in excludedPackages || it.packageName.isEmpty() || it.userId in hiddenUsers }
            .distinctBy { it.taskId }
            .take(MAX)

    /** X or Backspace with no Alt, Fn, Ctrl or Meta closes the focused frame. */
    fun isCloseKey(
        keyCode: Int,
        metaState: Int,
    ): Boolean = (keyCode == KEYCODE_X || keyCode == KEYCODE_DEL) && metaState and MODIFIERS == 0

    /** The frame that takes focus after the one at [closed] goes: the next, else the previous, else none. */
    fun focusAfterClose(
        closed: Int,
        size: Int,
    ): Int? {
        val remaining = size - 1
        if (remaining <= 0) return null
        return closed.coerceAtMost(remaining - 1).coerceAtLeast(0)
    }
}

/**
 * The app grid pages: every app (Private Space apps stay in All Apps) in the
 * user's order, five columns of labelled, tile-free icons (Ripe 10's grid,
 * owner decision Q10, sized for the Q25's 411dp square), as many rows as fit.
 */
object AppGrid {
    /** Five per row: a 40-48dp icon and a one-line label in about 80dp on a 411dp square. */
    const val COLUMNS = 5
    const val MIN_ROWS = 3
    const val MAX_ROWS = 5

    const val MIN_ICON_DP = 32
    const val MAX_ICON_DP = 56

    /** The share of a cell's width the icon takes, so neighbours keep a gap (Ripe: 92px in ~150px). */
    private const val ICON_WIDTH_SHARE = 0.56f
    private const val SABLE_PREFIX = "Sable "

    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22

    /**
     * The saved order with uninstalled apps dropped and new apps appended; if
     * never saved, [preferredFirst] (the old Home apps, else the core apps)
     * then [installed] in its own (A to Z) order.
     */
    fun resolve(
        saved: List<String>?,
        preferredFirst: List<String>,
        installed: List<String>,
    ): List<String> {
        val present = installed.toSet()
        val head = (saved ?: preferredFirst).filter { it in present }.distinct()
        val taken = head.toSet()
        return head + installed.filterNot { it in taken }
    }

    /** Rows per page for [heightDp] of grid space and a cell at least [cellDp] tall. */
    fun rowsFor(
        heightDp: Int,
        cellDp: Int,
    ): Int = (heightDp / cellDp.coerceAtLeast(1)).coerceIn(MIN_ROWS, MAX_ROWS)

    fun perPage(rows: Int): Int = COLUMNS * rows

    /**
     * The icon size for a cell of [cellWidthDp] x [cellHeightDp]: a share of
     * the width, no taller than the height less the label ([labelDp]), kept
     * within [MIN_ICON_DP]..[MAX_ICON_DP].
     */
    fun iconSizeFor(
        cellWidthDp: Int,
        cellHeightDp: Int,
        labelDp: Int,
    ): Int = minOf((cellWidthDp * ICON_WIDTH_SHARE).toInt(), cellHeightDp - labelDp).coerceIn(MIN_ICON_DP, MAX_ICON_DP)

    /**
     * The one-line grid label: "Sable Calculator" shows as "Calculator", as
     * BB10 and Ripe name apps under their icons, unless another app on the
     * grid is already called that ([gridLabels], any case): "Sable Camera"
     * stays whole beside Aperture's "Camera". Search, All Apps and the spoken
     * description keep the full name.
     */
    fun gridLabel(
        label: String,
        gridLabels: Set<String> = emptySet(),
    ): String {
        val trimmed = label.trim()
        if (!trimmed.startsWith(SABLE_PREFIX)) return trimmed
        val short = trimmed.removePrefix(SABLE_PREFIX).trim()
        val taken = gridLabels.any { it.trim().equals(short, ignoreCase = true) }
        return if (short.isEmpty() || taken) trimmed else short
    }

    /** Number of grid pages for [count] apps (always at least one). */
    fun pageCount(
        count: Int,
        perPage: Int,
    ): Int = if (count <= 0 || perPage <= 0) 1 else (count + perPage - 1) / perPage

    fun <T> page(
        items: List<T>,
        page: Int,
        perPage: Int,
    ): List<T> = items.drop(page * perPage).take(perPage)

    /**
     * Moves the item at [index] by [delta] places (±1 sideways, ±[COLUMNS] up
     * and down), shifting the items in between like an icon dragged on the
     * grid. Out of range clamps to the first or last place.
     */
    fun move(
        keys: List<String>,
        index: Int,
        delta: Int,
    ): List<String> {
        if (index !in keys.indices || delta == 0) return keys
        val target = (index + delta).coerceIn(0, keys.lastIndex)
        if (target == index) return keys
        return keys.toMutableList().also { list -> list.add(target, list.removeAt(index)) }
    }

    /** Arrow key to grid places: Left/Right one, Up/Down one row; null for other keys. */
    fun arrowDelta(keyCode: Int): Int? =
        when (keyCode) {
            KEYCODE_DPAD_LEFT -> -1
            KEYCODE_DPAD_RIGHT -> 1
            KEYCODE_DPAD_UP -> -COLUMNS
            KEYCODE_DPAD_DOWN -> COLUMNS
            else -> null
        }
}

/**
 * Profiles beside an app's name (WORK_PROFILE_BADGE=REQUIRED) and Dual apps
 * (the private-space-dual-work design: "Enter primary · Shift+Enter dual").
 */
object AppProfiles {
    const val WORK = "work"
    const val PRIVATE = "private"

    /** Android's clone profile ("Dual apps" / app cloning). */
    const val DUAL = "dual"
    const val OTHER = "profile"

    /** The badge text: "Work", "Private", "Dual", "Profile". */
    fun badge(profileLabel: String): String = profileLabel.replaceFirstChar { it.uppercaseChar() }

    /**
     * The other instance of the same app for Shift+Enter: the Dual copy of a
     * personal app, or the personal app of a Dual copy. [componentOf] and
     * [profileOf] read an entry's component (flattened) and profile label.
     */
    fun <T> secondInstance(
        app: T,
        apps: List<T>,
        componentOf: (T) -> String,
        profileOf: (T) -> String?,
    ): T? {
        val wanted =
            when (profileOf(app)) {
                null -> DUAL
                DUAL -> null
                else -> return null
            }
        val component = componentOf(app)
        return apps.firstOrNull { it !== app && componentOf(it) == component && profileOf(it) == wanted }
    }
}
