package org.sableos.start.ui

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sable.start.model.StartText.t
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import org.sableos.design.SableAppearance
import org.sableos.design.SableTheme
import org.sableos.start.live.LiveAvailability
import org.sableos.start.model.AppEntry
import org.sableos.start.model.Bb7Folders
import org.sableos.start.model.Bb7Home
import org.sableos.start.model.Bb7IconSize
import org.sableos.start.model.Bb7Layout
import org.sableos.start.model.Bb7Naming
import org.sableos.start.model.Bb7Panel
import org.sableos.start.model.Bb7Shortcuts
import org.sableos.start.model.Bb7Swipe
import org.sableos.start.model.Bb7TrayView
import org.sableos.start.model.GridFolder
import org.sableos.start.model.GridFolders
import org.sableos.start.model.HomeApps
import org.sableos.start.model.HomeHeader
import org.sableos.start.model.SpeedDialKeys
import org.sableos.start.model.StartTypeToSearch
import org.sableos.start.model.inLauncherUser
import org.sableos.start.model.stableKey
import org.sableos.start.platform.LetterShortcutRepository
import org.sableos.start.platform.LiveSurfaceRepository
import org.sableos.start.platform.SpeedDialRepository

/**
 * Sable Start in the BlackBerry 7 style ([org.sableos.start.model.HomeStyle.Bb7]): the banner
 * (status dials, date, weather and unread count; the time is the status bar's, so it is not shown
 * twice) on the wallpaper, and a tray along the bottom with one row of Favorites that opens into
 * the whole panel. Keys as on BlackBerry 7: arrows move, Enter opens, Up opens the tray and
 * Escape closes it, Space switches panels (Favorites, All, Downloads, then your folders), typing
 * opens Command as on the BlackBerry 10 home (and as BlackBerry 7's typing opened Universal Search),
 * Menu opens the home options, Alt+Space opens Command. Folders are the BlackBerry 10 grid's, so a
 * group of apps is the same on both homes; shortcuts can sit on the wallpaper between the banner
 * and the tray (Add to Home screen). As on the BlackBerry 10 home, Hub is to the left and the apps to the
 * right: a swipe to the right or Alt+Left opens Hub, a swipe to the left or Alt+Right opens All;
 * with the tray open, Alt+Left/Right switch panels. The home screen shows [homeRows] rows of the
 * panel's icons; dragging the tray up or down shows more or fewer, as on BlackBerry 7. Menu on an
 * icon puts it on the home screen (Add to Favorites), takes it off, or moves it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bb7HomeScreen(
    apps: List<AppEntry>,
    favoriteKeys: List<String>?,
    homeRows: Int,
    liveSurfaceRepository: LiveSurfaceRepository,
    /** The tray's view, grid columns and icon size, set from the home menu. */
    layout: Bb7Layout = Bb7Layout(),
    appearance: SableAppearance,
    refreshGeneration: Int,
    homeGeneration: Int,
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenHub: () -> Unit,
    /** BlackBerry 7's Delete: the system uninstaller asks before anything is removed. */
    onUninstallApp: (AppEntry) -> Unit = {},
    onFavoritesChanged: (List<String>) -> Unit,
    onHomeRowsChanged: (Int) -> Unit,
    onLayoutChanged: (Bb7Layout) -> Unit = {},
    /** The BB10 grid's folders, tray places after Favorites, All and Downloads ([Bb7Folders]). */
    folders: List<GridFolder> = emptyList(),
    /** The BB10 grid's saved order (null: never arranged), which folder changes keep in step. */
    gridOrder: List<String>? = null,
    onFoldersChanged: (List<GridFolder>, List<String>?) -> Unit = { _, _ -> },
    /** Shortcuts on the wallpaper between the banner and the tray ([Bb7Shortcuts]). */
    shortcuts: List<String> = emptyList(),
    onShortcutsChanged: (List<String>) -> Unit = {},
    /** Holding a letter opens its app or calls its speed dial, as on the BlackBerry 10 home. */
    speedDial: SpeedDialRepository? = null,
    letterShortcuts: LetterShortcutRepository? = null,
    onOpenMenu: (List<Pair<String, () -> Unit>>) -> Unit,
    /** Command search seeded with what was typed (empty for Alt+Space), shown on the BlackBerry 10 screen. */
    onOpenCommand: (String) -> Unit = {},
) {
    // The tray place: the three panels, then the folders (see Bb7Home.trayPlaces).
    var place by remember { mutableIntStateOf(0) }
    val installedKeys = remember(apps) { apps.map { it.stableKey() }.toSet() }
    val shownFolders = Bb7Folders.shown(folders, installedKeys)
    // A folder ungrouped elsewhere leaves the tray on Favorites.
    val at = if (place < Bb7Home.trayPlaces(shownFolders.size)) place else 0
    val panel = Bb7Home.panelAt(at)
    val folderIndex = at - Bb7Panel.entries.size
    val folder = shownFolders.getOrNull(folderIndex)
    val order = gridOrder ?: emptyList()
    // Folder changes keep the BB10 grid order in step, unless it was never arranged.
    val setFolders = { change: Pair<List<GridFolder>, List<String>>? ->
        change?.let { (next, nextOrder) -> onFoldersChanged(next, if (gridOrder == null) null else nextOrder) }
    }
    // A folder name while it is typed (for a new folder holding [newFolderKey], or renaming
    // [renameId]), and the app waiting for its hold-key letter.
    var naming by remember { mutableStateOf<String?>(null) }
    var newFolderKey by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<Int?>(null) }
    var lettering by remember { mutableStateOf<AppEntry?>(null) }
    var shortcutVersion by remember { mutableIntStateOf(0) }
    val speedDialKeys = remember { SpeedDialKeys() }
    val heldLetters by
        produceState(emptySet<Char>(), refreshGeneration, speedDial, shortcutVersion) {
            value = speedDial?.assignedLetters() ?: emptySet()
        }
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf(false) }
    // Focus on the home screen shortcuts instead of the tray, and which one.
    var onHome by remember { mutableStateOf(false) }
    var homeSelected by remember { mutableIntStateOf(0) }
    val homeApps = Bb7Shortcuts.apps(shortcuts, apps)

    // Set from the laid-out width below, read by the key handler.
    var keyColumns by remember { mutableIntStateOf(Bb7Home.MIN_COLUMNS) }
    val focus = remember { FocusRequester() }

    // Home closes the tray and any search, as the BlackBerry 7 home key did.
    LaunchedEffect(homeGeneration) {
        open = false
        query = ""
        selected = 0
        moving = false
        naming = null
        newFolderKey = null
        renameId = null
        lettering = null
        onHome = false
    }
    val live by
        produceState(liveSurfaceRepository.initialSnapshot(), refreshGeneration) {
            value = liveSurfaceRepository.snapshot()
        }
    val today by
        produceState(System.currentTimeMillis(), refreshGeneration) {
            while (true) {
                value = System.currentTimeMillis()
                delay(DATE_TICK_MS - value % DATE_TICK_MS)
            }
        }
    val openApps = {
        query = ""
        place = Bb7Panel.All.ordinal
        open = true
        selected = 0
    }

    val searching = query.isNotEmpty()
    val shown =
        when {
            searching -> Bb7Home.search(apps, query)
            panel != null -> Bb7Home.panelApps(panel, apps, favoriteKeys)
            folder != null -> Bb7Folders.apps(folder, apps)
            else -> emptyList()
        }
    val favorites = Bb7Home.panelApps(Bb7Panel.Favorites, apps, favoriteKeys).map { it.stableKey() }
    val trayTitle =
        when {
            naming != null -> t("folder_name", naming.orEmpty() + "_")
            lettering != null -> t("letter_press_for", lettering?.label.orEmpty())
            moving && onHome -> t("move") + homeApps.getOrNull(homeSelected)?.label.orEmpty() + t("enter_to_drop")
            moving -> t("move") + shown.getOrNull(selected)?.label.orEmpty() + t("enter_to_drop")
            searching -> t("search", query)
            else -> panel?.title ?: folder?.name.orEmpty()
        }
    // Moves the favourite being moved one place ([delta] -1/+1) or a row ([delta] ±columns).
    val moveBy = { delta: Int ->
        if (onHome) {
            val keys = homeApps.map { it.stableKey() }
            val next = HomeApps.move(keys, homeSelected, delta)
            if (next != keys) {
                onShortcutsChanged(next)
                homeSelected += delta
            }
        } else {
            val next = HomeApps.move(favorites, selected, delta)
            if (next != favorites) {
                onFavoritesChanged(next)
                selected += delta
            }
        }
    }
    // The Menu key: the highlighted icon's items first, then Start's own (added by the activity).
    val menuItems = {
        val app = if (onHome) homeApps.getOrNull(homeSelected) else shown.getOrNull(selected)
        buildList<Pair<String, () -> Unit>> {
            if (app != null) {
                val key = app.stableKey()
                add(t("open") to { onLaunchApp(app) })
                if (onHome) {
                    add(t("move_2") to { moving = true })
                } else if (key in favorites) {
                    if (panel == Bb7Panel.Favorites && !searching) add(t("move_2") to { moving = true })
                    add(t("remove_from_favorites") to { onFavoritesChanged(Bb7Home.toggleFavorite(favorites, key)) })
                } else {
                    add(t("add_to_favorites") to { onFavoritesChanged(Bb7Home.toggleFavorite(favorites, key)) })
                }
                val homeLabel = if (key in shortcuts) t("home_shortcut_remove") else t("home_shortcut_add")
                add(homeLabel to { onShortcutsChanged(Bb7Shortcuts.toggle(shortcuts, key)) })
                // The same folder entries as on the BlackBerry 10 grid.
                folders.forEach { own ->
                    if (key in own.keys) {
                        add(t("remove_from_tab", own.name) to { setFolders(GridFolders.remove(folders, order, key)) })
                    } else {
                        add(
                            t("add_to_tab", own.name) to
                                { setFolders(GridFolders.add(folders, own.id, key) to order.filterNot { it == key }) },
                        )
                    }
                }
                if (folders.size < GridFolders.MAX) {
                    add(
                        t("new_folder") to {
                            newFolderKey = key
                            naming = ""
                        },
                    )
                }
                if (letterShortcuts != null && app.inLauncherUser) {
                    val letter = letterShortcuts.load().letterOf(app.component.flattenToString())
                    val label = letter?.let { t("letter_shortcut_current", it.uppercaseChar().toString()) }
                    add((label ?: t("letter_shortcut")) to { lettering = app })
                }
                add(t("app_info") to { onOpenAppInfo(app) })
                if (app.canUninstall && app.inLauncherUser) add(t("delete_app") to { onUninstallApp(app) })
            }
            if (!searching) add(t("open_command") to { onOpenCommand("") })
            if (!searching && folder != null && !onHome) {
                add(
                    t("rename_folder") to {
                        renameId = folder.id
                        naming = folder.name
                    },
                )
                add(
                    t("ungroup_folder") to {
                        setFolders(GridFolders.ungroup(folders, order, folder.id))
                        place = 0
                    },
                )
            }
            if (!open && !searching && homeRows < Bb7Home.MAX_HOME_ROWS) {
                add(t("show_more_rows") to { onHomeRowsChanged(homeRows + 1) })
            }
            if (!open && !searching && homeRows > Bb7Home.MIN_HOME_ROWS) {
                add(t("show_fewer_rows") to { onHomeRowsChanged(homeRows - 1) })
            }
            if (!searching) {
                // Each line shows its current value; choosing it moves to the next one.
                val nextView = if (layout.view == Bb7TrayView.Grid) Bb7TrayView.List else Bb7TrayView.Grid
                add(t("tray_view", layout.view.title) to { onLayoutChanged(layout.copy(view = nextView)) })
                val auto = layout.columns == Bb7Layout.AUTO
                val shownColumns = if (auto) t("columns_auto") else layout.columns.toString()
                add(
                    t("grid_columns", shownColumns) to
                        { onLayoutChanged(layout.copy(columns = Bb7Home.nextColumns(layout.columns))) },
                )
                val sizes = Bb7IconSize.entries
                val nextSize = sizes[(layout.iconSize.ordinal + 1) % sizes.size]
                add(t("icon_size", layout.iconSize.title) to { onLayoutChanged(layout.copy(iconSize = nextSize)) })
            }
        }
    }

    SableTheme(appearance = appearance) {
        Bb10Theme(appearance) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            val down = event.type == KeyEventType.KeyDown
                            if (naming != null || lettering != null) {
                                if (down) {
                                    naming?.let { name ->
                                        naming = Bb7Naming.onKey(native.keyCode, native.unicodeChar, name)
                                        if (naming == null) {
                                            if (native.keyCode == KeyEvent.KEYCODE_ENTER) {
                                                newFolderKey?.let { key ->
                                                    setFolders(GridFolders.create(folders, order, key, name))
                                                }
                                                renameId?.let { id ->
                                                    setFolders(GridFolders.rename(folders, id, name) to order)
                                                }
                                            }
                                            newFolderKey = null
                                            renameId = null
                                        }
                                    }
                                    lettering?.let { app ->
                                        lettering = null
                                        val letter = SpeedDialKeys.letterFor(native.keyCode)
                                        val plain = native.metaState and CHORD_META == 0
                                        if (native.keyCode == KeyEvent.KEYCODE_DEL) {
                                            letterShortcuts?.remove(app.component)
                                        } else if (letter != null && plain) {
                                            letterShortcuts?.assign(letter, app.component)
                                        }
                                        shortcutVersion++
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                            // Holding an assigned letter opens its app or calls; a short press still searches.
                            if (!moving && !searching) {
                                val seed =
                                    if (down) {
                                        StartTypeToSearch.seed(native.unicodeChar, native.metaState, native.repeatCount > 0)
                                    } else {
                                        null
                                    }
                                val step =
                                    speedDialKeys.onKey(
                                        native.keyCode,
                                        down,
                                        native.repeatCount,
                                        native.metaState,
                                        seed,
                                        heldLetters,
                                    )
                                if (step is SpeedDialKeys.Step.Call) speedDial?.call(step.letter)
                                // Typing opens Command, as on the BlackBerry 10 home.
                                if (step is SpeedDialKeys.Step.Search) onOpenCommand(step.seed)
                                if (step != SpeedDialKeys.Step.Pass) return@onPreviewKeyEvent true
                            }
                            if (!down) return@onPreviewKeyEvent false
                            val columns = keyColumns
                            val visible = if (open || searching) shown.size else minOf(shown.size, columns * homeRows)
                            if (onHome && (open || searching || homeApps.isEmpty())) onHome = false
                            if (onHome && !moving) {
                                val size = homeApps.size
                                when (native.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> homeSelected = Bb7Home.move(homeSelected, size, columns, -1, 0)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> homeSelected = Bb7Home.move(homeSelected, size, columns, 1, 0)
                                    KeyEvent.KEYCODE_DPAD_UP -> homeSelected = Bb7Home.move(homeSelected, size, columns, 0, -1)
                                    KeyEvent.KEYCODE_DPAD_DOWN ->
                                        if (homeSelected + columns < size) {
                                            homeSelected = Bb7Home.move(homeSelected, size, columns, 0, 1)
                                        } else {
                                            onHome = false
                                            selected = (homeSelected % columns).coerceAtMost((visible - 1).coerceAtLeast(0))
                                        }
                                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                                        homeApps.getOrNull(homeSelected)?.let { onLaunchApp(it) }
                                    KeyEvent.KEYCODE_MENU -> onOpenMenu(menuItems())
                                    KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> onHome = false
                                    else -> {
                                        if (native.keyCode == KeyEvent.KEYCODE_SPACE && native.isAltPressed) {
                                            onOpenCommand("")
                                        } else {
                                            return@onPreviewKeyEvent false
                                        }
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (moving) {
                                when (native.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveBy(-1)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveBy(1)
                                    KeyEvent.KEYCODE_DPAD_UP -> moveBy(-columns)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveBy(columns)
                                    else -> moving = false
                                }
                                return@onPreviewKeyEvent true
                            }
                            when {
                                native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && native.isAltPressed ->
                                    if (open || searching) {
                                        place = Bb7Home.stepPlace(at, -1, shownFolders.size)
                                        selected = 0
                                    } else {
                                        onOpenHub()
                                    }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && native.isAltPressed ->
                                    if (open || searching) {
                                        place = Bb7Home.stepPlace(at, 1, shownFolders.size)
                                        selected = 0
                                    } else {
                                        openApps()
                                    }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ->
                                    selected = Bb7Home.move(selected, visible, columns, -1, 0)
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ->
                                    selected = Bb7Home.move(selected, visible, columns, 1, 0)
                                native.keyCode == KeyEvent.KEYCODE_DPAD_UP ->
                                    if (open || searching || selected >= columns) {
                                        selected = Bb7Home.move(selected, visible, columns, 0, -1)
                                    } else if (homeApps.isNotEmpty()) {
                                        // Up from the tray's first row reaches the shortcuts above it.
                                        onHome = true
                                        homeSelected = Bb7Home.lastRowStart(homeApps.size, columns) + selected
                                        homeSelected = homeSelected.coerceAtMost(homeApps.size - 1)
                                    } else {
                                        open = true
                                    }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ->
                                    if (open && selected + columns >= visible) {
                                        open = false
                                        selected = selected.coerceAtMost(columns - 1)
                                    } else {
                                        selected = Bb7Home.move(selected, visible, columns, 0, 1)
                                    }
                                native.keyCode == KeyEvent.KEYCODE_ENTER ||
                                    native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ->
                                    shown.getOrNull(selected)?.let { onLaunchApp(it) }
                                native.keyCode == KeyEvent.KEYCODE_SPACE && native.isAltPressed -> onOpenCommand("")
                                native.keyCode == KeyEvent.KEYCODE_SPACE && !searching -> {
                                    place = Bb7Home.stepPlace(at, 1, shownFolders.size)
                                    selected = 0
                                }
                                native.keyCode == KeyEvent.KEYCODE_DEL && searching -> {
                                    query = query.dropLast(1)
                                    selected = 0
                                }
                                native.keyCode == KeyEvent.KEYCODE_ESCAPE || native.keyCode == KeyEvent.KEYCODE_BACK ->
                                    if (searching || open) {
                                        query = ""
                                        open = false
                                        selected = 0
                                    } else {
                                        return@onPreviewKeyEvent false
                                    }
                                native.keyCode == KeyEvent.KEYCODE_MENU -> onOpenMenu(menuItems())
                                else -> {
                                    val ch = native.unicodeChar.takeIf { it > 0 }?.toChar()
                                    if (ch == null || !(ch.isLetterOrDigit() || (ch == ' ' && searching))) {
                                        return@onPreviewKeyEvent false
                                    }
                                    if (searching) {
                                        query += ch
                                        selected = 0
                                    } else {
                                        onOpenCommand(ch.toString())
                                    }
                                }
                            }
                            true
                        },
            ) {
                val columns = Bb7Home.columns(maxWidth.value.toInt(), layout, open = open || searching)
                SideEffect { keyColumns = columns }
                val swipe = with(LocalDensity.current) { SWIPE_DP.dp.toPx() }
                val rowPx = with(LocalDensity.current) { ROW_DP.dp.toPx() }
                Column(
                    modifier =
                        Modifier.fillMaxSize().pointerInput(swipe) {
                            var dragged = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragEnd = {
                                    when (Bb7Home.swipe(dragged, swipe)) {
                                        Bb7Swipe.Hub -> onOpenHub()
                                        Bb7Swipe.Apps -> openApps()
                                        null -> Unit
                                    }
                                },
                            ) { _, amount -> dragged += amount }
                        },
                ) {
                    Bb7Banner(
                        dateText = DateFormat.getDateInstance(DateFormat.FULL).format(Date(today)),
                        weatherLine =
                            live.weather.takeIf { it.availability == LiveAvailability.LIVE }
                                ?.let { HomeHeader.weather(it.detail) }
                                ?.let { HomeHeader.weatherIcon(it.first) + " " + it.first }
                                ?: HomeHeader.weatherFallback(live.weather.availability),
                        unread = live.hubApps.sumOf { it.count },
                        compact = open || searching,
                        onOpenHub = onOpenHub,
                    )
                    if (!open && !searching) {
                        // The wallpaper between the banner and the tray, with the home screen shortcuts at its foot.
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            if (homeApps.isNotEmpty()) {
                                Bb7HomeShortcuts(
                                    apps = homeApps,
                                    columns = columns,
                                    iconDp = layout.iconSize.dp,
                                    selected = if (onHome) homeSelected else -1,
                                    moving = moving && onHome,
                                    onLaunch = { onLaunchApp(it) },
                                    onInfo = onOpenAppInfo,
                                )
                            }
                        }
                    }
                    Bb7Tray(
                        title = trayTitle,
                        apps = if (open || searching) shown else shown.take(columns * homeRows),
                        columns = columns,
                        list = (open || searching) && layout.view == Bb7TrayView.List,
                        iconDp = layout.iconSize.dp,
                        open = open || searching,
                        moving = moving,
                        onResize = { dragged ->
                            val rows = Bb7Home.resizeRows(homeRows, dragged, rowPx)
                            if (rows > Bb7Home.MAX_HOME_ROWS) {
                                open = true
                            } else if (rows != homeRows) {
                                onHomeRowsChanged(rows)
                                selected = selected.coerceAtMost(columns * rows - 1)
                            }
                        },
                        selected = if (onHome) -1 else selected,
                        modifier = if (open || searching) Modifier.weight(1f) else Modifier,
                        onToggle = {
                            open = !open
                            query = ""
                        },
                        onStep = { step ->
                            query = ""
                            place = Bb7Home.stepPlace(at, step, shownFolders.size)
                            selected = 0
                        },
                        onLaunch = { onLaunchApp(it) },
                        onInfo = onOpenAppInfo,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/**
 * The BlackBerry 7 banner: the status dials (as on the BlackBerry 10 home), the date, weather and
 * the Hub unread count, on a scrim so they read over any wallpaper. The status bar shows the time.
 */
@Composable
private fun Bb7Banner(
    dateText: String,
    weatherLine: String,
    unread: Int,
    compact: Boolean,
    onOpenHub: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BANNER_SCRIM)
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
    ) {
        if (!compact) SableStatusLabels(modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(top = if (compact) 0.dp else 6.dp)) {
                Text(
                    text = dateText,
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (!compact) {
                    Text(
                        text = weatherLine,
                        color = BANNER_MUTED,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            UnreadBadge(unread, onOpenHub)
        }
    }
}

/**
 * The Hub unread count; tapping it opens Hub. Nothing when there is nothing unread. Sable 2.0:
 * white on black with a thin grey outline, since blue is kept for focus.
 */
@Composable
private fun UnreadBadge(
    unread: Int,
    onOpenHub: () -> Unit,
) {
    if (unread <= 0) return
    Text(
        text = "✉ $unread",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenHub)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * The tray: its panel name between arrows, then the home screen's rows of apps (closed) or the
 * whole panel (open). Tapping the name opens or closes it; the arrows switch panels; dragging the
 * closed tray up or down ([onResize], px) shows more or fewer rows. The icon being moved is outlined.
 *
 * Sable 2.0 black and white: a thin line above the tray's name, the selected app filled blue with
 * its name in the on-blue colour (the icon being moved outlined in white), and in List view a thin
 * divider under each row, as the BlackBerry 10 lists.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bb7Tray(
    title: String,
    apps: List<AppEntry>,
    columns: Int,
    list: Boolean,
    iconDp: Int,
    open: Boolean,
    moving: Boolean,
    selected: Int,
    modifier: Modifier,
    onResize: (Float) -> Unit,
    onToggle: () -> Unit,
    onStep: (Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
) {
    val grid = rememberLazyGridState()
    LaunchedEffect(selected, open) {
        if (open && selected in apps.indices) grid.animateScrollToItem(selected)
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    val selectedFill = MaterialTheme.colorScheme.primary
    val selectedInk = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(TRAY_SCRIM)
                .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .then(
                    if (open) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            var dragged = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragEnd = { onResize(dragged) },
                            ) { _, amount -> dragged += amount }
                        }
                    },
                ).padding(bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrayArrow("‹") { onStep(-1) }
            Text(
                text = title + if (open) "  ▾" else "  ▴",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onToggle),
            )
            TrayArrow("›") { onStep(1) }
        }
        if (apps.isEmpty()) {
            Text(
                text = t("no_apps_here"),
                color = BANNER_MUTED,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            userScrollEnabled = open,
            modifier = if (open) Modifier.weight(1f) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                apps,
                key = { _, app -> app.component.flattenToString() + app.user.hashCode() },
            ) { index, app ->
                val isSelected = index == selected
                val ink = if (isSelected) selectedInk else Color.White
                val shape = if (list) RectangleShape else RoundedCornerShape(8.dp)
                val cell =
                    Modifier
                        .clip(shape)
                        .background(if (isSelected) selectedFill else Color.Transparent)
                        .border(
                            if (moving && isSelected) 2.dp else 0.dp,
                            if (moving && isSelected) Color.White else Color.Transparent,
                            shape,
                        ).then(
                            if (list) {
                                Modifier.drawBehind {
                                    val y = size.height - 0.5.dp.toPx()
                                    drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                                }
                            } else {
                                Modifier
                            },
                        ).combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { onInfo(app) },
                        )
                if (list) {
                    // BlackBerry 7's list: icon and name on one row.
                    Row(
                        modifier = cell.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppMark(app = app, size = iconDp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = app.label,
                            color = ink,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(
                        modifier = cell.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppMark(app = app, size = iconDp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = app.label,
                            color = ink,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The shortcuts on the wallpaper between the banner and the tray: icons and names in the tray's
 * columns, white on the wallpaper with a shadowless scrim, the focused one filled blue as in the tray.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bb7HomeShortcuts(
    apps: List<AppEntry>,
    columns: Int,
    iconDp: Int,
    selected: Int,
    moving: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
) {
    val selectedFill = MaterialTheme.colorScheme.primary
    val selectedInk = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        apps.chunked(columns).forEachIndexed { row, rowApps ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowApps.forEachIndexed { column, app ->
                    val index = row * columns + column
                    val isSelected = index == selected
                    val shape = RoundedCornerShape(8.dp)
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(if (isSelected) selectedFill else SHORTCUT_SCRIM)
                                .border(
                                    if (moving && isSelected) 2.dp else 0.dp,
                                    if (moving && isSelected) Color.White else Color.Transparent,
                                    shape,
                                ).combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onInfo(app) })
                                .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppMark(app = app, size = iconDp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = app.label,
                            color = if (isSelected) selectedInk else Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
                repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TrayArrow(
    mark: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.width(44.dp).height(32.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = mark, color = Color.White, fontSize = 22.sp)
    }
}

// Shift, Alt, Sym, Fn, Ctrl and Meta: a chord never sets a hold-key letter.
private const val CHORD_META = 0xc1 or 0x32 or 0x4 or 0x8 or 0x7000 or 0x70000

private const val DATE_TICK_MS = 60_000L
private const val SWIPE_DP = 72

/** A tray row's height, the drag that shows one more or one fewer row. */
private const val ROW_DP = 64
private val BANNER_SCRIM = Color(0x99000000)
private val TRAY_SCRIM = Color(0xE6000000)
private val BANNER_MUTED = Color(0xFFA6A6A6)
private val SHORTCUT_SCRIM = Color(0x66000000)
