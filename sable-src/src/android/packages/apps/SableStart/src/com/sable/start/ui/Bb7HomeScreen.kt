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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import org.sableos.design.SableAppearance
import org.sableos.design.SableTheme
import org.sableos.start.live.LiveAvailability
import org.sableos.start.model.AppEntry
import org.sableos.start.model.Bb7Home
import org.sableos.start.model.Bb7Panel
import org.sableos.start.model.Bb7Swipe
import org.sableos.start.model.HomeApps
import org.sableos.start.model.HomeHeader
import org.sableos.start.model.stableKey
import org.sableos.start.platform.LiveSurfaceRepository
import java.text.DateFormat
import java.util.Date

/**
 * Sable Start in the BlackBerry 7 style ([org.sableos.start.model.HomeStyle.Bb7]): the banner
 * (status dials, date, weather and unread count; the time is the status bar's, so it is not shown
 * twice) on the wallpaper, and a tray along the bottom with one row of Favorites that opens into
 * the whole panel. Keys as on BlackBerry 7: arrows move, Enter opens, Up opens the tray and
 * Escape closes it, Space switches panels (Favorites, All, Downloads), typing searches apps, Menu
 * opens the home options. As on the BlackBerry 10 home, Hub is to the left and the apps to the
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
    appearance: SableAppearance,
    refreshGeneration: Int,
    homeGeneration: Int,
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenHub: () -> Unit,
    onFavoritesChanged: (List<String>) -> Unit,
    onHomeRowsChanged: (Int) -> Unit,
    onOpenMenu: (List<Pair<String, () -> Unit>>) -> Unit,
) {
    var panel by remember { mutableStateOf(Bb7Panel.Favorites) }
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf(false) }

    // Set from the laid-out width below, read by the key handler.
    var keyColumns by remember { mutableIntStateOf(Bb7Home.MIN_COLUMNS) }
    val focus = remember { FocusRequester() }

    // Home closes the tray and any search, as the BlackBerry 7 home key did.
    LaunchedEffect(homeGeneration) {
        open = false
        query = ""
        selected = 0
        moving = false
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
        panel = Bb7Panel.All
        open = true
        selected = 0
    }

    val searching = query.isNotEmpty()
    val shown =
        if (searching) Bb7Home.search(apps, query) else Bb7Home.panelApps(panel, apps, favoriteKeys)
    val favorites = Bb7Home.panelApps(Bb7Panel.Favorites, apps, favoriteKeys).map { it.stableKey() }
    val trayTitle =
        when {
            moving -> "Move " + shown.getOrNull(selected)?.label.orEmpty() + " · Enter to drop"
            searching -> "Search: $query"
            else -> panel.title
        }
    // Moves the favourite being moved one place ([delta] -1/+1) or a row ([delta] ±columns).
    val moveBy = { delta: Int ->
        val next = HomeApps.move(favorites, selected, delta)
        if (next != favorites) {
            onFavoritesChanged(next)
            selected += delta
        }
    }
    // The Menu key: the highlighted icon's items first, then Start's own (added by the activity).
    val menuItems = {
        val app = shown.getOrNull(selected)
        buildList<Pair<String, () -> Unit>> {
            if (app != null) {
                val key = app.stableKey()
                add("Open" to { onLaunchApp(app) })
                if (key in favorites) {
                    if (panel == Bb7Panel.Favorites && !searching) add("Move" to { moving = true })
                    add("Remove from Favorites" to { onFavoritesChanged(Bb7Home.toggleFavorite(favorites, key)) })
                } else {
                    add("Add to Favorites" to { onFavoritesChanged(Bb7Home.toggleFavorite(favorites, key)) })
                }
                add("App Info" to { onOpenAppInfo(app) })
            }
            if (!open && !searching && homeRows < Bb7Home.MAX_HOME_ROWS) {
                add("Show More Rows" to { onHomeRowsChanged(homeRows + 1) })
            }
            if (!open && !searching && homeRows > Bb7Home.MIN_HOME_ROWS) {
                add("Show Fewer Rows" to { onHomeRowsChanged(homeRows - 1) })
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
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val native = event.nativeKeyEvent
                            val columns = keyColumns
                            val visible = if (open || searching) shown.size else minOf(shown.size, columns * homeRows)
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
                                        panel = Bb7Home.step(panel, -1)
                                        selected = 0
                                    } else {
                                        onOpenHub()
                                    }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && native.isAltPressed ->
                                    if (open || searching) {
                                        panel = Bb7Home.step(panel, 1)
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
                                native.keyCode == KeyEvent.KEYCODE_SPACE && !searching -> {
                                    panel = Bb7Home.step(panel, 1)
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
                                    query += ch
                                    selected = 0
                                }
                            }
                            true
                        },
            ) {
                val columns = Bb7Home.columns(maxWidth.value.toInt())
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
                    if (!open && !searching) Spacer(Modifier.weight(1f))
                    Bb7Tray(
                        title = trayTitle,
                        apps = if (open || searching) shown else shown.take(columns * homeRows),
                        columns = columns,
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
                        selected = selected,
                        modifier = if (open || searching) Modifier.weight(1f) else Modifier,
                        onToggle = {
                            open = !open
                            query = ""
                        },
                        onStep = { step ->
                            query = ""
                            panel = Bb7Home.step(panel, step)
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
        if (!compact) SableStatusRings(modifier = Modifier.fillMaxWidth(), ringSize = RING_DP.dp)
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

/** The Hub unread count; tapping it opens Hub. Nothing when there is nothing unread. */
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                .clickable(onClick = onOpenHub)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * The tray: its panel name between arrows, then the home screen's rows of apps (closed) or the
 * whole panel (open). Tapping the name opens or closes it; the arrows switch panels; dragging the
 * closed tray up or down ([onResize], px) shows more or fewer rows. The icon being moved is outlined.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bb7Tray(
    title: String,
    apps: List<AppEntry>,
    columns: Int,
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(TRAY_SCRIM)
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
                text = "No apps here",
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
            itemsIndexed(apps, key = { _, app -> app.component.flattenToString() + app.user.hashCode() }) { index, app ->
                val isSelected = index == selected
                val outline = if (moving && isSelected) Color.White else MaterialTheme.colorScheme.primary
                Column(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent)
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                if (isSelected) outline else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onInfo(app) })
                            .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppMark(app = app, size = ICON_DP)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = app.label,
                        color = Color.White,
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

private const val DATE_TICK_MS = 60_000L
private const val RING_DP = 34
private const val SWIPE_DP = 72

/** A tray row's height, the drag that shows one more or one fewer row. */
private const val ROW_DP = 64
private const val ICON_DP = 40
private val BANNER_SCRIM = Color(0x99000000)
private val TRAY_SCRIM = Color(0xCC0B0D10)
private val BANNER_MUTED = Color(0xFFC9D1DA)
