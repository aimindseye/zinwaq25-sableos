package org.sableos.start.ui

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import org.sableos.start.model.HomeHeader
import org.sableos.start.platform.LiveSurfaceRepository
import java.text.DateFormat
import java.util.Date

/**
 * Sable Start in the BlackBerry 7 style ([org.sableos.start.model.HomeStyle.Bb7]): the clock, date,
 * weather and unread count on the wallpaper, and a tray along the bottom with one row of
 * Favorites that opens into the whole panel. Keys as on BlackBerry 7: arrows move, Enter opens,
 * Up opens the tray and Escape closes it, Space or Alt+Left/Right switch panels (Favorites, All,
 * Downloads), typing searches apps, Menu opens the home options.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bb7HomeScreen(
    apps: List<AppEntry>,
    favoriteKeys: List<String>?,
    liveSurfaceRepository: LiveSurfaceRepository,
    appearance: SableAppearance,
    refreshGeneration: Int,
    homeGeneration: Int,
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenHub: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    var panel by remember { mutableStateOf(Bb7Panel.Favorites) }
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }

    // Set from the laid-out width below, read by the key handler.
    var keyColumns by remember { mutableIntStateOf(Bb7Home.MIN_COLUMNS) }
    val focus = remember { FocusRequester() }

    // Home closes the tray and any search, as the BlackBerry 7 home key did.
    LaunchedEffect(homeGeneration) {
        open = false
        query = ""
        selected = 0
    }
    val live by
        produceState(liveSurfaceRepository.initialSnapshot(), refreshGeneration) {
            value = liveSurfaceRepository.snapshot()
        }
    val now by
        produceState(System.currentTimeMillis()) {
            while (true) {
                value = System.currentTimeMillis()
                delay(CLOCK_TICK_MS - value % CLOCK_TICK_MS)
            }
        }

    val searching = query.isNotEmpty()
    val shown =
        if (searching) Bb7Home.search(apps, query) else Bb7Home.panelApps(panel, apps, favoriteKeys)
    val trayTitle = if (searching) "Search: $query" else panel.title

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
                            val visible = if (open || searching) shown.size else minOf(shown.size, columns)
                            when {
                                native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && native.isAltPressed -> {
                                    panel = Bb7Home.step(panel, -1)
                                    selected = 0
                                }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && native.isAltPressed -> {
                                    panel = Bb7Home.step(panel, 1)
                                    selected = 0
                                }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ->
                                    selected = Bb7Home.move(selected, visible, columns, -1, 0)
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ->
                                    selected = Bb7Home.move(selected, visible, columns, 1, 0)
                                native.keyCode == KeyEvent.KEYCODE_DPAD_UP ->
                                    if (open || searching) {
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
                                native.keyCode == KeyEvent.KEYCODE_MENU -> onOpenMenu()
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Bb7Banner(
                        timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)),
                        dateText = DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)),
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
                        apps = if (open || searching) shown else shown.take(columns),
                        columns = columns,
                        open = open || searching,
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

/** Clock, date, weather and the Hub unread count, on a scrim so they read over any wallpaper. */
@Composable
private fun Bb7Banner(
    timeText: String,
    dateText: String,
    weatherLine: String,
    unread: Int,
    compact: Boolean,
    onOpenHub: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BANNER_SCRIM)
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeText,
                color = Color.White,
                fontSize = if (compact) 22.sp else 44.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
            if (!compact) {
                Text(text = dateText, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    text = weatherLine,
                    color = BANNER_MUTED,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (unread > 0) {
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
    }
}

/**
 * The tray: its panel name between arrows, then one row of apps (closed) or the whole panel
 * (open). Tapping the name opens or closes it; the arrows switch panels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bb7Tray(
    title: String,
    apps: List<AppEntry>,
    columns: Int,
    open: Boolean,
    selected: Int,
    modifier: Modifier,
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
                .padding(bottom = 6.dp),
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
                Column(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent)
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
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

private const val CLOCK_TICK_MS = 60_000L
private const val ICON_DP = 40
private val BANNER_SCRIM = Color(0x99000000)
private val TRAY_SCRIM = Color(0xCC0B0D10)
private val BANNER_MUTED = Color(0xFFC9D1DA)
