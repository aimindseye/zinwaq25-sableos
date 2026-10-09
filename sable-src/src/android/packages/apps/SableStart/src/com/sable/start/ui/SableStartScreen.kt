package org.sableos.start.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.sableos.design.AccentPreset
import org.sableos.design.AppearanceMode
import org.sableos.design.SableAppearance
import org.sableos.design.SableRefreshableSurface
import org.sableos.design.SableTheme
import org.sableos.start.live.LiveAvailability
import org.sableos.start.live.LiveDatum
import org.sableos.start.live.LiveSurfaceSnapshot
import org.sableos.start.model.AppEntry
import org.sableos.start.model.stableKey
import org.sableos.start.platform.LiveSurfaceRepository
import java.time.LocalTime
import java.util.Locale

private enum class SableStartDestination {
    Start,
    Apps,
    Search,
    Live,
    Appearance,
    Peek,
}

private data class HomeTile(
    val title: String,
    val app: AppEntry,
    val accent: Color,
    val liveDatum: LiveDatum?,
    val wide: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
)

private data class AppRailRow(
    val app: AppEntry,
    val section: String,
    val startsSection: Boolean,
)

private data class SearchCommand(
    val title: String,
    val subtitle: String,
    val mark: String,
    val onClick: () -> Unit,
)

private val SableBlue = Color(0xFF4D9CFF)
private val SableGreen = Color(0xFF35C66B)
private val SablePurple = Color(0xFF7A42E8)
private val SableSlate = Color(0xFF64748B)
private val SableOrange = Color(0xFFF28C45)
private val SableTilePalette =
    listOf(
        SableBlue,
        SableGreen,
        SablePurple,
        SableOrange,
        SableSlate,
    )

/**
 * Production Sable Start UI.
 *
 * R9 combines the Start, one-hand Favorites, alphabet rail, local Search /
 * Command, Sable Peek and contextual local-data work in one production path.
 * Recents/Overview remain owned by Android Quickstep.
 */
@Composable
fun SableStartScreen(
    apps: List<AppEntry>,
    liveSurfaceRepository: LiveSurfaceRepository,
    appearance: SableAppearance = SableAppearance(),
    refreshGeneration: Int = 0,
    homeGeneration: Int = 0,
    backGeneration: Int = 0,
    initialScreen: String = "start",
    onCanNavigateBackChanged: (Boolean) -> Unit = {},
    initialFavoriteKeys: Set<String> = emptySet(),
    onFavoriteKeysChanged: (Set<String>) -> Unit = {},
    initialStartTileKeys: Set<String>? = null,
    onStartTileKeysChanged: (Set<String>) -> Unit = {},
    onAppearanceChanged: (SableAppearance) -> Unit = {},
    onResetAppearance: () -> Unit = {},
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenSystemSettings: () -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    onRequestLivePermissions: () -> Unit,
    onRefreshLive: () -> Unit,
) {
    var destination by remember(initialScreen) {
        mutableStateOf(
            when (initialScreen.lowercase(Locale.ROOT)) {
                "live" -> SableStartDestination.Live
                "apps" -> SableStartDestination.Apps
                "search" -> SableStartDestination.Search
                "appearance" -> SableStartDestination.Appearance
                else -> SableStartDestination.Start
            },
        )
    }
    var returnDestination by remember { mutableStateOf(SableStartDestination.Start) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var favoriteKeys by remember(initialFavoriteKeys) {
        mutableStateOf(initialFavoriteKeys.toList())
    }
    var startTileKeys by remember(initialStartTileKeys) {
        mutableStateOf(initialStartTileKeys?.toList())
    }
    var recentKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveSnapshot by remember {
        mutableStateOf(liveSurfaceRepository.initialSnapshot())
    }
    var liveRefreshing by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(refreshGeneration, homeGeneration) {
        liveRefreshing = true
        liveSnapshot = liveSurfaceRepository.snapshot()
        liveRefreshing = false
    }

    val appByKey = apps.associateBy { it.stableKey() }
    val favoriteApps = favoriteKeys.mapNotNull(appByKey::get)
    val startApps =
        resolveStartApps(
            apps = apps,
            explicitKeys = startTileKeys?.toSet(),
        )
    val recentApps = recentKeys.mapNotNull(appByKey::get)

    fun launchApp(entry: AppEntry) {
        if (onLaunchApp(entry)) {
            val key = entry.stableKey()
            recentKeys =
                (listOf(key) + recentKeys.filterNot { it == key })
                    .take(MAX_SESSION_RECENTS)
        }
    }

    fun openPeek(
        entry: AppEntry,
        from: SableStartDestination,
    ) {
        selectedApp = entry
        returnDestination = from
        destination = SableStartDestination.Peek
    }

    fun openSearch(from: SableStartDestination) {
        returnDestination = from
        destination = SableStartDestination.Search
    }

    fun navigateBack() {
        destination =
            when (destination) {
                SableStartDestination.Start -> SableStartDestination.Start
                SableStartDestination.Search,
                SableStartDestination.Peek,
                -> returnDestination
                SableStartDestination.Apps,
                SableStartDestination.Live,
                SableStartDestination.Appearance,
                -> SableStartDestination.Start
            }
    }

    LaunchedEffect(homeGeneration) {
        if (homeGeneration > 0) {
            destination = SableStartDestination.Start
            returnDestination = SableStartDestination.Start
            selectedApp = null
        }
    }

    LaunchedEffect(backGeneration) {
        if (backGeneration > 0 && destination != SableStartDestination.Start) {
            navigateBack()
        }
    }

    LaunchedEffect(destination) {
        onCanNavigateBackChanged(destination != SableStartDestination.Start)
        if (destination == SableStartDestination.Live) {
            onRefreshLive()
        }
    }

    SableTheme(appearance = appearance) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                when (destination) {
                    SableStartDestination.Start ->
                        SableRefreshableSurface(
                            isRefreshing = liveRefreshing,
                            onRefresh = onRefreshLive,
                        ) {
                            StartScreen(
                                snapshot = liveSnapshot,
                                startApps = startApps,
                                onLaunchApp = ::launchApp,
                                onOpenPeek = { app ->
                                    openPeek(app, SableStartDestination.Start)
                                },
                                onAllApps = {
                                    destination = SableStartDestination.Apps
                                },
                                onSearch = {
                                    openSearch(SableStartDestination.Start)
                                },
                                onLive = {
                                    destination = SableStartDestination.Live
                                },
                                onAppearance = {
                                    destination = SableStartDestination.Appearance
                                },
                            )
                        }

                    SableStartDestination.Apps ->
                        AppsScreen(
                            apps = apps,
                            onBack = ::navigateBack,
                            onSearch = {
                                openSearch(SableStartDestination.Apps)
                            },
                            onLaunchApp = ::launchApp,
                            onOpenPeek = { app ->
                                openPeek(app, SableStartDestination.Apps)
                            },
                        )

                    SableStartDestination.Search ->
                        SearchScreen(
                            apps = apps,
                            onBack = ::navigateBack,
                            onLaunchApp = ::launchApp,
                            onOpenPeek = { app ->
                                openPeek(app, SableStartDestination.Search)
                            },
                            onAllApps = {
                                destination = SableStartDestination.Apps
                            },
                            onAppearance = {
                                destination = SableStartDestination.Appearance
                            },
                            onLive = {
                                destination = SableStartDestination.Live
                            },
                            onOpenSettings = onOpenSystemSettings,
                        )

                    SableStartDestination.Live ->
                        SableRefreshableSurface(
                            isRefreshing = liveRefreshing,
                            onRefresh = onRefreshLive,
                        ) {
                            LiveSurfaceScreen(
                                snapshot = liveSnapshot,
                                apps = apps,
                                onBack = ::navigateBack,
                                onRequestPermissions = onRequestLivePermissions,
                            )
                        }

                    SableStartDestination.Appearance ->
                        AppearanceScreen(
                            appearance = appearance,
                            onBack = ::navigateBack,
                            onAppearanceChanged = onAppearanceChanged,
                            onReset = onResetAppearance,
                        )

                    SableStartDestination.Peek -> {
                        val app = selectedApp
                        if (app == null) {
                            destination = returnDestination
                        } else {
                            val key = app.stableKey()
                            SablePeekScreen(
                                app = app,
                                isFavorite = key in favoriteKeys,
                                isStartTile = startApps.any { it.stableKey() == key },
                                onClose = ::navigateBack,
                                onOpen = { launchApp(app) },
                                onOpenAppInfo = { onOpenAppInfo(app) },
                                onToggleFavorite = {
                                    favoriteKeys =
                                        if (key in favoriteKeys) {
                                            favoriteKeys.filterNot { it == key }
                                        } else {
                                            (favoriteKeys + key).distinct()
                                        }
                                    onFavoriteKeysChanged(favoriteKeys.toSet())
                                },
                                onToggleStartTile = {
                                    val current =
                                        startTileKeys
                                            ?: startApps.map { it.stableKey() }
                                    startTileKeys =
                                        if (key in current) {
                                            current.filterNot { it == key }
                                        } else {
                                            (current + key).distinct()
                                        }
                                    onStartTileKeysChanged(
                                        startTileKeys.orEmpty().toSet(),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartScreen(
    snapshot: LiveSurfaceSnapshot,
    startApps: List<AppEntry>,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onSearch: () -> Unit,
    onLive: () -> Unit,
    onAppearance: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 10.dp,
                bottom = 28.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = snapshot.timeText,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 58.sp,
                    )
                    Text(
                        text = snapshot.dateText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                SableControlShortcut(
                    onClick = onAppearance,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = startGreeting(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(14.dp))

            val tiles =
                startApps.map { app ->
                    HomeTile(
                        title = app.label,
                        app = app,
                        accent = appColor(app),
                        liveDatum = liveDatumForApp(app, snapshot),
                        wide = isWideStartTile(app),
                        onClick = { onLaunchApp(app) },
                        onLongClick = { onOpenPeek(app) },
                    )
                }

            TileGrid(tiles)
            Spacer(Modifier.height(18.dp))
            ApprovedStartNavigationRow(
                onAllApps = onAllApps,
                onSearch = onSearch,
                onLive = onLive,
            )
        }
    }
}

private fun startGreeting(): String =
    when (LocalTime.now().hour) {
        in 5..11 -> "good morning"
        in 12..16 -> "good afternoon"
        in 17..21 -> "good evening"
        else -> "good night"
    }

@Composable
private fun SableControlShortcut(
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(46.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SableGlyphIcon(
            packageName = "com.android.settings",
            accent = SableSlate,
            modifier = Modifier.size(46.dp),
            compact = true,
        )
    }
}

@Composable
private fun ApprovedStartNavigationRow(
    onAllApps: () -> Unit,
    onSearch: () -> Unit,
    onLive: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "all apps",
            modifier = Modifier.clickable(onClick = onAllApps),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "   ·   ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "search",
            modifier = Modifier.clickable(onClick = onSearch),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "   ·   ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "live",
            modifier = Modifier.clickable(onClick = onLive),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TileGrid(tiles: List<HomeTile>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var index = 0
        while (index < tiles.size) {
            val tile = tiles[index]
            if (tile.wide) {
                MetroTile(
                    tile = tile,
                    modifier = Modifier.fillMaxWidth(),
                )
                index += 1
            } else {
                val next =
                    tiles.getOrNull(index + 1)
                        ?.takeIf { candidate -> !candidate.wide }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetroTile(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                    )
                    if (next != null) {
                        MetroTile(
                            tile = next,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                index += if (next == null) 1 else 2
            }
        }
    }
}

@Composable
private fun MetroTile(
    tile: HomeTile,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val live =
        tile.liveDatum?.takeIf { datum ->
            datum.availability == LiveAvailability.LIVE ||
                datum.availability == LiveAvailability.EMPTY
        }
    val liveTile =
        tile.app.component.packageName in
            setOf("org.sableos.hub", "org.sableos.weather", "org.sableos.mail") ||
            tile.app.label.equals("Sable Hub", ignoreCase = true) ||
            tile.app.label.equals("Sable Messages", ignoreCase = true) ||
            tile.app.label.equals("Sable Weather", ignoreCase = true) ||
            tile.app.label.equals("Sable Mail", ignoreCase = true)

    Box(
        modifier =
            modifier
                .height(if (tile.wide) 106.dp else 118.dp)
                .shadow(3.dp, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant,
                            tile.accent.copy(alpha = 0.11f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = tile.accent.copy(alpha = 0.30f),
                    shape = shape,
                )
                .combinedClickable(
                    onClick = tile.onClick,
                    onLongClick = tile.onLongClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(tile.accent),
        )

        when {
            tile.wide -> {
                val wide = approvedWideContent(tile, live)
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppMark(
                        app = tile.app,
                        size = 54,
                        fallbackContentColor = MaterialTheme.colorScheme.onSurface,
                        showFallbackSurface = true,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = wide.first,
                            style = MaterialTheme.typography.labelLarge,
                            color = tile.accent,
                        )
                        Text(
                            text = wide.second,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = wide.third,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            liveTile -> {
                val metric = approvedMetric(tile.app, live)
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tile.title.uppercase(Locale.getDefault()),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = tile.accent,
                            maxLines = 1,
                        )
                        AppMark(
                            app = tile.app,
                            size = 28,
                            fallbackContentColor = MaterialTheme.colorScheme.onSurface,
                            showFallbackSurface = false,
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = metric.first,
                            fontSize = 34.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Light,
                        )
                        if (metric.second.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = metric.second,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppMark(
                        app = tile.app,
                        size = 42,
                        fallbackContentColor = MaterialTheme.colorScheme.onSurface,
                        showFallbackSurface = true,
                    )
                    Column {
                        Text(
                            text = tile.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        live?.detail?.let { detail ->
                            Text(
                                text = approvedCompactDetail(tile.app, detail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ApprovedWideContent(
    val first: String,
    val second: String,
    val third: String,
)

private fun approvedWideContent(
    tile: HomeTile,
    live: LiveDatum?,
): ApprovedWideContent =
    when (tile.app.component.packageName) {
        "org.sableos.calendar" ->
            ApprovedWideContent(
                first = "NEXT",
                second = live?.detail ?: "nothing scheduled",
                third = "Calendar",
            )

        "org.sableos.media" -> {
            val parts = live?.detail.orEmpty().split(" · ")
            ApprovedWideContent(
                first = "NOW PLAYING",
                second = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Nothing playing",
                third =
                    parts.drop(1).joinToString(" · ")
                        .replace("playing", "▶")
                        .ifBlank { "Sable Media" },
            )
        }

        else ->
            ApprovedWideContent(
                first = tile.title.uppercase(Locale.getDefault()),
                second = live?.detail ?: tile.title,
                third = tile.title,
            )
    }

private fun approvedMetric(
    app: AppEntry,
    live: LiveDatum?,
): Pair<String, String> {
    val detail = live?.detail.orEmpty()
    return when {
        app.component.packageName == "org.sableos.weather" ||
            app.label.equals("Sable Weather", ignoreCase = true) -> {
            val parts = detail.split(" · ")
            val temperature =
                parts.firstOrNull { it.trim().endsWith("°") }
                    ?.trim()
                    ?: "--°"
            val condition =
                parts.dropWhile { !it.trim().endsWith("°") }
                    .drop(1)
                    .joinToString(" · ")
                    .ifBlank { "weather" }
            temperature to condition
        }

        app.component.packageName == "org.sableos.hub" ||
            app.label.equals("Sable Hub", ignoreCase = true) ||
            app.label.equals("Sable Messages", ignoreCase = true) -> {
            val count =
                detail.substringBefore(" ")
                    .takeIf { it.all(Char::isDigit) }
                    ?: "0"
            val label =
                detail.substringAfter(" ", "active")
                    .ifBlank { "active" }
            count to label
        }

        app.component.packageName == "org.sableos.mail" ||
            app.label.equals("Sable Mail", ignoreCase = true) -> {
            val count = detail.substringBefore(" ").takeIf { it.all(Char::isDigit) } ?: "0"
            count to "unread"
        }

        else -> detail to ""
    }
}

private fun approvedCompactDetail(
    app: AppEntry,
    detail: String,
): String =
    when (app.component.packageName) {
        "com.android.gallery3d" ->
            detail.replace(" photos", " local")
        else -> detail
    }

@Composable
private fun SearchLaunchButton(
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "search + command",
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "apps · settings · Sable actions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppsScreen(
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
) {
    val rows = remember(apps) { buildRailRows(apps) }
    val letters = remember(rows) { rows.map { it.section }.distinct() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember(letters) {
        mutableStateOf(letters.firstOrNull().orEmpty())
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 18.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 38.dp),
        ) {
            MetroHeader(
                title = "all apps",
                onBack = onBack,
            )
            Spacer(Modifier.height(8.dp))
            SearchLaunchButton(onClick = onSearch)
            Spacer(Modifier.height(12.dp))

            if (rows.isEmpty()) {
                EmptyState("No launchable apps are available.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        items = rows,
                        key = { it.app.stableKey() },
                    ) { row ->
                        if (row.startsSection) {
                            Text(
                                text = row.section,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        AppRow(
                            app = row.app,
                            onClick = { onLaunchApp(row.app) },
                            onContext = { onOpenPeek(row.app) },
                        )
                    }
                }
            }
        }

        if (letters.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(22.dp)
                        .pointerInput(letters, rows) {
                            fun jumpTo(y: Float) {
                                if (letters.isEmpty() || size.height <= 0) return
                                val slot =
                                    ((y / size.height) * letters.size)
                                        .toInt()
                                        .coerceIn(0, letters.lastIndex)
                                val letter = letters[slot]
                                val index = rows.indexOfFirst { it.section == letter }
                                if (index >= 0) {
                                    activeLetter = letter
                                    scope.launch {
                                        listState.scrollToItem(index)
                                    }
                                }
                            }

                            detectDragGestures(
                                onDragStart = { offset -> jumpTo(offset.y) },
                                onDrag = { change, _ ->
                                    jumpTo(change.position.y)
                                    change.consume()
                                },
                            )
                        },
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(38.dp)
                        .pointerInput(letters, rows) {
                            fun jumpTo(y: Float) {
                                if (letters.isEmpty() || size.height <= 0) return
                                val slot =
                                    ((y / size.height) * letters.size)
                                        .toInt()
                                        .coerceIn(0, letters.lastIndex)
                                val letter = letters[slot]
                                val index = rows.indexOfFirst { it.section == letter }
                                if (index >= 0) {
                                    activeLetter = letter
                                    scope.launch {
                                        listState.scrollToItem(index)
                                    }
                                }
                            }

                            detectDragGestures(
                                onDragStart = { offset -> jumpTo(offset.y) },
                                onDrag = { change, _ ->
                                    jumpTo(change.position.y)
                                    change.consume()
                                },
                            )
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                letters.forEach { letter ->
                    val selected = letter == activeLetter
                    Text(
                        text = letter,
                        modifier =
                            Modifier
                                .clickable {
                                    val index = rows.indexOfFirst { it.section == letter }
                                    if (index >= 0) {
                                        activeLetter = letter
                                        scope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontWeight =
                            if (selected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onAppearance: () -> Unit,
    onLive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val locale = Locale.getDefault()
    val filteredApps =
        remember(apps, normalizedQuery) {
            if (normalizedQuery.isEmpty()) {
                apps.take(6)
            } else {
                apps.filter { app ->
                    app.label.contains(normalizedQuery, ignoreCase = true)
                }.take(12)
            }
        }

    val commands =
        listOf(
            SearchCommand(
                title = "All apps",
                subtitle = "Alphabetical launcher list",
                mark = "A",
                onClick = onAllApps,
            ),
            SearchCommand(
                title = "Appearance",
                subtitle = "Theme and Sable accent",
                mark = "◐",
                onClick = onAppearance,
            ),
            SearchCommand(
                title = "Local context",
                subtitle = "Calendar, photos and local media state",
                mark = "◫",
                onClick = onLive,
            ),
            SearchCommand(
                title = "System Settings",
                subtitle = "Open Android settings",
                mark = "⚙",
                onClick = onOpenSettings,
            ),
        )
    val filteredCommands =
        commands.filter { command ->
            normalizedQuery.isEmpty() ||
                command.title.lowercase(locale).contains(normalizedQuery.lowercase(locale)) ||
                command.subtitle.lowercase(locale).contains(normalizedQuery.lowercase(locale))
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        MetroHeader(
            title = "search",
            onBack = onBack,
        )
        Spacer(Modifier.height(10.dp))

        SableSearchField(
            value = query,
            onValueChange = { query = it },
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (filteredCommands.isNotEmpty()) {
                item {
                    SectionLabel(
                        if (normalizedQuery.isEmpty()) {
                            "quick actions"
                        } else {
                            "commands"
                        },
                    )
                }
                items(filteredCommands) { command ->
                    SearchCommandRow(command)
                }
                item {
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (filteredApps.isNotEmpty()) {
                item {
                    SectionLabel("apps")
                }
                items(
                    items = filteredApps,
                    key = { it.stableKey() },
                ) { app ->
                    AppRow(
                        app = app,
                        onClick = { onLaunchApp(app) },
                        onContext = { onOpenPeek(app) },
                    )
                }
            } else if (filteredCommands.isEmpty()) {
                item {
                    EmptyState("No local results match “$normalizedQuery”.")
                }
            }
        }
    }
}

@Composable
private fun SableSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(46.dp)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "search apps + Sable actions",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun SearchCommandRow(
    command: SearchCommand,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(onClick = command.onClick)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command.mark,
            modifier = Modifier.width(38.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = command.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = command.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    onClick: () -> Unit,
    onContext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark(app = app, size = 42)
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
                if (app.privacySummary.isNotBlank()) {
                    Text(
                        text = app.privacySummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }

        TextButton(
            onClick = onContext,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = "›",
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactAppRow(
    app: AppEntry,
    onClick: () -> Unit,
    onContext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark(app = app, size = 36)
            Spacer(Modifier.width(12.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
        TextButton(onClick = onContext) {
            Text("⋯")
        }
    }
}

@Composable
private fun AppMark(
    app: AppEntry,
    size: Int,
    fallbackContentColor: Color = MaterialTheme.colorScheme.onSurface,
    showFallbackSurface: Boolean = true,
) {
    val glyphPackage = approvedGlyphPackage(app)
    if (glyphPackage != null) {
        SableGlyphIcon(
            packageName = glyphPackage,
            accent = appColor(app),
            modifier = Modifier.size(size.dp),
            compact = size <= 46,
        )
        return
    }

    val context = LocalContext.current
    val densityDpi = context.resources.displayMetrics.densityDpi
    val icon =
        remember(app.stableKey(), size, densityDpi) {
            loadLauncherIcon(
                context = context,
                app = app,
                sizeDp = size,
            )
        }

    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier =
                Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (showFallbackSurface) {
                            Modifier.background(appColor(app))
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = appMark(app),
                fontWeight = FontWeight.Bold,
                color = fallbackContentColor,
            )
        }
    }
}

private fun approvedGlyphPackage(app: AppEntry): String? {
    val packageName = app.component.packageName
    if (sableGlyphForPackage(packageName) != null) return packageName

    return when {
        app.label.equals("Phone", ignoreCase = true) -> "com.android.dialer"
        app.label.equals("Sable Hub", ignoreCase = true) ||
            app.label.equals("Hub", ignoreCase = true) ||
            app.label.equals("Sable Messages", ignoreCase = true) ||
            app.label.equals("Messages", ignoreCase = true) -> "org.sableos.hub"
        app.label.equals("Sable Mail", ignoreCase = true) ||
            app.label.equals("Mail", ignoreCase = true) -> "org.sableos.mail"
        app.label.equals("Sable Calendar", ignoreCase = true) ||
            app.label.equals("Calendar", ignoreCase = true) -> "org.sableos.calendar"
        app.label.equals("Sable Weather", ignoreCase = true) ||
            app.label.equals("Weather", ignoreCase = true) -> "org.sableos.weather"
        app.label.equals("Photos", ignoreCase = true) ||
            app.label.equals("Gallery", ignoreCase = true) -> "com.android.gallery3d"
        app.label.equals("Sable Media", ignoreCase = true) ||
            app.label.equals("Media", ignoreCase = true) -> "org.sableos.media"
        app.label.equals("Camera", ignoreCase = true) -> "app.grapheneos.camera"
        app.label.equals("Sable Calculator", ignoreCase = true) ||
            app.label.equals("Calculator", ignoreCase = true) -> "org.sableos.calculator"
        app.label.equals("Files", ignoreCase = true) -> "com.android.documentsui"
        app.label.equals("Clock", ignoreCase = true) -> "com.android.deskclock"
        app.label.equals("Vanadium", ignoreCase = true) -> "app.vanadium.browser"
        app.label.equals("Settings", ignoreCase = true) -> "com.android.settings"
        else -> null
    }
}

private fun loadLauncherIcon(
    context: Context,
    app: AppEntry,
    sizeDp: Int,
): Bitmap? =
    runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val activityInfo =
            launcherApps
                .getActivityList(app.component.packageName, app.user)
                .firstOrNull { candidate ->
                    candidate.componentName == app.component
                } ?: return@runCatching null

        val densityDpi = context.resources.displayMetrics.densityDpi
        val density = context.resources.displayMetrics.density
        val iconSizePx =
            (sizeDp * density)
                .toInt()
                .coerceAtLeast(1)
        val insetPx = (iconSizePx * 0.06f).toInt()
        val drawable = activityInfo.getBadgedIcon(densityDpi)
        val bitmap =
            Bitmap.createBitmap(
                iconSizePx,
                iconSizePx,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)

        drawable.setBounds(
            insetPx,
            insetPx,
            iconSizePx - insetPx,
            iconSizePx - insetPx,
        )
        drawable.draw(canvas)
        bitmap
    }.getOrNull()

@Composable
private fun SablePeekScreen(
    app: AppEntry,
    isFavorite: Boolean,
    isStartTile: Boolean,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleStartTile: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = "close",
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onClose)
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(54.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppMark(app = app, size = 58)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = "peek",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (app.privacySummary.isNotBlank()) {
                        Text(
                            text = app.privacySummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "open",
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "→",
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.height(6.dp))

            ContextAction(
                mark = if (isStartTile) "−" else "+",
                label = if (isStartTile) "remove from start" else "pin to start",
                onClick = onToggleStartTile,
            )
            ContextAction(
                mark = if (isFavorite) "−" else "+",
                label =
                    if (isFavorite) {
                        "unpin from favorites"
                    } else {
                        "pin to favorites"
                    },
                onClick = onToggleFavorite,
            )
            ContextAction(
                mark = "ⓘ",
                label = "permissions + app info",
                onClick = onOpenAppInfo,
            )

            Text(
                text = "Shortcuts and notification content stay hidden until Android capability and privacy gates are wired.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun AppearanceScreen(
    appearance: SableAppearance,
    onBack: () -> Unit,
    onAppearanceChanged: (SableAppearance) -> Unit,
    onReset: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            MetroHeader(
                title = "appearance",
                onBack = onBack,
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("theme")
            Spacer(Modifier.height(4.dp))
        }

        items(AppearanceMode.entries) { mode ->
            val selected = appearance.mode == mode
            AppearanceChoiceRow(
                title =
                    when (mode) {
                        AppearanceMode.FollowSystem -> "follow system"
                        AppearanceMode.Light -> "light"
                        AppearanceMode.Dark -> "dark"
                    },
                selected = selected,
                accent = appearance.accent.color,
                onClick = {
                    onAppearanceChanged(
                        appearance.copy(mode = mode),
                    )
                },
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SectionLabel("accent")
            Spacer(Modifier.height(4.dp))
        }

        items(AccentPreset.entries) { accent ->
            val selected = appearance.accent == accent
            AccentChoiceRow(
                accent = accent,
                selected = selected,
                onClick = {
                    onAppearanceChanged(
                        appearance.copy(accent = accent),
                    )
                },
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "reset · follow system + Sable Blue",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onReset)
                        .padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AppearanceChoiceRow(
    title: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(
                        if (selected) {
                            accent
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight =
                if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
        )
        if (selected) {
            Text(
                text = "✓",
                fontSize = 20.sp,
                color = accent,
            )
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun AccentChoiceRow(
    accent: AccentPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .background(accent.color),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = accent.displayName.lowercase(Locale.getDefault()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight =
                if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
        )
        if (selected) {
            Text(
                text = "✓",
                fontSize = 20.sp,
                color = accent.color,
            )
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun LiveSurfaceScreen(
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Text(
                text = "live",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = "Useful state only. No fake feeds and no ambient polling.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = snapshot.timeText,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = snapshot.dateText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            ApprovedLiveRow(
                app = findLiveApp(apps, "org.sableos.weather"),
                title = "Sable Weather",
                datum = snapshot.weather,
                kind = "weather",
            )
            ApprovedLiveRow(
                app = findLiveApp(apps, "org.sableos.calendar"),
                title = "Calendar",
                datum = snapshot.calendar,
                kind = "calendar",
            )
            ApprovedLiveRow(
                app = findLiveApp(apps, "org.sableos.hub"),
                title = "Sable Hub",
                datum = snapshot.messages,
                kind = "messages",
            )
            ApprovedLiveRow(
                app = findLiveApp(apps, "org.sableos.mail"),
                title = "Sable Mail",
                datum = snapshot.mail,
                kind = "mail",
            )
            ApprovedLiveRow(
                app = findLiveApp(apps, "org.sableos.media"),
                title = "Sable Media",
                datum = snapshot.music,
                kind = "media",
            )
            ApprovedLiveRow(
                app = findLiveApp(apps, "com.android.gallery3d"),
                title = "Photos",
                datum = snapshot.photos,
                kind = "photos",
            )
        }

        if (snapshot.photos.availability == LiveAvailability.PERMISSION_REQUIRED) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable(onClick = onRequestPermissions)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "+  enable local photos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun findLiveApp(
    apps: List<AppEntry>,
    packageName: String,
): AppEntry? =
    apps.firstOrNull { it.component.packageName == packageName }
        ?: when (packageName) {
            "org.sableos.weather" ->
                apps.firstOrNull { it.label.equals("Sable Weather", ignoreCase = true) }
            "org.sableos.mail" ->
                apps.firstOrNull { it.label.equals("Sable Mail", ignoreCase = true) }
            else -> null
        }

private data class ApprovedLiveContent(
    val primary: String,
    val secondary: String,
    val state: String,
    val accent: Color,
)

private fun approvedLiveContent(
    kind: String,
    datum: LiveDatum,
): ApprovedLiveContent {
    val unavailable =
        datum.availability !in setOf(
            LiveAvailability.LIVE,
            LiveAvailability.EMPTY,
        )

    if (unavailable) {
        val state =
            when (datum.availability) {
                LiveAvailability.LOADING -> "LOADING"
                LiveAvailability.PERMISSION_REQUIRED -> "ACCESS"
                LiveAvailability.ERROR -> "ERROR"
                else -> "OFFLINE"
            }
        return ApprovedLiveContent(
            primary = datum.detail.ifBlank { "unavailable" },
            secondary = "no fabricated content",
            state = state,
            accent = SableSlate,
        )
    }

    return when (kind) {
        "weather" -> {
            val parts = datum.detail.split(" · ")
            val location = parts.firstOrNull().orEmpty()
            val primary = parts.drop(1).joinToString(" · ").ifBlank { datum.detail }
            ApprovedLiveContent(
                primary = primary,
                secondary =
                    buildString {
                        if (location.isNotBlank()) {
                            append(location)
                            append(" · ")
                        }
                        append(observedAge(datum.observedAtEpochMs))
                    },
                state = "LIVE",
                accent = SableBlue,
            )
        }

        "calendar" ->
            ApprovedLiveContent(
                primary = datum.detail,
                secondary = "next event",
                state = "NEXT",
                accent = SableOrange,
            )

        "messages" ->
            ApprovedLiveContent(
                primary = datum.detail,
                secondary = "message content stays private on Home",
                state = "PRIVATE",
                accent = SableGreen,
            )

        "mail" ->
            ApprovedLiveContent(
                primary =
                    datum.detail
                        .replace("mail alert", "unread")
                        .replace("unreads", "unread"),
                secondary = "subject previews off",
                state = "PRIVATE",
                accent = SableSlate,
            )

        "media" -> {
            val parts = datum.detail.split(" · ")
            ApprovedLiveContent(
                primary = parts.firstOrNull().orEmpty().ifBlank { "nothing playing" },
                secondary = parts.drop(1).joinToString(" · ").ifBlank { "local playback" },
                state =
                    if (datum.detail.contains("playing", ignoreCase = true)) {
                        "PLAYING"
                    } else {
                        "PAUSED"
                    },
                accent = SablePurple,
            )
        }

        "photos" ->
            ApprovedLiveContent(
                primary =
                    datum.detail
                        .replace(" photos", " local photos")
                        .replace(" photo", " local photo"),
                secondary = "device media only",
                state = "LOCAL",
                accent = SablePurple,
            )

        else ->
            ApprovedLiveContent(
                primary = datum.detail,
                secondary = datum.title,
                state = "LIVE",
                accent = SableBlue,
            )
    }
}

private fun observedAge(observedAtEpochMs: Long): String {
    if (observedAtEpochMs <= 0L) return "updated recently"
    val ageMinutes =
        ((System.currentTimeMillis() - observedAtEpochMs).coerceAtLeast(0L) / 60_000L)
    return when {
        ageMinutes <= 1L -> "updated now"
        ageMinutes < 60L -> "updated " + ageMinutes + " min ago"
        else -> "updated today"
    }
}

@Composable
private fun ApprovedLiveRow(
    app: AppEntry?,
    title: String,
    datum: LiveDatum,
    kind: String,
) {
    val content = approvedLiveContent(kind, datum)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) {
            AppMark(
                app = app,
                size = 48,
                fallbackContentColor = MaterialTheme.colorScheme.onSurface,
                showFallbackSurface = true,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(content.accent.copy(alpha = 0.16f))
                        .border(
                            1.dp,
                            content.accent.copy(alpha = 0.55f),
                            RoundedCornerShape(11.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = content.accent,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = content.primary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.secondary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = content.state,
            style = MaterialTheme.typography.labelLarge,
            color = content.accent,
        )
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun MetroHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .width(48.dp),
        ) {
            Text(
                text = "‹",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun MetroLink(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun ContextAction(
    mark: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mark, fontSize = 22.sp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 18.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun buildRailRows(apps: List<AppEntry>): List<AppRailRow> {
    val locale = Locale.getDefault()
    val sorted =
        apps.sortedWith(
            compareBy<AppEntry> { it.label.lowercase(locale) }
                .thenBy { it.component.packageName }
                .thenBy { it.component.className },
        )

    return sorted.mapIndexed { index, app ->
        val section = alphabetSection(app.label, locale)
        AppRailRow(
            app = app,
            section = section,
            startsSection =
                index == 0 ||
                    alphabetSection(sorted[index - 1].label, locale) != section,
        )
    }
}

private fun alphabetSection(
    label: String,
    locale: Locale,
): String {
    val first =
        label
            .trim()
            .firstOrNull()
            ?.toString()
            ?.uppercase(locale)
            .orEmpty()

    return if (first.firstOrNull()?.isLetter() == true) first else "#"
}

private fun resolveStartApps(
    apps: List<AppEntry>,
    explicitKeys: Set<String>?,
): List<AppEntry> {
    if (explicitKeys != null) {
        return apps
            .filter { it.stableKey() in explicitKeys }
            .sortedWith(startTileComparator())
    }

    val selected = linkedMapOf<String, AppEntry>()

    PREFERRED_START_APP_GROUPS.forEach { group ->
        val selectedApp =
            apps.firstOrNull { app ->
                app.component.packageName in group.packageNames
            } ?: apps.firstOrNull { app ->
                group.labels.any { preferred ->
                    app.label.equals(preferred, ignoreCase = true)
                }
            }

        selectedApp?.let { app ->
            selected.putIfAbsent(app.stableKey(), app)
        }
    }

    apps
        .filterNot(::isSettingsApp)
        .sortedWith(startTileComparator())
        .forEach { app ->
            if (selected.size < DEFAULT_START_TILE_TARGET) {
                selected.putIfAbsent(app.stableKey(), app)
            }
        }

    return selected.values.take(DEFAULT_START_TILE_TARGET)
}

private fun startTileComparator(): Comparator<AppEntry> =
    compareBy<AppEntry> { app ->
        preferredStartRank(app)
    }.thenBy { app ->
        app.label.lowercase(Locale.getDefault())
    }.thenBy { app ->
        app.component.flattenToString()
    }

private fun preferredStartRank(app: AppEntry): Int {
    val index =
        PREFERRED_START_APP_GROUPS.indexOfFirst { group ->
            app.component.packageName in group.packageNames ||
                group.labels.any { preferred ->
                    app.label.equals(preferred, ignoreCase = true)
                }
        }
    return if (index >= 0) index else Int.MAX_VALUE
}

private fun isSettingsApp(app: AppEntry): Boolean =
    app.label.equals("Settings", ignoreCase = true) ||
        app.component.packageName == "com.android.settings"

private fun appMark(app: AppEntry): String =
    app.label
        .trim()
        .take(1)
        .uppercase()
        .ifEmpty { "•" }

private fun appColor(app: AppEntry): Color =
    when {
        app.component.packageName == "org.sableos.hub" ||
            app.label.equals("Sable Hub", ignoreCase = true) ||
            app.label.equals("Sable Messages", ignoreCase = true) -> SableGreen
        app.component.packageName == "org.sableos.mail" ||
            app.label.equals("Sable Mail", ignoreCase = true) -> SableSlate
        app.component.packageName == "org.sableos.calendar" ||
            app.label.equals("Calendar", ignoreCase = true) -> SableOrange
        app.component.packageName == "org.sableos.weather" ||
            app.label.equals("Sable Weather", ignoreCase = true) -> SableBlue
        app.component.packageName == "org.sableos.media" ||
            app.label.equals("Sable Media", ignoreCase = true) -> SablePurple
        app.component.packageName == "com.android.gallery3d" ||
            app.label.equals("Photos", ignoreCase = true) -> SablePurple
        app.component.packageName == "app.grapheneos.camera" ||
            app.label.equals("Camera", ignoreCase = true) -> SableSlate
        app.component.packageName == "org.sableos.calculator" ||
            app.label.equals("Sable Calculator", ignoreCase = true) -> SableBlue
        app.component.packageName == "com.android.documentsui" ||
            app.label.equals("Files", ignoreCase = true) -> SableOrange
        app.component.packageName == "com.android.dialer" ||
            app.label.equals("Phone", ignoreCase = true) -> SableBlue
        else -> {
            val index =
                Math.floorMod(
                    app.stableKey().hashCode(),
                    SableTilePalette.size,
                )
            SableTilePalette[index]
        }
    }

private fun liveDatumForApp(
    app: AppEntry,
    snapshot: LiveSurfaceSnapshot,
): LiveDatum? =
    when {
        app.component.packageName == "org.sableos.calendar" ||
            app.label.equals("Calendar", ignoreCase = true) -> snapshot.calendar
        app.component.packageName == "org.sableos.hub" ||
            app.label.equals("Sable Hub", ignoreCase = true) ||
            app.label.equals("Sable Messages", ignoreCase = true) -> snapshot.messages
        app.component.packageName == "org.sableos.mail" ||
            app.label.equals("Sable Mail", ignoreCase = true) -> snapshot.mail
        app.component.packageName == "org.sableos.weather" ||
            app.label.equals("Sable Weather", ignoreCase = true) -> snapshot.weather
        app.component.packageName == "org.sableos.media" ||
            app.label.equals("Sable Media", ignoreCase = true) -> snapshot.music
        app.component.packageName == "com.android.gallery3d" ||
            app.label.equals("Photos", ignoreCase = true) -> snapshot.photos
        else -> null
    }

private fun isWideStartTile(app: AppEntry): Boolean =
    app.component.packageName == "org.sableos.calendar" ||
        app.component.packageName == "org.sableos.media"

private data class PreferredStartAppGroup(
    val packageNames: Set<String> = emptySet(),
    val labels: List<String>,
)

private val PREFERRED_START_APP_GROUPS =
    listOf(
        PreferredStartAppGroup(
            packageNames = setOf("org.sableos.calendar"),
            labels = listOf("Sable Calendar", "Calendar"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("org.sableos.hub"),
            labels = listOf("Sable Hub", "Hub", "Sable Messages", "Messages"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("org.sableos.weather"),
            labels = listOf("Sable Weather", "Weather"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("org.sableos.media"),
            labels = listOf("Sable Media", "Media"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("org.sableos.mail"),
            labels = listOf("Sable Mail", "Mail"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("com.android.dialer"),
            labels = listOf("Phone"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("app.grapheneos.camera"),
            labels = listOf("Camera"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("com.android.gallery3d"),
            labels = listOf("Photos", "Gallery"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("org.sableos.calculator"),
            labels = listOf("Sable Calculator", "Calculator"),
        ),
        PreferredStartAppGroup(
            packageNames = setOf("com.android.documentsui"),
            labels = listOf("Files"),
        ),
    )

private const val DEFAULT_START_TILE_TARGET = 10
private const val MAX_FAVORITES_ON_START = 4
private const val MAX_SESSION_RECENTS = 8
