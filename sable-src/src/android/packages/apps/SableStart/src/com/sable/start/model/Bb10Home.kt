package org.sableos.start.model

/**
 * Sable Start in the BlackBerry 10 layout (owner request, after the BB10 UI
 * Guidelines): Hub | Active Frames | app grid pages, with Hub · page dots ·
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

/** What a frame's app is playing, from its media session (Android's own transport controls). */
data class FrameMedia(
    val packageName: String,
    val title: String?,
    val playing: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
)

/** The transport buttons on a frame. */
enum class FrameMediaCommand { Previous, PlayPause, Next }

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

    private const val KEYCODE_SPACE = 62
    private const val KEYCODE_MEDIA_PLAY_PAUSE = 85

    /**
     * The media session a frame shows controls for: the newest session of the frame's app, and
     * only for a frame of [currentUser] (Android lists the current user's sessions only).
     */
    fun mediaFor(
        frame: ActiveFrame,
        sessions: List<FrameMedia>,
        currentUser: Int,
    ): FrameMedia? =
        if (frame.userId != currentUser) null else sessions.firstOrNull { it.packageName == frame.packageName }

    /** Space or the play/pause key, with no modifier, plays or pauses the focused frame's media. */
    fun isPlayPauseKey(
        keyCode: Int,
        metaState: Int,
    ): Boolean = (keyCode == KEYCODE_SPACE || keyCode == KEYCODE_MEDIA_PLAY_PAUSE) && metaState and MODIFIERS == 0

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

/** BB10 Balance: the space All Apps shows. Private Space keeps its own section in Personal. */
enum class BalanceSpace { Personal, Work }

object Balance {
    /** Personal and Work when the phone has a work profile; nothing to switch otherwise. */
    fun spaces(hasWorkProfile: Boolean): List<BalanceSpace> = if (hasWorkProfile) BalanceSpace.entries else emptyList()

    /** Whether an app with [profileLabel] belongs in [space]: Work apps only in Work, the rest in Personal. */
    fun inSpace(
        profileLabel: String?,
        space: BalanceSpace,
    ): Boolean = (profileLabel == AppProfiles.WORK) == (space == BalanceSpace.Work)

    fun next(space: BalanceSpace): BalanceSpace =
        if (space == BalanceSpace.Personal) BalanceSpace.Work else BalanceSpace.Personal
}

/** A folder on the BB10 app grid (owner request 2026-11-05): a name and its apps (stable keys), in order. */
data class GridFolder(
    val id: Int,
    val name: String,
    val keys: List<String> = emptyList(),
) {
    /** The folder's place in the grid order. */
    val token: String get() = GridFolders.TOKEN_PREFIX + id
}

/** One place on the app grid: an app, or a folder with the apps of it that are installed. */
sealed interface GridItem {
    data class App(
        val key: String,
    ) : GridItem

    data class Folder(
        val folder: GridFolder,
        val keys: List<String>,
    ) : GridItem
}

/**
 * Folders on the BlackBerry 10 app grid. A folder sits in the grid order as its token
 * ("folder:3"); the apps in it leave the grid and show when it opens. An app is in at
 * most one folder. A folder with no installed app left is not shown. Saved as one line
 * per folder (id, name and keys separated by tabs and commas); a damaged line is dropped.
 */
object GridFolders {
    const val TOKEN_PREFIX = "folder:"
    const val MAX = 12
    const val MAX_APPS = 25

    private const val LINE_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "\t"
    private const val KEY_SEPARATOR = ","

    /** The folder [key] is in, or null. */
    fun folderOf(
        folders: List<GridFolder>,
        key: String,
    ): GridFolder? = folders.firstOrNull { key in it.keys }

    /**
     * The grid's places: [AppGrid.resolve] over the apps outside folders, with each folder
     * that still holds an installed app at its saved place (a new folder goes last).
     */
    fun layout(
        saved: List<String>?,
        preferredFirst: List<String>,
        installed: List<String>,
        folders: List<GridFolder>,
    ): List<GridItem> {
        val present = installed.toSet()
        val shown = folders.associateBy { it.token }.mapValues { (_, f) -> f.keys.filter { it in present }.distinct() }
        val live = shown.filterValues { it.isNotEmpty() }
        val inFolders = live.values.flatten().toSet()
        val order = (saved ?: preferredFirst).filter { it in live || (it in present && it !in inFolders) }.distinct()
        val taken = order.toSet()
        val full = order + live.keys.filterNot { it in taken } + installed.filterNot { it in taken || it in inFolders }
        val byToken = folders.associateBy { it.token }
        return full.distinct().map { key ->
            val folder = byToken[key]
            if (folder == null) GridItem.App(key) else GridItem.Folder(folder, live.getValue(key))
        }
    }

    /** The grid order (keys and folder tokens) of [items], as Edit Home saves it. */
    fun order(items: List<GridItem>): List<String> =
        items.map {
            when (it) {
                is GridItem.App -> it.key
                is GridItem.Folder -> it.folder.token
            }
        }

    /**
     * A new folder called [name] holding [key], at the app's place in [order] (an app taken
     * from another folder leaves it, and a folder left empty goes). Unchanged (null) when
     * there are [MAX] folders, the name is empty or already used.
     */
    fun create(
        folders: List<GridFolder>,
        order: List<String>,
        key: String,
        name: String,
    ): Pair<List<GridFolder>, List<String>>? {
        val clean = Bb7Tabs.cleanName(name) ?: return null
        if (folders.size >= MAX || folders.any { it.name.equals(clean, ignoreCase = true) }) return null
        val folder = GridFolder(id = (folders.maxOfOrNull { it.id } ?: 0) + 1, name = clean, keys = listOf(key))
        val left = folders.map { it.copy(keys = it.keys - key) }.filter { it.keys.isNotEmpty() }
        val at = order.indexOf(key)
        val nextOrder =
            if (at < 0) {
                order + folder.token
            } else {
                order.toMutableList().also { it[at] = folder.token }
            }
        return (left + folder) to nextOrder
    }

    /**
     * Moves [key] into the folder [id] (out of any other, which goes when left empty);
     * unchanged when that folder is full or missing.
     */
    fun add(
        folders: List<GridFolder>,
        id: Int,
        key: String,
    ): List<GridFolder> {
        val target = folders.firstOrNull { it.id == id } ?: return folders
        if (key in target.keys || target.keys.size >= MAX_APPS) return folders
        return folders
            .map { if (it.id == id) it.copy(keys = it.keys + key) else it.copy(keys = it.keys - key) }
            .filter { it.keys.isNotEmpty() }
    }

    /**
     * Takes [key] out of its folder and puts it on the grid right after the folder. A folder
     * left empty goes, and so does its place in the order.
     */
    fun remove(
        folders: List<GridFolder>,
        order: List<String>,
        key: String,
    ): Pair<List<GridFolder>, List<String>> {
        val folder = folderOf(folders, key) ?: return folders to order
        val left = folders.map { if (it.id == folder.id) it.copy(keys = it.keys - key) else it }
        val emptied = left.first { it.id == folder.id }.keys.isEmpty()
        val at = order.indexOf(folder.token)
        val withApp =
            when {
                key in order -> order
                at < 0 -> order + key
                else -> order.toMutableList().also { it.add(at + 1, key) }
            }
        return if (emptied) {
            left.filterNot { it.id == folder.id } to withApp.filterNot { it == folder.token }
        } else {
            left to withApp
        }
    }

    /** Renames the folder [id]; unchanged when the name is empty or another folder has it. */
    fun rename(
        folders: List<GridFolder>,
        id: Int,
        name: String,
    ): List<GridFolder> {
        val clean = Bb7Tabs.cleanName(name) ?: return folders
        if (folders.any { it.id != id && it.name.equals(clean, ignoreCase = true) }) return folders
        return folders.map { if (it.id == id) it.copy(name = clean) else it }
    }

    /** Removes the folder [id]; its apps go back on the grid where the folder was, in its order. */
    fun ungroup(
        folders: List<GridFolder>,
        order: List<String>,
        id: Int,
    ): Pair<List<GridFolder>, List<String>> {
        val folder = folders.firstOrNull { it.id == id } ?: return folders to order
        val at = order.indexOf(folder.token)
        val apps = folder.keys.filterNot { it in order }
        val nextOrder =
            if (at < 0) {
                order + apps
            } else {
                order.take(at) + apps + order.drop(at + 1)
            }
        return folders.filterNot { it.id == id } to nextOrder
    }

    fun encode(folders: List<GridFolder>): String =
        folders.joinToString(LINE_SEPARATOR) {
            listOf(it.id.toString(), it.name, it.keys.joinToString(KEY_SEPARATOR)).joinToString(FIELD_SEPARATOR)
        }

    fun decode(text: String?): List<GridFolder> {
        val folders = mutableListOf<GridFolder>()
        text.orEmpty().split(LINE_SEPARATOR).forEach { line ->
            val folder = parse(line)
            val clash = folder != null && folders.any { it.id == folder.id || it.name.equals(folder.name, ignoreCase = true) }
            if (folder != null && !clash && folders.size < MAX) {
                val claimed = folders.flatMap { it.keys }.toSet()
                folders += folder.copy(keys = folder.keys.filterNot { it in claimed }.take(MAX_APPS))
            }
        }
        return folders
    }

    private fun parse(line: String): GridFolder? {
        val fields = line.split(FIELD_SEPARATOR)
        if (fields.size != 3) return null
        val id = fields[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val name = Bb7Tabs.cleanName(fields[1]) ?: return null
        return GridFolder(id, name, fields[2].split(KEY_SEPARATOR).filter { it.isNotBlank() }.distinct())
    }
}
