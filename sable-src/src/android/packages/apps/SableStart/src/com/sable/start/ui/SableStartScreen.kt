package org.sableos.start.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.CallLog
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import org.sableos.start.model.ActiveFrame
import org.sableos.start.model.ActiveFrames
import org.sableos.start.model.AppGrid
import org.sableos.start.model.AppProfiles
import org.sableos.start.model.HomeHeader
import org.sableos.start.model.CommandGroups
import org.sableos.start.model.CommandBestMatch
import org.sableos.start.privacy.PrivacyFilter
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.design.AccentPreset
import org.sableos.design.AppearanceMode
import org.sableos.design.SableAlphabetIndex
import org.sableos.design.SableAlphabetRail
import org.sableos.design.SableAlphabetRailWidth
import org.sableos.design.SableAppearance
import org.sableos.design.SableFocusMemory
import org.sableos.design.SableRefreshableSurface
import org.sableos.design.SableTheme
import org.sableos.design.SableTypeToJump
import org.sableos.design.sableFocusRing
import org.sableos.start.live.LiveAvailability
import org.sableos.start.live.LiveDatum
import org.sableos.start.live.LiveSurfaceSnapshot
import org.sableos.start.model.AppEntry
import org.sableos.start.model.AppGlyphs
import org.sableos.start.model.CommandQuery
import org.sableos.start.model.CommandRanking
import org.sableos.start.model.CommandScope
import org.sableos.start.model.CoreAppIdentity
import org.sableos.start.model.GlanceWidgets
import org.sableos.start.model.HomeApps
import org.sableos.start.model.HomeLayout
import org.sableos.start.model.HubBar
import org.sableos.start.model.HubFilter
import org.sableos.start.model.HubPeople
import org.sableos.start.model.HubPerson
import org.sableos.start.model.QuickBar
import org.sableos.start.model.StartPages
import org.sableos.start.model.StartTypeToSearch
import org.sableos.start.model.inLauncherUser
import org.sableos.start.model.stableKey
import org.sableos.start.platform.LiveSurfaceRepository
import org.sableos.start.platform.PeopleRepository
import org.sableos.start.privacy.AllAppsKeyAction
import org.sableos.start.privacy.AllAppsKeyPolicy
import org.sableos.start.privacy.AppAction
import org.sableos.start.privacy.AppActionContext
import org.sableos.start.privacy.AppActionPolicy
import org.sableos.start.privacy.PrivacySummaryPolicy
import java.time.LocalTime
import java.util.Locale

private enum class SableStartDestination {
    /** The Start pages: Hub, Active Frames, app grid ([StartPages]). */
    Start,
    Apps,
    Search,
    Appearance,
    Peek,
    EditHome,
    SlotOptions,

    /** All Apps "Choose base bar slot" for an app when the bar is full. */
    SlotPicker,
}

/** One quick bar slot resolved against the installed apps. */
private data class QuickSlot(
    val token: String,
    val label: String,
    /** The app a role or app slot opens; null for Command and All Apps. */
    val app: AppEntry?,
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
    /** Where the result comes from, shown on the row ("Settings", "Contacts"). */
    val source: String,
    val onClick: () -> Unit,
    /** Per-result actions (Menu or Alt+Enter on the row); empty means "open" only. */
    val actions: List<Pair<String, () -> Unit>> = emptyList(),
)

/** What Edit Home saves with Done. */
private data class HomeEdit(
    val gridOrder: List<String>,
    val quickBar: List<String>,
    val glanceWidgets: List<String>,
    val hiddenSections: Set<String>,
)

private val SableBlue = Color(0xFF4D9CFF)
private val SableGreen = Color(0xFF35C66B)
private val SablePurple = Color(0xFF7A42E8)
private val SableSlate = Color(0xFF64748B)
private val SableOrange = Color(0xFFF28C45)
private val SableSun = Color(0xFFF5C542)
private val SableTilePalette =
    listOf(
        SableBlue,
        SableGreen,
        SablePurple,
        SableOrange,
        SableSlate,
    )

// BlackBerry 10 look on the Start pages. Values from the public BB10 look; the UI
// Guidelines PDF has the exact tokens. The pages follow Android's Dark theme and the
// Sable accent: dark is the BB10 look, light keeps the layout on light surfaces, and the
// focus highlight is the accent (BB10 blue for the default Sable Blue).
private val Bb10DefaultBlue = Color(0xFF00A8DF)

/** Start's page colours for one Dark theme state and accent. */
@Immutable
internal data class StartPalette(
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val card: Color,
    val tile: Color,
    val line: Color,
    val barTop: Color,
    val barBottom: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
)

internal fun startPalette(
    dark: Boolean,
    accent: AccentPreset,
): StartPalette {
    val accentColor = if (accent == AccentPreset.Blue) Bb10DefaultBlue else accent.color
    val onAccent = if (accent == AccentPreset.Blue) Color.White else accent.onColor
    return if (dark) {
        StartPalette(
            accent = accentColor,
            onAccent = onAccent,
            background = Color(0xFF121212),
            card = Color(0xFF262626),
            tile = Color(0xFF2E2E2E),
            line = Color(0xFF3A3A3A),
            barTop = Color(0xFF2A2A2A),
            barBottom = Color(0xFF141414),
            text = Color.White,
            muted = Color(0xFF9E9E9E),
            dim = Color(0xFF5E5E5E),
        )
    } else {
        StartPalette(
            accent = accentColor,
            onAccent = onAccent,
            background = Color(0xFFF2F4F7),
            card = Color.White,
            tile = Color(0xFFE6E9EE),
            line = Color(0xFFCBD1D8),
            barTop = Color(0xFFF7F8FA),
            barBottom = Color(0xFFDDE1E6),
            text = Color(0xFF12171C),
            muted = Color(0xFF515B66),
            dim = Color(0xFFA3ABB4),
        )
    }
}

private val LocalStartPalette = staticCompositionLocalOf { startPalette(dark = true, accent = AccentPreset.Blue) }

private val Bb10Blue: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.accent
private val Bb10OnBlue: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.onAccent
private val Bb10Background: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.background
private val Bb10Card: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.card
private val Bb10Tile: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.tile
private val Bb10Line: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.line
private val Bb10BarTop: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.barTop
private val Bb10BarBottom: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.barBottom
private val Bb10Text: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.text
private val Bb10Muted: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.muted
private val Bb10Dim: Color
    @Composable @ReadOnlyComposable get() = LocalStartPalette.current.dim

/**
 * Production Sable Start UI in the BlackBerry 10 layout (owner request, after
 * the BB10 UI Guidelines): a horizontal strip of Hub (left), Active Frames
 * (Home: up to eight recent apps as live cards, or the Glance widgets while
 * none run) and the app grid pages (right), with the BB10 page dots and the
 * Phone · Search · Camera bar. All Apps, Command/Search, Peek, Edit Home and
 * Appearance open over the pages. Recents/Overview remain owned by Android
 * Quickstep; Active Frames read the same recent tasks.
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
    /** Start tiles chosen before Home became an ordered list; seeds the grid order with [initialHomeApps]. */
    initialStartTileKeys: Set<String>? = null,
    /** The three-page Home apps; they lead the app grid until the user orders it. */
    initialHomeApps: List<String>? = null,
    initialGridOrder: List<String>? = null,
    onGridOrderChanged: (List<String>?) -> Unit = {},
    initialQuickBar: List<String>? = null,
    onQuickBarChanged: (List<String>?) -> Unit = {},
    initialGlanceWidgets: List<String>? = null,
    onGlanceWidgetsChanged: (List<String>?) -> Unit = {},
    initialHiddenHomeSections: Set<String>? = null,
    onHiddenHomeSectionsChanged: (Set<String>?) -> Unit = {},
    /** Recent tasks, most recent first ([ActiveFrames.select]). */
    frames: List<ActiveFrame> = emptyList(),
    /** Changes whenever the frames should repaint their snapshots. */
    framesGeneration: Int = 0,
    /** The frame's live picture; called off the main thread. */
    frameSnapshot: (ActiveFrame) -> Bitmap? = { null },
    onResumeFrame: (ActiveFrame) -> Unit = {},
    onCloseFrame: (ActiveFrame) -> Unit = {},
    onAppearanceChanged: (SableAppearance) -> Unit = {},
    onResetAppearance: () -> Unit = {},
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenSystemSettings: () -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    onRequestLivePermissions: () -> Unit,
    onRefreshLive: () -> Unit,
    onOpenNotificationSettings: (AppEntry) -> Unit = onOpenAppInfo,
    onUninstallApp: (AppEntry) -> Unit = {},
    onStartIntent: (Intent) -> Unit = {},
    /** null: no private space on this device; true: locked; false: unlocked. */
    privateSpaceLocked: Boolean? = null,
    onTogglePrivateSpace: () -> Unit = {},
    /** Counts up for each "show the Hub page" request (org.sableos.start.action.SHOW_HUB). */
    hubGeneration: Int = 0,
    /** The tab that request asked for ([HubFilter] by name), or null for All. */
    hubRequestFilter: String? = null,
    /** The Contacts tab asks for READ_CONTACTS; the activity bumps [refreshGeneration] after. */
    onRequestContactsPermission: () -> Unit = {},
) {
    var destination by remember(initialScreen) {
        mutableStateOf(destinationFor(initialScreen))
    }
    var returnDestination by remember { mutableStateOf(SableStartDestination.Start) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var selectedSlot by remember { mutableStateOf<QuickSlot?>(null) }
    var savedGridOrder by remember(initialGridOrder) { mutableStateOf(initialGridOrder) }
    var recentKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    // Text typed on Start before Command/Search opened (type-to-launch).
    var searchSeed by remember { mutableStateOf("") }
    var quickBarTokens by remember(initialQuickBar) {
        mutableStateOf(QuickBar.normalize(initialQuickBar))
    }
    var glanceWidgets by remember(initialGlanceWidgets) {
        mutableStateOf(GlanceWidgets.normalize(initialGlanceWidgets))
    }
    var hiddenSections by remember(initialHiddenHomeSections) {
        mutableStateOf(HomeLayout.normalizeHidden(initialHiddenHomeSections))
    }
    var hubFilter by remember { mutableStateOf(HubFilter.All) }
    var editBackGeneration by remember { mutableStateOf(0) }
    // Survives Peek / App info / Search round trips (FOCUS_RESTORATION=REQUIRED).
    val focusMemory = remember { SableFocusMemory() }
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
    // The grid holds every app except Private Space ones, which stay in All Apps behind its lock.
    val gridCandidates = apps.filterNot { it.profileLabel == PROFILE_PRIVATE_LABEL }
    val defaultHomeKeys = defaultHomeApps(apps).map { it.stableKey() }
    val gridKeys =
        AppGrid.resolve(
            saved = savedGridOrder,
            preferredFirst = HomeApps.resolve(initialHomeApps, initialStartTileKeys, defaultHomeKeys),
            installed = gridCandidates.map { it.stableKey() },
        )
    val gridApps = gridKeys.mapNotNull(appByKey::get)
    val gridLabels = gridApps.mapTo(HashSet()) { it.label }
    val quickSlots =
        quickBarTokens.mapNotNull { token ->
            resolveQuickSlot(token, apps, appByKey)
        }
    val gridRows = AppGrid.rowsFor(startGridHeightDp(), GRID_CELL_MIN_DP)
    val gridPerPage = AppGrid.perPage(gridRows)
    val gridPages = AppGrid.pageCount(gridApps.size, gridPerPage)
    val pageCount = StartPages.count(gridPages)

    val startPager =
        rememberPagerState(initialPage = StartPages.forScreen(initialScreen)) { pageCount }
    val pagerScope = rememberCoroutineScope()

    fun movePage(delta: Int) {
        val target = StartPages.target(startPager.currentPage, delta, pageCount)
        pagerScope.launch { startPager.animateScrollToPage(target) }
    }

    fun showPage(page: Int) {
        destination = SableStartDestination.Start
        pagerScope.launch { startPager.animateScrollToPage(page) }
    }

    fun launchApp(entry: AppEntry) {
        if (onLaunchApp(entry)) {
            val key = entry.stableKey()
            recentKeys =
                (listOf(key) + recentKeys.filterNot { it == key })
                    .take(MAX_SESSION_RECENTS)
        }
    }

    fun secondInstance(entry: AppEntry): AppEntry? =
        AppProfiles.secondInstance(
            app = entry,
            apps = apps,
            componentOf = { it.component.flattenToString() },
            profileOf = { it.profileLabel },
        )

    /** Shift+Enter: the Dual copy (or the personal app of a Dual copy); the app itself when it has none. */
    fun launchSecond(entry: AppEntry) = launchApp(secondInstance(entry) ?: entry)

    fun setGridOrder(next: List<String>) {
        savedGridOrder = next
        onGridOrderChanged(next)
    }

    fun openPeek(
        entry: AppEntry,
        from: SableStartDestination,
    ) {
        selectedApp = entry
        returnDestination = from
        destination = SableStartDestination.Peek
    }

    fun openSearch(
        from: SableStartDestination,
        seed: String = "",
    ) {
        searchSeed = seed
        returnDestination = from
        destination = SableStartDestination.Search
    }

    fun navigateBack() {
        destination = backDestination(destination, returnDestination)
    }

    /** Hub's sources, private mode and history (Hub has no launcher entry of its own). */
    fun openHubSettings() {
        onStartIntent(hubSettingsIntent())
    }

    fun showHub(filter: HubFilter) {
        hubFilter = filter
        showPage(StartPages.HUB)
    }

    fun dial(digits: String) {
        onStartIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(digits))))
    }

    fun activateSlot(slot: QuickSlot) {
        when (slot.token) {
            QuickBar.COMMAND -> openSearch(SableStartDestination.Start)
            QuickBar.ALL_APPS -> destination = SableStartDestination.Apps
            // The Hub slot is Start's own Hub page; the Sable Hub app has no screen to open.
            QuickBar.HUB -> showHub(HubFilter.All)
            else -> slot.app?.let(::launchApp)
        }
    }

    fun resumeFrame(frame: ActiveFrame) {
        onResumeFrame(frame)
    }

    LaunchedEffect(homeGeneration) {
        if (homeGeneration > 0) {
            destination = SableStartDestination.Start
            returnDestination = SableStartDestination.Start
            selectedApp = null
            startPager.animateScrollToPage(StartPages.HOME)
        }
    }

    LaunchedEffect(hubGeneration) {
        if (hubGeneration > 0) {
            destination = SableStartDestination.Start
            selectedApp = null
            hubFilter = HubFilter.fromName(hubRequestFilter) ?: HubFilter.All
            startPager.animateScrollToPage(StartPages.HUB)
        }
    }

    LaunchedEffect(backGeneration) {
        if (backGeneration > 0) {
            when {
                // Edit Home decides: cancel the item being moved, else leave without saving.
                destination == SableStartDestination.EditHome -> editBackGeneration += 1
                destination != SableStartDestination.Start -> navigateBack()
                // Back on Hub or an app grid page returns to Active Frames.
                startPager.currentPage != StartPages.HOME -> startPager.animateScrollToPage(StartPages.HOME)
            }
        }
    }

    LaunchedEffect(destination, startPager.currentPage) {
        onCanNavigateBackChanged(
            destination != SableStartDestination.Start || startPager.currentPage != StartPages.HOME,
        )
    }

    LaunchedEffect(startPager.settledPage) {
        if (startPager.settledPage == StartPages.HUB) onRefreshLive()
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
                        Bb10Theme(appearance) {
                            StartStrip(
                                state = startPager,
                                pageCount = pageCount,
                                quickSlots = quickSlots,
                                onMovePage = ::movePage,
                                onShowPage = { page -> pagerScope.launch { startPager.animateScrollToPage(page) } },
                                onQuickSlot = ::activateSlot,
                                onSlotOptions = { slot ->
                                    selectedSlot = slot
                                    destination = SableStartDestination.SlotOptions
                                },
                                onHubPeek = { pagerScope.launch { startPager.animateScrollToPage(StartPages.HUB) } },
                                onDial = { char -> dial(char.toString()) },
                                onTypeToSearch = { char -> openSearch(SableStartDestination.Start, char.toString()) },
                                onCommand = { openSearch(SableStartDestination.Start) },
                                onEditHome = { destination = SableStartDestination.EditHome },
                            ) { page ->
                                when {
                                    page == StartPages.HUB ->
                                        SableRefreshableSurface(
                                            isRefreshing = liveRefreshing,
                                            onRefresh = onRefreshLive,
                                        ) {
                                            HubPage(
                                                snapshot = liveSnapshot,
                                                apps = apps,
                                                filter = hubFilter,
                                                active = startPager.settledPage == StartPages.HUB,
                                                onFilter = { hubFilter = it },
                                                onLaunchApp = ::launchApp,
                                                onOpenHubSettings = ::openHubSettings,
                                                onStartIntent = onStartIntent,
                                                refreshGeneration = refreshGeneration,
                                                onRequestContactsPermission = onRequestContactsPermission,
                                            )
                                        }
                                    page == StartPages.HOME ->
                                        ActiveFramesPage(
                                            frames = frames,
                                            apps = apps,
                                            hiddenSections = hiddenSections,
                                            onOpenSettings = onOpenSystemSettings,
                                            onSearch = { openSearch(SableStartDestination.Start) },
                                            snapshot = liveSnapshot,
                                            widgets = glanceWidgets,
                                            refreshGeneration = refreshGeneration,
                                            framesGeneration = framesGeneration,
                                            frameSnapshot = frameSnapshot,
                                            active = startPager.settledPage == StartPages.HOME,
                                            onResume = ::resumeFrame,
                                            onClose = onCloseFrame,
                                            onLaunchApp = ::launchApp,
                                            onStartIntent = onStartIntent,
                                            onRequestPermissions = onRequestLivePermissions,
                                            onShowHub = { showHub(HubFilter.All) },
                                        )
                                    else -> {
                                        val index = page - StartPages.FIRST_GRID
                                        AppGridPage(
                                            apps = AppGrid.page(gridApps, index, gridPerPage),
                                            rows = gridRows,
                                            gridLabels = gridLabels,
                                            active = startPager.settledPage == page,
                                            onLaunchApp = ::launchApp,
                                            onLaunchSecond = ::launchSecond,
                                            onOpenPeek = { app -> openPeek(app, SableStartDestination.Start) },
                                            onEditHome = { destination = SableStartDestination.EditHome },
                                        )
                                    }
                                }
                            }
                        }

                    SableStartDestination.SlotOptions -> {
                        val slot = selectedSlot
                        if (slot == null) {
                            destination = SableStartDestination.Start
                        } else {
                            SlotOptionsScreen(
                                slot = slot,
                                apps = apps,
                                onBack = ::navigateBack,
                                onOpen = {
                                    destination = SableStartDestination.Start
                                    activateSlot(slot)
                                },
                                onHubFilter = ::showHub,
                                onOpenHubSettings = {
                                    destination = SableStartDestination.Start
                                    openHubSettings()
                                },
                                onStartIntent = { intent ->
                                    destination = SableStartDestination.Start
                                    onStartIntent(intent)
                                },
                                onLaunchApp = { app ->
                                    destination = SableStartDestination.Start
                                    launchApp(app)
                                },
                                onAppInfo = { app -> onOpenAppInfo(app) },
                                onEditHome = { destination = SableStartDestination.EditHome },
                            )
                        }
                    }

                    SableStartDestination.SlotPicker -> {
                        val app = selectedApp
                        if (app == null) {
                            destination = SableStartDestination.Start
                        } else {
                            SlotPickerScreen(
                                app = app,
                                tokens = quickBarTokens,
                                apps = apps,
                                appByKey = appByKey,
                                onBack = { destination = SableStartDestination.Peek },
                                onReplace = { index ->
                                    val next = QuickBar.replace(quickBarTokens, index, QuickBar.appToken(app.stableKey()))
                                    if (next != quickBarTokens) {
                                        quickBarTokens = next
                                        onQuickBarChanged(next)
                                    }
                                    destination = SableStartDestination.Peek
                                },
                            )
                        }
                    }

                    SableStartDestination.EditHome ->
                        EditHomeScreen(
                            apps = apps,
                            initial =
                                HomeEdit(
                                    gridOrder = gridKeys,
                                    quickBar = quickBarTokens,
                                    glanceWidgets = glanceWidgets,
                                    hiddenSections = hiddenSections,
                                ),
                            defaults =
                                HomeEdit(
                                    gridOrder =
                                        AppGrid.resolve(
                                            saved = null,
                                            preferredFirst = defaultHomeKeys,
                                            installed = gridCandidates.map { it.stableKey() },
                                        ),
                                    quickBar = QuickBar.DEFAULT,
                                    glanceWidgets = GlanceWidgets.DEFAULT,
                                    hiddenSections = HomeLayout.DEFAULT_HIDDEN,
                                ),
                            backGeneration = editBackGeneration,
                            onCancel = { destination = SableStartDestination.Start },
                            onDone = { edit ->
                                setGridOrder(edit.gridOrder)
                                quickBarTokens = QuickBar.normalize(edit.quickBar)
                                onQuickBarChanged(if (quickBarTokens == QuickBar.DEFAULT) null else quickBarTokens)
                                glanceWidgets = edit.glanceWidgets
                                onGlanceWidgetsChanged(edit.glanceWidgets)
                                hiddenSections = edit.hiddenSections
                                onHiddenHomeSectionsChanged(edit.hiddenSections)
                                destination = SableStartDestination.Start
                                pagerScope.launch { startPager.scrollToPage(StartPages.HOME) }
                            },
                            onAppearance = {
                                returnDestination = SableStartDestination.Start
                                destination = SableStartDestination.Appearance
                            },
                        )

                    SableStartDestination.Apps ->
                        AppsScreen(
                            apps = apps,
                            focusMemory = focusMemory,
                            onBack = ::navigateBack,
                            onSearch = {
                                openSearch(SableStartDestination.Apps)
                            },
                            onLaunchApp = ::launchApp,
                            onLaunchSecond = ::launchSecond,
                            onOpenPeek = { app ->
                                openPeek(app, SableStartDestination.Apps)
                            },
                            onOpenAppInfo = onOpenAppInfo,
                            privateSpaceLocked = privateSpaceLocked,
                            onTogglePrivateSpace = onTogglePrivateSpace,
                        )

                    SableStartDestination.Search ->
                        SearchScreen(
                            apps = apps,
                            recentKeys = recentKeys,
                            initialQuery = searchSeed,
                            privateSpaceLocked = privateSpaceLocked,
                            onTogglePrivateSpace = onTogglePrivateSpace,
                            onBack = ::navigateBack,
                            onLaunchApp = ::launchApp,
                            secondInstanceOf = ::secondInstance,
                            onOpenAppInfo = onOpenAppInfo,
                            onOpenPeek = { app ->
                                openPeek(app, SableStartDestination.Search)
                            },
                            onAllApps = {
                                destination = SableStartDestination.Apps
                            },
                            onAppearance = {
                                returnDestination = SableStartDestination.Start
                                destination = SableStartDestination.Appearance
                            },
                            onFrames = { showPage(StartPages.HOME) },
                            onGrid = { showPage(StartPages.FIRST_GRID) },
                            onHub = ::showHub,
                            onEditHome = {
                                destination = SableStartDestination.EditHome
                            },
                            onOpenSettings = onOpenSystemSettings,
                            onStartIntent = onStartIntent,
                        )

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
                            val barIndex = quickSlots.indexOfFirst { it.app?.stableKey() == key }
                            SablePeekScreen(
                                app = app,
                                inBaseBar = QuickBar.appState(app.inLauncherUser, barIndex >= 0),
                                hasSecondInstance = secondInstance(app) != null,
                                onToggleBaseBar = {
                                    val slotToken = quickSlots.getOrNull(barIndex)?.token
                                    if (slotToken == null && QuickBar.needsSlotPicker(quickBarTokens)) {
                                        destination = SableStartDestination.SlotPicker
                                    } else {
                                        val next = QuickBar.toggleApp(quickBarTokens, slotToken, key)
                                        if (next != quickBarTokens) {
                                            quickBarTokens = next
                                            onQuickBarChanged(next)
                                        }
                                    }
                                },
                                onClose = ::navigateBack,
                                onOpen = { launchApp(app) },
                                onOpenSecond = { launchSecond(app) },
                                onOpenAppInfo = { onOpenAppInfo(app) },
                                onOpenNotificationSettings = { onOpenNotificationSettings(app) },
                                onUninstall = { onUninstallApp(app) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The BB10 look over the Start pages, in the user's Dark theme and Sable accent: the
 * focus fill and rings are the accent ([startPalette]).
 */
@Composable
internal fun Bb10Theme(
    appearance: SableAppearance,
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance.mode) {
            AppearanceMode.FollowSystem -> isSystemInDarkTheme()
            AppearanceMode.Light -> false
            AppearanceMode.Dark -> true
        }
    val palette = remember(dark, appearance.accent) { startPalette(dark, appearance.accent) }
    val base = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalStartPalette provides palette) {
        MaterialTheme(
            colorScheme =
                base.copy(
                    primary = palette.accent,
                    onPrimary = palette.onAccent,
                    background = palette.background,
                    onBackground = palette.text,
                    surface = palette.card,
                    onSurface = palette.text,
                    surfaceVariant = palette.tile,
                    onSurfaceVariant = palette.muted,
                    outlineVariant = palette.line,
                ),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * The app grid's height in dp: the window (LocalWindowInfo, so the Q25's
 * square and any other size alike) less the system bars, the page dots, the
 * bar and the grid's padding. Before the window is measured, the
 * configuration's screen height stands in.
 */
@Composable
private fun startGridHeightDp(): Int {
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val insets = WindowInsets.safeDrawing
    val contentDp =
        if (windowHeightPx > 0) {
            with(density) { (windowHeightPx - insets.getTop(density) - insets.getBottom(density)).toDp().value.toInt() }
        } else {
            LocalConfiguration.current.screenHeightDp - STATUS_BAR_FALLBACK_DP
        }
    return contentDp - START_CHROME_DP
}

/**
 * The horizontal strip (BB10 layout): pages, the BB10 page indicator and the
 * Phone · Search · Camera bar.
 *
 * Keys anywhere on the strip: Alt+Left/Right (Fn+Left/Right) move one page,
 * Alt+1..Alt+3 open the bar slots, Alt+Space opens Command, digits typed on the
 * focused Phone slot dial, Menu that nothing took opens Edit Home, and any
 * other printable key opens Command/Search seeded with it.
 */
@Composable
private fun StartStrip(
    state: PagerState,
    pageCount: Int,
    quickSlots: List<QuickSlot>,
    onMovePage: (Int) -> Unit,
    onShowPage: (Int) -> Unit,
    onQuickSlot: (QuickSlot) -> Unit,
    onSlotOptions: (QuickSlot) -> Unit,
    onHubPeek: () -> Unit,
    onDial: (Char) -> Unit,
    onTypeToSearch: (Char) -> Unit,
    onCommand: () -> Unit,
    onEditHome: () -> Unit,
    page: @Composable (Int) -> Unit,
) {
    var phoneSlotFocused by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Bb10Background)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val delta = StartPages.keyDelta(native.keyCode, native.metaState)
                    val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
                    val dial = if (phoneSlotFocused) QuickBar.dialChar(native.unicodeChar) else null
                    val slot =
                        QuickBar.shortcutSlot(
                            keyCode = native.keyCode,
                            metaState = native.metaState,
                            slotCount = quickSlots.size,
                            unicode = native.unicodeChar,
                        )
                    val action: (() -> Unit)? =
                        when {
                            delta != null -> {
                                { onMovePage(delta) }
                            }
                            alt && native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE -> onCommand
                            dial != null -> {
                                { onDial(dial) }
                            }
                            slot != null -> {
                                { onQuickSlot(quickSlots[slot]) }
                            }
                            else -> null
                        }
                    if (action != null && event.type == KeyEventType.KeyDown && native.repeatCount == 0) action()
                    action != null
                }.onKeyEvent { event ->
                    // What no frame, icon or slot took: Menu edits Home, printable keys search.
                    val native = event.nativeKeyEvent
                    if (native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU) {
                        if (event.type == KeyEventType.KeyDown) onEditHome()
                        return@onKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val seed =
                        StartTypeToSearch.seed(
                            unicode = native.unicodeChar,
                            metaState = native.metaState,
                            repeat = native.repeatCount > 0,
                        ) ?: return@onKeyEvent false
                    onTypeToSearch(seed)
                    true
                },
    ) {
        HorizontalPager(
            state = state,
            beyondViewportPageCount = 0,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index -> page(index) }
        Bb10PageIndicator(
            current = state.currentPage,
            count = pageCount,
            onShowPage = onShowPage,
        )
        Bb10ActionBar(
            slots = quickSlots,
            onSlot = onQuickSlot,
            onOptions = onSlotOptions,
            onHubPeek = onHubPeek,
            onPhoneFocusChanged = { phoneSlotFocused = it },
        )
    }
}

/**
 * BB10 page indicator: the Hub mark, the Active Frames mark and one dot per
 * app grid page; the current page is white, the others grey.
 */
@Composable
private fun Bb10PageIndicator(
    current: Int,
    count: Int,
    onShowPage: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(INDICATOR_HEIGHT_DP.dp)
                .semantics {
                    contentDescription = pageDescription(current)
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (page in 0 until count) {
            val color = if (page == current) Bb10Text else Bb10Dim
            Box(
                modifier =
                    Modifier
                        .size(width = INDICATOR_SLOT_DP.dp, height = INDICATOR_HEIGHT_DP.dp)
                        .clickable { onShowPage(page) },
                contentAlignment = Alignment.Center,
            ) {
                ComposeCanvas(Modifier.size(if (page < StartPages.FIRST_GRID) 10.dp else 6.dp)) {
                    val unit = size.minDimension
                    when (page) {
                        StartPages.HUB ->
                            // Hub: three short lines.
                            for (line in 0..2) {
                                val y = unit * (0.2f + line * 0.3f)
                                drawLine(color, Offset(0f, y), Offset(unit, y), strokeWidth = unit * 0.14f, cap = StrokeCap.Round)
                            }
                        StartPages.HOME -> {
                            // Active Frames: four small squares.
                            val cell = unit * 0.42f
                            for (row in 0..1) {
                                for (col in 0..1) {
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(col * (unit - cell), row * (unit - cell)),
                                        size = Size(cell, cell),
                                    )
                                }
                            }
                        }
                        else -> drawCircle(color = color, radius = unit / 2f)
                    }
                }
            }
        }
    }
}

private fun pageDescription(page: Int): String =
    when (page) {
        StartPages.HUB -> "Hub page"
        StartPages.HOME -> "Active Frames"
        else -> "App grid page ${page - StartPages.FIRST_GRID + 1}"
    }

/**
 * Active Frames (BB10 Home). On top, the status dials and Settings gear, then
 * the location's weather with the date under it (no clock: the status bar
 * keeps the only one; no greeting). Below, up to eight recent apps as live
 * cards, two by two, scrolling down for more. Each card shows the app's
 * last picture (else its icon and live summary) and its name.
 *
 * Keys: arrows move between frames, Enter resumes, X or Backspace closes the
 * focused frame (the next one takes focus). With no frames the Glance widgets
 * (Weather, Calendar, Battery, Media; set in Edit Home) fill the frames.
 */
@Composable
private fun ActiveFramesPage(
    frames: List<ActiveFrame>,
    apps: List<AppEntry>,
    hiddenSections: Set<String>,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    snapshot: LiveSurfaceSnapshot,
    widgets: List<String>,
    refreshGeneration: Int,
    framesGeneration: Int,
    frameSnapshot: (ActiveFrame) -> Bitmap?,
    active: Boolean,
    onResume: (ActiveFrame) -> Unit,
    onClose: (ActiveFrame) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onStartIntent: (Intent) -> Unit,
    onRequestPermissions: () -> Unit,
    onShowHub: () -> Unit,
) {
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    val firstWidgetFocus = remember { FocusRequester() }
    // The frame to focus once the list changes (after a close), else the first one.
    var focusTask by remember { mutableStateOf<Int?>(null) }

    fun requesterFor(taskId: Int) = requesters.getOrPut(taskId) { FocusRequester() }

    LaunchedEffect(active, frames.map { it.taskId }) {
        if (!active) return@LaunchedEffect
        withFrameNanos { }
        val target = focusTask?.takeIf { id -> frames.any { it.taskId == id } } ?: frames.firstOrNull()?.taskId
        focusTask = null
        if (target != null) {
            runCatching { requesterFor(target).requestFocus() }
        } else {
            runCatching { firstWidgetFocus.requestFocus() }
        }
    }

    fun shown(section: String) = HomeLayout.isShown(hiddenSections, section)

    Column(modifier = Modifier.fillMaxSize()) {
        if (shown(HomeLayout.STATUS_RINGS)) {
            StatusDialsRow(onOpenSettings = onOpenSettings)
        }
        if (shown(HomeLayout.WEATHER)) {
            WeatherDateHeader(
                datum = snapshot.weather,
                refreshGeneration = refreshGeneration,
                onOpen = { findLiveApp(apps, WEATHER_PACKAGE)?.let(onLaunchApp) },
            )
        }
        if (shown(HomeLayout.SEARCH_ROW)) {
            Box(Modifier.padding(horizontal = FRAME_PADDING)) { SearchLaunchButton(onClick = onSearch) }
        }
        ActiveFramesGrid(
            frames = frames,
            apps = apps,
            snapshot = snapshot,
            widgets = widgets,
            refreshGeneration = refreshGeneration,
            framesGeneration = framesGeneration,
            frameSnapshot = frameSnapshot,
            requesterFor = ::requesterFor,
            firstWidgetFocus = firstWidgetFocus,
            onResume = onResume,
            onClose = { frame ->
                val index = frames.indexOf(frame)
                focusTask =
                    ActiveFrames.focusAfterClose(index, frames.size)?.let { next ->
                        frames.filterNot { it == frame }.getOrNull(next)?.taskId
                    }
                onClose(frame)
            },
            onLaunchApp = onLaunchApp,
            onStartIntent = onStartIntent,
            onRequestPermissions = onRequestPermissions,
            onShowHub = onShowHub,
        )
    }
}

/** The status dials (Wi-Fi, Mobile, CPU, RAM, battery time left, Cell) and the Settings gear. */
@Composable
private fun StatusDialsRow(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = FRAME_PADDING, end = 2.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SableStatusRings(modifier = Modifier.weight(1f), ringSize = STATUS_RING_SIZE)
        Text(
            text = "⚙",
            fontSize = 22.sp,
            color = Bb10Text,
            modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .width(44.dp)
                    .sableFocusRing()
                    .clickable(onClick = onOpenSettings)
                    .semantics { contentDescription = "Settings" }
                    .padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "72° Partly cloudy · Seattle" with the date under it. The date follows the
 * locale and turns over at midnight; there is no clock here.
 */
@Composable
private fun WeatherDateHeader(
    datum: LiveDatum,
    refreshGeneration: Int,
    onOpen: () -> Unit,
) {
    val locale = Locale.getDefault()
    val today by
        produceState(initialValue = LocalDate.now(), refreshGeneration) {
            while (true) {
                value = LocalDate.now()
                val millisOfDay = LocalTime.now().toNanoOfDay() / NANOS_PER_MILLI
                delay(HomeHeader.millisToMidnight(millisOfDay))
            }
        }
    val dateText =
        remember(today, locale) {
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, HomeHeader.DATE_SKELETON)
            today.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    val weather =
        datum.takeIf { it.availability == LiveAvailability.LIVE }?.let { HomeHeader.weather(it.detail) }
    val line =
        when {
            weather == null -> HomeHeader.weatherFallback(datum.availability)
            weather.second.isBlank() -> HomeHeader.weatherIcon(weather.first) + " " + weather.first
            else -> HomeHeader.weatherIcon(weather.first) + " " + weather.first + " · " + weather.second
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FRAME_PADDING)
                .sableFocusRing()
                .clickable(onClick = onOpen)
                .semantics { contentDescription = "$line. $dateText" }
                .padding(vertical = 2.dp),
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.titleMedium,
            color = Bb10Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodySmall,
            color = Bb10Muted,
            maxLines = 1,
        )
    }
}

/** The frames themselves (or the Glance widgets as frames), two rows to the remaining height. */
@Composable
private fun ColumnScope.ActiveFramesGrid(
    frames: List<ActiveFrame>,
    apps: List<AppEntry>,
    snapshot: LiveSurfaceSnapshot,
    widgets: List<String>,
    refreshGeneration: Int,
    framesGeneration: Int,
    frameSnapshot: (ActiveFrame) -> Bitmap?,
    requesterFor: (Int) -> FocusRequester,
    firstWidgetFocus: FocusRequester,
    onResume: (ActiveFrame) -> Unit,
    onClose: (ActiveFrame) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onStartIntent: (Intent) -> Unit,
    onRequestPermissions: () -> Unit,
    onShowHub: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val cardHeight =
            ((maxHeight - FRAME_PADDING * 2 - FRAME_GAP) / ActiveFrames.ROWS_PER_SCREEN).coerceAtLeast(FRAME_MIN_HEIGHT)
        if (frames.isEmpty()) {
            GlanceFrames(
                widgets = widgets,
                snapshot = snapshot,
                apps = apps,
                refreshGeneration = refreshGeneration,
                cardHeight = cardHeight,
                firstFocus = firstWidgetFocus,
                onLaunchApp = onLaunchApp,
                onStartIntent = onStartIntent,
                onRequestPermissions = onRequestPermissions,
                onShowHub = onShowHub,
            )
            return@BoxWithConstraints
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(FRAME_PADDING),
            verticalArrangement = Arrangement.spacedBy(FRAME_GAP),
        ) {
            items(frames.chunked(ActiveFrames.COLUMNS), key = { row -> row.first().taskId }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FRAME_GAP)) {
                    row.forEach { frame ->
                        FrameCard(
                            frame = frame,
                            app = appForFrame(frame, apps),
                            live = appForFrame(frame, apps)?.let { liveDatumForApp(it, snapshot) },
                            framesGeneration = framesGeneration,
                            frameSnapshot = frameSnapshot,
                            onResume = { onResume(frame) },
                            onClose = { onClose(frame) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(cardHeight)
                                    .focusRequester(requesterFor(frame.taskId)),
                        )
                    }
                    repeat(ActiveFrames.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The installed app a frame belongs to (same package, same user), else any copy of the package. */
private fun appForFrame(
    frame: ActiveFrame,
    apps: List<AppEntry>,
): AppEntry? {
    val samePackage = apps.filter { it.component.packageName == frame.packageName }
    // UserHandle.hashCode() is the user id, as in stableKey().
    return samePackage.firstOrNull { it.user.hashCode() == frame.userId } ?: samePackage.firstOrNull()
}

/**
 * One Active Frame: the app's live picture above a title strip (app mark, name, close), so the
 * strip never covers the app. The picture is the full-resolution snapshot, scaled to the card's
 * width from the top. Focus is an accent outline and an accent strip.
 */
@Composable
private fun FrameCard(
    frame: ActiveFrame,
    app: AppEntry?,
    live: LiveDatum?,
    framesGeneration: Int,
    frameSnapshot: (ActiveFrame) -> Bitmap?,
    onResume: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val label = app?.label ?: frame.packageName.substringAfterLast('.')
    val picture by
        produceState<Bitmap?>(initialValue = null, frame.taskId, framesGeneration) {
            value = withContext(Dispatchers.IO) { runCatching { frameSnapshot(frame) }.getOrNull() }
        }
    val shape = RoundedCornerShape(FRAME_CORNER)
    Column(
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(Bb10Card)
                .border(if (focused) 2.dp else 1.dp, if (focused) Bb10Blue else Bb10Line, shape)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (!ActiveFrames.isCloseKey(native.keyCode, native.metaState)) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) onClose()
                    true
                }.combinedClickable(onClick = onResume, onLongClick = onClose)
                .semantics { contentDescription = "$label, Active Frame. Enter resumes, X closes" },
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Bb10Background)) {
            val bitmap = picture
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (app != null) AppMark(app = app, size = FRAME_ICON_DP)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text =
                            live?.takeIf { it.availability == LiveAvailability.LIVE }?.detail
                                ?: if (frame.running) "running" else "Enter opens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Bb10Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FRAME_LABEL_HEIGHT)
                    .background(if (focused) Bb10Blue else Bb10BarTop)
                    .padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app != null) {
                AppMark(app = app, size = FRAME_STRIP_ICON_DP)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Bb10OnBlue else Bb10Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier =
                    Modifier
                        .size(FRAME_LABEL_HEIGHT)
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = "Close $label" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    fontSize = 18.sp,
                    color = if (focused) Bb10OnBlue else Bb10Muted,
                )
            }
        }
    }
}

/**
 * No frames: the Glance widgets as frames, in the user's order (Weather,
 * Calendar, Battery, Media by default). No ambient polling: values refresh
 * with Start's live snapshot.
 */
@Composable
private fun GlanceFrames(
    widgets: List<String>,
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    refreshGeneration: Int,
    cardHeight: Dp,
    firstFocus: FocusRequester,
    onLaunchApp: (AppEntry) -> Unit,
    onStartIntent: (Intent) -> Unit,
    onRequestPermissions: () -> Unit,
    onShowHub: () -> Unit,
) {
    val context = LocalContext.current
    val battery by
        produceState<Pair<Int?, Boolean>>(initialValue = null to false, refreshGeneration) {
            value = withContext(Dispatchers.IO) { readBattery(context) }
        }

    fun open(packageName: String) = findLiveApp(apps, packageName)?.let(onLaunchApp)

    if (widgets.isEmpty()) {
        Box(Modifier.fillMaxSize().focusRequester(firstFocus).focusable(), contentAlignment = Alignment.Center) {
            EmptyState("No apps are open. Press Menu to choose Glance widgets for this page.")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(FRAME_PADDING),
        verticalArrangement = Arrangement.spacedBy(FRAME_GAP),
    ) {
        items(widgets.chunked(ActiveFrames.COLUMNS), key = { row -> row.first() }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FRAME_GAP)) {
                row.forEach { widget ->
                    val modifier =
                        Modifier
                            .weight(1f)
                            .height(cardHeight)
                            .then(if (widget == widgets.first()) Modifier.focusRequester(firstFocus) else Modifier)
                    when (widget) {
                        GlanceWidgets.WEATHER -> {
                            val (headline, detail) = weatherLines(snapshot.weather)
                            GlanceFrameCard("Weather", headline, detail, SableSun, { open(WEATHER_PACKAGE) }, modifier)
                        }
                        GlanceWidgets.BATTERY -> BatteryGlanceFrame(battery, onStartIntent, modifier)
                        GlanceWidgets.PHOTOS -> {
                            val datum = snapshot.photos
                            val needsPermission = datum.availability == LiveAvailability.PERMISSION_REQUIRED
                            GlanceFrameCard(
                                title = "Photos",
                                headline = if (needsPermission) "Photos" else datum.detail,
                                detail = if (needsPermission) "Enter to allow local photo counts" else "",
                                accent = SablePurple,
                                onClick = { if (needsPermission) onRequestPermissions() else open(GALLERY_PACKAGE) },
                                modifier = modifier,
                            )
                        }
                        else -> LiveGlanceFrame(widget, snapshot, apps, { open(it) }, onShowHub, modifier)
                    }
                }
                repeat(ActiveFrames.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The Battery Glance frame: level and charging state; Enter opens battery usage. */
@Composable
private fun BatteryGlanceFrame(
    battery: Pair<Int?, Boolean>,
    onStartIntent: (Intent) -> Unit,
    modifier: Modifier,
) {
    val (level, charging) = battery
    GlanceFrameCard(
        title = "Battery",
        headline = level?.let { "$it%" } ?: "--",
        detail = if (level == null) "unavailable" else if (charging) "charging" else "",
        accent = SableGreen,
        onClick = { onStartIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
        modifier = modifier,
    )
}

/** Calendar, Media, Hub and Mail Glance frames: the live snapshot's line for that app. */
@Composable
private fun LiveGlanceFrame(
    widget: String,
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    open: (String) -> Unit,
    onShowHub: () -> Unit,
    modifier: Modifier,
) {
    val (datum, packageName, accent) =
        when (widget) {
            GlanceWidgets.CALENDAR -> Triple(snapshot.calendar, CALENDAR_PACKAGE, SableBlue)
            GlanceWidgets.MEDIA -> Triple(snapshot.music, MEDIA_PACKAGE, SablePurple)
            GlanceWidgets.HUB -> Triple(snapshot.messages, HUB_PACKAGE, SableGreen)
            else -> Triple(snapshot.mail, MAIL_PACKAGE, SableOrange)
        }
    GlanceFrameCard(
        title = GlanceWidgets.label(widget),
        headline = liveDetail(datum, findLiveApp(apps, packageName)),
        detail = "",
        accent = accent,
        // The Hub widget opens Start's own Hub page (Sable Hub has no screen of its own).
        onClick = { if (widget == GlanceWidgets.HUB) onShowHub() else open(packageName) },
        modifier = modifier,
    )
}

/** A Glance widget drawn as an Active Frame: its value large, its name along the bottom. */
@Composable
private fun GlanceFrameCard(
    title: String,
    headline: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(Bb10Card)
                .border(if (focused) 3.dp else 1.dp, if (focused) Bb10Blue else Bb10Line, shape)
                .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(accent))
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = FRAME_LABEL_HEIGHT),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = Bb10Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Bb10Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = title,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(FRAME_LABEL_HEIGHT)
                    .background(if (focused) Bb10Blue else Color(0xE6000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Bb10OnBlue else Color.White,
            maxLines = 1,
        )
    }
}

/** "72° Partly cloudy" and "Seattle" from the Weather snapshot ([HomeHeader.weather]). */
private fun weatherLines(datum: LiveDatum): Pair<String, String> {
    if (datum.availability != LiveAvailability.LIVE && datum.availability != LiveAvailability.EMPTY) {
        return "Weather" to datum.detail.ifBlank { "unavailable" }
    }
    return HomeHeader.weather(datum.detail)?.let { (headline, place) -> headline to place.ifBlank { "Weather" } }
        ?: ("Weather" to datum.detail)
}

/**
 * One app grid page (Ripe 10 / BB10 page model): five columns of labelled,
 * tile-free icons in the user's order (Edit Home reorders them), as many rows
 * as the window fits ([AppGrid.rowsFor]); the icon size follows the cell.
 * Arrows move focus, Enter opens, Shift+Enter opens the Dual copy, Space, Menu
 * or Alt+Enter opens the app's actions, long press on an icon its actions and
 * on an empty place Edit Home; typing searches.
 */
@Composable
private fun AppGridPage(
    apps: List<AppEntry>,
    rows: Int,
    /** Every grid app's name, so a shortened name never repeats another ([AppGrid.gridLabel]). */
    gridLabels: Set<String>,
    active: Boolean,
    onLaunchApp: (AppEntry) -> Unit,
    onLaunchSecond: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onEditHome: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(active, apps.firstOrNull()?.stableKey()) {
        if (active && apps.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onEditHome() }) }
                .padding(horizontal = GRID_PADDING, vertical = GRID_PADDING),
    ) {
        val iconDp =
            AppGrid.iconSizeFor(
                cellWidthDp = (maxWidth / AppGrid.COLUMNS).value.toInt(),
                cellHeightDp = (maxHeight / rows).value.toInt(),
                labelDp = GRID_LABEL_LINE_DP + GRID_CELL_CHROME_DP,
            )
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (column in 0 until AppGrid.COLUMNS) {
                        val app = apps.getOrNull(row * AppGrid.COLUMNS + column)
                        if (app == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            GridCell(
                                app = app,
                                label = AppGrid.gridLabel(app.label, gridLabels),
                                iconDp = iconDp,
                                onOpen = { onLaunchApp(app) },
                                onOpenSecond = { onLaunchSecond(app) },
                                onActions = { onOpenPeek(app) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(if (row == 0 && column == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A grid icon: the app's glyph (no tile) with its short name on one line
 * under it ([AppGrid.gridLabel]); focus fills the cell BB10 blue.
 */
@Composable
private fun GridCell(
    app: AppEntry,
    label: String,
    iconDp: Int,
    onOpen: () -> Unit,
    onOpenSecond: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier =
            modifier
                .padding(GRID_CELL_GAP)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Bb10Blue.copy(alpha = 0.32f) else Color.Transparent)
                .border(2.dp, if (focused) Bb10Blue else Color.Transparent, shape)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val decision =
                        AllAppsKeyPolicy.decide(
                            keyCode = native.keyCode,
                            unicode = 0,
                            metaState = native.metaState,
                            repeat = native.repeatCount > 0,
                        )
                    val action: (() -> Unit)? =
                        when (decision.action) {
                            AllAppsKeyAction.OpenSecond -> onOpenSecond
                            AllAppsKeyAction.Actions, AllAppsKeyAction.ToggleDetail, AllAppsKeyAction.AppInfo -> onActions
                            else -> null
                        }
                    if (action != null && event.type == KeyEventType.KeyDown) action()
                    action != null
                }.combinedClickable(onClick = onOpen, onLongClick = onActions)
                .semantics {
                    contentDescription = app.label + (app.profileLabel?.let { ", ${AppProfiles.badge(it)}" } ?: "")
                }.padding(vertical = GRID_CELL_INNER_PADDING, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppMark(app = app, size = iconDp)
            val badge = app.profileLabel
            if (badge != null) {
                Text(
                    text = AppProfiles.badge(badge),
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .border(1.dp, Bb10Muted, RoundedCornerShape(6.dp))
                            .padding(horizontal = 3.dp),
                    fontSize = 8.sp,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(GRID_ICON_LABEL_GAP))
        Text(
            text = label,
            // No tracking: "Minesweeper" fits a fifth of 411dp on one line.
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
            color = Bb10Text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A live datum's line, or how to reach its app when there is nothing live to show. */
private fun liveDetail(
    datum: LiveDatum,
    app: AppEntry?,
): String =
    when (datum.availability) {
        LiveAvailability.LIVE, LiveAvailability.EMPTY -> datum.detail
        else -> if (app == null) "not installed" else "open ${app.label}"
    }

/** Battery level and charging from the sticky battery broadcast (no receiver kept). */
private fun readBattery(context: Context): Pair<Int?, Boolean> =
    runCatching {
        val intent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return@runCatching null to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) level * PERCENT / scale else null
        percent to
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }.getOrDefault(null to false)

private fun destinationFor(screen: String): SableStartDestination =
    when (screen.lowercase(Locale.ROOT)) {
        "apps" -> SableStartDestination.Apps
        "search" -> SableStartDestination.Search
        "appearance" -> SableStartDestination.Appearance
        "quickbar", "home", "edit" -> SableStartDestination.EditHome
        else -> SableStartDestination.Start
    }

private fun backDestination(
    current: SableStartDestination,
    returnTo: SableStartDestination,
): SableStartDestination =
    when (current) {
        SableStartDestination.Search, SableStartDestination.Peek -> returnTo
        SableStartDestination.SlotPicker -> SableStartDestination.Peek
        else -> SableStartDestination.Start
    }

/** A Glance or Home module card; the focused card is filled with the accent. */
@Composable
private fun GlanceCard(
    title: String,
    detail: String,
    accent: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    large: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (large) 72.dp else 56.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.focusable())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (large) 34.dp else 26.dp)
                    .clip(if (round) CircleShape else RoundedCornerShape(6.dp))
                    .background(accent),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Hub page (left of Active Frames; visual "5. Hub"): the one Sable Hub. Under the "Sable Hub"
 * title the unread bar (after the Ripe launcher's BB7 row: each app with unread notifications as
 * its small icon and count, "+N" for the rest, from Sable Hub's listener), then the All /
 * Messages / Calls / Contacts tabs. Start shows counts and names only (the snapshots carry no
 * message text) and opens Sable Messages, Phone, Contacts or the owning app for the rest.
 */
@Composable
private fun HubPage(
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    filter: HubFilter,
    active: Boolean,
    onFilter: (HubFilter) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenHubSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
    refreshGeneration: Int,
    onRequestContactsPermission: () -> Unit,
) {
    val context = LocalContext.current
    val messagesApp = findLiveApp(apps, MESSAGES_PACKAGE)
    val mailApp = findLiveApp(apps, MAIL_PACKAGE)
    val contactsApp = findLiveApp(apps, CONTACTS_PACKAGE)
    // Hub has no launcher entry; its snapshot provider answering is how Start knows it is there.
    val hubInstalled = snapshot.messages.availability != LiveAvailability.UNAVAILABLE
    val personalApps = remember(apps) { apps.filter { it.inLauncherUser }.associateBy { it.component.packageName } }
    val barItems = remember(snapshot.hubApps, personalApps) { HubBar.items(snapshot.hubApps) { it in personalApps } }
    val peopleRepository = remember(context) { PeopleRepository(context) }
    val contactsTab = filter == HubFilter.Contacts
    val canReadContacts = remember(refreshGeneration, contactsTab) { peopleRepository.hasPermission() }
    // Kept across reloads (live refreshes, Start coming back) so the list never blinks empty.
    var people by remember { mutableStateOf(emptyList<HubPerson>()) }
    var peopleLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(contactsTab, canReadContacts, refreshGeneration) {
        if (contactsTab) {
            people = if (canReadContacts) peopleRepository.load() else emptyList()
            peopleLoaded = true
        }
    }
    val tabFocus = remember { FocusRequester() }
    val personFocus = remember { mutableMapOf<Long, FocusRequester>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { tabFocus.requestFocus() }
        }
    }

    fun requesterFor(person: HubPerson): FocusRequester = personFocus.getOrPut(person.id) { FocusRequester() }

    fun openMessages() {
        val app = messagesApp
        if (app != null) {
            onLaunchApp(app)
        } else {
            onStartIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
        }
    }

    fun openBarItem(item: HubBar.Item) {
        when (val target = HubBar.target(item.packageName)) {
            is HubBar.Target.Tab -> onFilter(target.filter)
            HubBar.Target.Private -> onOpenHubSettings()
            HubBar.Target.OpenApp -> personalApps[item.packageName]?.let(onLaunchApp)
        }
    }

    val actions =
        HubActions(
            onLaunchApp = onLaunchApp,
            onStartIntent = onStartIntent,
            onOpenMessages = ::openMessages,
            onOpenHubSettings = onOpenHubSettings,
            onRequestContactsPermission = onRequestContactsPermission,
        )

    /** Contacts tab: a letter (or digit) key jumps to the first person of that letter. */
    fun jumpKey(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        if (!contactsTab || people.isEmpty() || native.metaState and HUB_JUMP_BLOCKING_META != 0) return false
        val char = native.unicodeChar.takeIf { it > 0 }?.toChar() ?: return false
        val index = HubPeople.jump(people, char) ?: return false
        if (event.type == KeyEventType.KeyDown) {
            val person = people[index]
            scope.launch {
                // The header item comes first, so a person's list index is one more than theirs.
                listState.scrollToItem(index + 1)
                withFrameNanos { }
                runCatching { requesterFor(person).requestFocus() }
            }
        }
        return true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent(::jumpKey),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Text(
                text = HUB_LABEL,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                // The strip's own text colour (BB10 palette), not the appearance's content colour.
                color = Bb10Text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            HubUnreadBar(
                items = barItems,
                appFor = { personalApps[it] },
                onOpen = ::openBarItem,
            )
            Spacer(Modifier.height(8.dp))
            HubTabs(filter = filter, firstTab = tabFocus, onFilter = onFilter)
        }
        if (contactsTab) {
            hubContactsItems(
                people = people,
                loaded = peopleLoaded,
                canRead = canReadContacts,
                contactsApp = contactsApp,
                messagesPackage = messagesApp?.component?.packageName,
                requesterFor = ::requesterFor,
                actions = actions,
            )
        } else {
            hubSummaryItems(
                filter = filter,
                snapshot = snapshot,
                summary = if (hubInstalled) HubBar.summary(barItems) else null,
                messagesApp = messagesApp,
                mailApp = mailApp,
                actions = actions,
            )
        }
    }
}

/**
 * The Hub page's unread bar: one item per app (its icon and count), most unread first, "+N" in
 * the last slot when they do not all fit; Enter or a tap on "+N" shows every app. While the device
 * is locked or Sable private mode is on, Hub sends one total without app names (a lock mark).
 */
@Composable
private fun HubUnreadBar(
    items: List<HubBar.Item>,
    appFor: (String) -> AppEntry?,
    onOpen: (HubBar.Item) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val slots = HubBar.slots(maxWidth.value.toInt())
        val layout = HubBar.layout(items, slots)
        val rows = if (expanded) items.chunked(slots) else listOf(layout.visible)
        val overflow = if (expanded) 0 else layout.overflow
        if (items.isEmpty()) {
            Text(
                text = "no new notifications",
                style = MaterialTheme.typography.labelLarge,
                color = Bb10Muted,
                modifier = Modifier.height(HUB_BAR_ITEM_HEIGHT).padding(vertical = 6.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(HubBar.GAP_DP.dp)) {
                        row.forEach { item ->
                            HubBarItem(
                                item = item,
                                app = appFor(item.packageName),
                                onOpen = { onOpen(item) },
                            )
                        }
                        if (rowIndex == rows.lastIndex && overflow > 0) {
                            HubBarChip(
                                description = "$overflow more apps with notifications",
                                onClick = { expanded = true },
                            ) {
                                Text(
                                    text = HubBar.overflowLabel(overflow),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LocalContentColor.current,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubBarItem(
    item: HubBar.Item,
    app: AppEntry?,
    onOpen: () -> Unit,
) {
    val count = HubBar.countLabel(item.count)
    val description =
        if (item.redacted) {
            "$count new notifications, apps hidden"
        } else {
            "${app?.label ?: item.packageName}, $count new"
        }
    HubBarChip(description = description, onClick = onOpen) {
        if (app != null) {
            AppMark(app = app, size = HUB_BAR_ICON_DP)
        } else {
            HubLockMark()
        }
        Text(
            text = count,
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current,
            maxLines = 1,
        )
    }
}

/** One fixed-width, focusable slot of the unread bar; the focused one is BB10 blue. */
@Composable
private fun HubBarChip(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier =
            Modifier
                .width(HubBar.ITEM_DP.dp)
                .height(HUB_BAR_ITEM_HEIGHT)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Bb10Blue else Bb10Card)
                .border(1.dp, Bb10Line, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        // 56dp: 4 + 20dp icon + 4 + room for "99+" + 4.
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (focused) Bb10OnBlue else Bb10Text) {
            content()
        }
    }
}

/** A small padlock: Hub's counts-only total (locked device or Sable private mode). */
@Composable
private fun HubLockMark() {
    val ink = LocalContentColor.current
    ComposeCanvas(Modifier.size(HUB_BAR_ICON_DP.dp)) {
        val unit = size.minDimension
        val stroke = unit * 0.12f
        drawArc(
            color = ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(unit * 0.3f, unit * 0.12f),
            size = Size(unit * 0.4f, unit * 0.44f),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(unit * 0.2f, unit * 0.42f),
            size = Size(unit * 0.6f, unit * 0.46f),
            cornerRadius = CornerRadius(unit * 0.08f),
        )
    }
}

/** What the Hub page's tabs and cards open. */
private class HubActions(
    val onLaunchApp: (AppEntry) -> Unit,
    val onStartIntent: (Intent) -> Unit,
    val onOpenMessages: () -> Unit,
    val onOpenHubSettings: () -> Unit,
    val onRequestContactsPermission: () -> Unit,
)

/** The Hub page tabs: All · Messages · Calls · Contacts; All takes focus when the page opens. */
@Composable
private fun HubTabs(
    filter: HubFilter,
    firstTab: FocusRequester,
    onFilter: (HubFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HubFilter.entries.forEach { tab ->
            val selected = tab == filter
            Text(
                text = tab.label,
                modifier =
                    Modifier
                        .then(if (tab == HubFilter.All) Modifier.focusRequester(firstTab) else Modifier)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        ).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .sableFocusRing()
                        .clickable { onFilter(tab) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * The All, Messages and Calls tabs: Messages and Mail, Missed calls, and on All the notification
 * summary ([summary] is null when Sable Hub is absent, which also hides Hub settings).
 */
private fun androidx.compose.foundation.lazy.LazyListScope.hubSummaryItems(
    filter: HubFilter,
    snapshot: LiveSurfaceSnapshot,
    summary: String?,
    messagesApp: AppEntry?,
    mailApp: AppEntry?,
    actions: HubActions,
) {
    val showMessages = filter != HubFilter.Calls
    val showCalls = filter != HubFilter.Messages
    if (showMessages) {
        item(key = "messages") {
            GlanceCard(
                title = "Messages",
                detail = liveDetail(snapshot.messages, messagesApp),
                accent = SableBlue,
                onClick = actions.onOpenMessages,
            )
        }
        item(key = "mail") {
            GlanceCard(
                title = "Mail",
                detail = liveDetail(snapshot.mail, mailApp),
                accent = Color(0xFFE5484D),
                onClick = mailApp?.let { { actions.onLaunchApp(it) } },
            )
        }
    }
    if (showCalls) {
        item(key = "calls") {
            // Start holds no call-log permission: the Phone app shows the missed calls.
            GlanceCard(
                title = "Missed calls",
                detail = "recent calls in Phone",
                accent = SableGreen,
                onClick = { actions.onStartIntent(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)) },
            )
        }
    }
    if (filter == HubFilter.All) {
        item(key = "notifications") {
            // Android owns the notifications; its history lists what was dismissed too.
            GlanceCard(
                title = "Notifications",
                detail = summary ?: "Sable Hub not installed",
                accent = SableSun,
                onClick = { actions.onStartIntent(Intent(ACTION_NOTIFICATION_HISTORY)) },
            )
        }
    }
    if (summary != null) {
        item(key = "settings") {
            Spacer(Modifier.height(4.dp))
            MetroLink(text = "Hub settings", onClick = actions.onOpenHubSettings)
        }
    }
}

/**
 * The Contacts tab: favourites, then A to Z, as in the Contacts app. Enter opens the contact,
 * Call and Message act on its default number (Message in Sable Messages); a letter key jumps
 * ([HubPage]). Without READ_CONTACTS the tab offers to allow it, or the Contacts app.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.hubContactsItems(
    people: List<HubPerson>,
    loaded: Boolean,
    canRead: Boolean,
    contactsApp: AppEntry?,
    messagesPackage: String?,
    requesterFor: (HubPerson) -> FocusRequester,
    actions: HubActions,
) {
    val onStartIntent = actions.onStartIntent
    val openContacts = contactsApp?.let { app -> { actions.onLaunchApp(app) } }
    if (!canRead || people.isEmpty()) {
        item(key = "contacts-empty") {
            if (!canRead) {
                GlanceCard(
                    title = "Allow contacts",
                    detail = "Start shows names here; nothing leaves the phone",
                    accent = SablePurple,
                    onClick = actions.onRequestContactsPermission,
                )
            } else {
                EmptyState(if (loaded) "No contacts yet." else "Loading contacts…")
            }
        }
        if (openContacts != null) {
            item(key = "contacts-open") {
                MetroLink(text = "open Contacts", onClick = openContacts)
            }
        }
        return
    }
    itemsIndexed(items = people, key = { _, person -> "person:" + person.id }) { index, person ->
        if (HubPeople.startsSection(people, index)) {
            Text(
                text = HubPeople.sectionOf(person),
                style = MaterialTheme.typography.titleMedium,
                color = Bb10Blue,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
        }
        val number = person.number
        HubPersonRow(
            person = person,
            nameModifier = Modifier.focusRequester(requesterFor(person)),
            onOpen = { onStartIntent(PeopleRepository.viewIntent(person)) },
            onCall = number?.let { { onStartIntent(PeopleRepository.callIntent(it)) } },
            onMessage = number?.let { { onStartIntent(PeopleRepository.messageIntent(it, messagesPackage)) } },
        )
    }
}

@Composable
private fun HubPersonRow(
    person: HubPerson,
    nameModifier: Modifier,
    onOpen: () -> Unit,
    onCall: (() -> Unit)?,
    onMessage: (() -> Unit)?,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = (if (person.starred) "★ " else "") + person.name,
            modifier =
                nameModifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(shape)
                    .background(if (focused) Bb10Blue else Color.Transparent)
                    .clickable(onClick = onOpen)
                    .semantics { contentDescription = person.name + if (person.starred) ", favourite" else "" }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) Bb10OnBlue else Bb10Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onCall != null) HubPersonAction(label = "call", description = "Call ${person.name}", onClick = onCall)
        if (onMessage != null) HubPersonAction(label = "text", description = "Message ${person.name}", onClick = onMessage)
    }
}

@Composable
private fun HubPersonAction(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = label,
        modifier =
            Modifier
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Bb10Blue else Bb10Card)
                .border(1.dp, Bb10Line, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (focused) Bb10OnBlue else Bb10Text,
        maxLines = 1,
    )
}

/**
 * Quick slot options (Long press / Menu / Fn+Enter on a slot): the Hub slot
 * offers its filters, the Phone slot Dial pad, Recent calls and Contacts, every
 * slot Edit Home (reorder, replace, reset).
 */
@Composable
private fun SlotOptionsScreen(
    slot: QuickSlot,
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onHubFilter: (HubFilter) -> Unit,
    onOpenHubSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onEditHome: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(slot) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            MetroHeader(title = slot.label.lowercase(Locale.getDefault()), onBack = onBack)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.focusRequester(firstFocus)) {
                ContextAction(mark = "→", label = "open", onClick = onOpen)
            }
            when (slot.token) {
                QuickBar.HUB -> {
                    HubFilter.entries.forEach { filter ->
                        ContextAction(mark = "◇", label = "Hub page · ${filter.label}", onClick = { onHubFilter(filter) })
                    }
                    ContextAction(mark = "⚙", label = "Hub settings", onClick = onOpenHubSettings)
                }
                QuickBar.PHONE -> {
                    ContextAction(mark = "☎", label = "dial pad", onClick = { onStartIntent(Intent(Intent.ACTION_DIAL)) })
                    ContextAction(
                        mark = "↺",
                        label = "recent calls",
                        onClick = { onStartIntent(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)) },
                    )
                    findLiveApp(apps, CONTACTS_PACKAGE)?.let { contacts ->
                        ContextAction(mark = "@", label = "contacts", onClick = { onLaunchApp(contacts) })
                    }
                }
                else -> Unit
            }
            slot.app?.let { app ->
                ContextAction(mark = "ⓘ", label = "app info", onClick = { onAppInfo(app) })
            }
            ContextAction(mark = "▭", label = "edit Home and quick bar", onClick = onEditHome)
        }
    }
}

/**
 * The BB10 bar under Active Frames and the app grid: Phone · Search · Camera
 * by default, icon only, white on the dark bar, the focused slot BB10 blue.
 * Enter or a tap opens a slot; long press, Menu or Alt+Enter (Fn+Enter) opens
 * the slot's options; Space on a Hub slot peeks at the Hub page; digits typed
 * while Phone is focused go to the dialer (handled by [StartStrip]).
 */
@Composable
private fun Bb10ActionBar(
    slots: List<QuickSlot>,
    onSlot: (QuickSlot) -> Unit,
    onOptions: (QuickSlot) -> Unit,
    onHubPeek: () -> Unit,
    onPhoneFocusChanged: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT_DP.dp)
                .background(Brush.verticalGradient(listOf(Bb10BarTop, Bb10BarBottom)))
                .border(1.dp, Bb10Line)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        slots.forEachIndexed { index, slot ->
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (focused) Bb10Blue else Color.Transparent)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (slot.token == QuickBar.PHONE) onPhoneFocusChanged(state.isFocused)
                        }.onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            val alt = native.metaState and (QuickBar.META_FUNCTION_ON or QuickBar.META_ALT_ON) != 0
                            val action: (() -> Unit)? =
                                when {
                                    native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU ||
                                        (native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt) -> {
                                        { onOptions(slot) }
                                    }
                                    native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE && !alt && slot.token == QuickBar.HUB -> onHubPeek
                                    else -> null
                                }
                            if (action != null && event.type == KeyEventType.KeyDown && native.repeatCount == 0) action()
                            action != null
                        }.combinedClickable(
                            onClick = { onSlot(slot) },
                            onLongClick = { onOptions(slot) },
                        ).semantics {
                            contentDescription = "${slot.label}, ${QuickBar.shortcutLabel(index)}"
                        },
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides if (focused) Bb10OnBlue else Bb10Text) {
                    QuickSlotMark(slot = slot, size = BAR_GLYPH_DP, monochrome = true)
                }
            }
        }
    }
}

/**
 * A slot's mark. Phone, Search, Camera and All Apps are drawn as BB10-style
 * glyphs ([monochrome] in the bar's content colour); app slots show the app's icon.
 */
@Composable
private fun QuickSlotMark(
    slot: QuickSlot,
    size: Int,
    monochrome: Boolean = false,
) {
    val app = slot.app
    val glyph =
        slot.token in setOf(QuickBar.COMMAND, QuickBar.ALL_APPS, QuickBar.HUB) ||
            (monochrome && slot.token in setOf(QuickBar.PHONE, QuickBar.CAMERA))
    if (app != null && !glyph) {
        AppMark(app = app, size = size)
        return
    }
    val color = if (monochrome) LocalContentColor.current else MaterialTheme.colorScheme.primary
    ComposeCanvas(Modifier.size(size.dp)) {
        val unit = this.size.minDimension
        val stroke = unit * 0.1f
        when (slot.token) {
            QuickBar.COMMAND -> {
                // Magnifier: Search / Command.
                drawCircle(color = color, radius = unit * 0.28f, center = Offset(unit * 0.42f, unit * 0.42f), style = Stroke(stroke))
                drawLine(
                    color = color,
                    start = Offset(unit * 0.62f, unit * 0.62f),
                    end = Offset(unit * 0.88f, unit * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            QuickBar.PHONE -> {
                // Handset: a "C" arc with round ear and mouth pieces.
                val inset = unit * 0.2f
                drawArc(
                    color = color,
                    startAngle = 100f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(unit - inset * 2, unit - inset * 2),
                    style = Stroke(unit * 0.15f, cap = StrokeCap.Round),
                )
                val radius = (unit - inset * 2) / 2f
                val center = Offset(unit / 2f, unit / 2f)
                listOf(100.0, 260.0).forEach { angle ->
                    val rad = Math.toRadians(angle)
                    drawCircle(
                        color = color,
                        radius = unit * 0.11f,
                        center = Offset(center.x + radius * kotlin.math.cos(rad).toFloat(), center.y + radius * kotlin.math.sin(rad).toFloat()),
                    )
                }
            }
            QuickBar.HUB ->
                // Hub: three short lines, as on the page indicator.
                for (line in 0..2) {
                    val y = unit * (0.25f + line * 0.25f)
                    drawLine(color, Offset(unit * 0.12f, y), Offset(unit * 0.88f, y), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            QuickBar.CAMERA -> {
                // Camera body, viewfinder bump and lens.
                drawRoundRect(
                    color = color,
                    topLeft = Offset(unit * 0.1f, unit * 0.3f),
                    size = Size(unit * 0.8f, unit * 0.52f),
                    cornerRadius = CornerRadius(unit * 0.08f),
                    style = Stroke(stroke),
                )
                drawRect(color = color, topLeft = Offset(unit * 0.34f, unit * 0.18f), size = Size(unit * 0.32f, unit * 0.12f))
                drawCircle(color = color, radius = unit * 0.14f, center = Offset(unit * 0.5f, unit * 0.56f), style = Stroke(stroke))
            }
            else -> {
                // Four squares: All Apps.
                val cell = unit * 0.36f
                val gap = unit * 0.14f
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(unit * 0.07f + col * (cell + gap), unit * 0.07f + row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(unit * 0.06f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * All Apps "Choose base bar slot" (sable-all-apps-titan2 panel 5): the bar is
 * full, so the app replaces the slot the user picks. Each slot says Keep, the
 * focused one "Replace with <app>", and Search "Required path" (it cannot be
 * replaced). Enter replaces; Back returns to the app's actions.
 */
@Composable
private fun SlotPickerScreen(
    app: AppEntry,
    tokens: List<String>,
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
    onBack: () -> Unit,
    onReplace: (Int) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(app) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            MetroHeader(title = "choose bar slot", onBack = onBack)
            Text(
                text = "Add ${app.label} to the bar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        itemsIndexed(tokens, key = { _, token -> token }) { index, token ->
            val slot = resolveQuickSlot(token, apps, appByKey)
            var focused by remember { mutableStateOf(false) }
            val required = QuickBar.isRequired(token)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .heightIn(min = 56.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (focused && !required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .sableFocusRing()
                        .clickable { if (!required) onReplace(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (slot != null) QuickSlotMark(slot = slot, size = 30)
                Spacer(Modifier.width(14.dp))
                val content = if (focused && !required) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Column(modifier = Modifier.weight(1f)) {
                    Text(slot?.label ?: quickSlotFallbackLabel(token), style = MaterialTheme.typography.titleMedium, color = content)
                    Text(
                        text = QuickBar.pickerLine(token, focused, app.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.8f),
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Every app stays on the app grid pages (Alt+Right).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The lists Edit Home reorders. */
private enum class EditSection {
    Grid,
    QuickBar,
    Glance,
}

/** The item picked up with Enter, and the layout before the pick (Back restores it). */
private data class EditMove(
    val section: EditSection,
    val key: String,
    val before: HomeEdit,
)

private fun HomeEdit.list(section: EditSection): List<String> =
    when (section) {
        EditSection.Grid -> gridOrder
        EditSection.QuickBar -> quickBar
        EditSection.Glance -> glanceWidgets
    }

private fun HomeEdit.with(
    section: EditSection,
    list: List<String>,
): HomeEdit =
    when (section) {
        EditSection.Grid -> copy(gridOrder = list)
        EditSection.QuickBar -> copy(quickBar = list)
        EditSection.Glance -> copy(glanceWidgets = list)
    }

/** Moves [key] by [delta] places in [section]: grid icons shift like a dragged icon, list items swap. */
private fun HomeEdit.moved(
    section: EditSection,
    key: String,
    delta: Int,
): HomeEdit {
    val list = list(section)
    val index = list.indexOf(key)
    val next =
        when (section) {
            EditSection.Grid -> AppGrid.move(list, index, delta)
            EditSection.QuickBar -> QuickBar.move(list, index, delta)
            EditSection.Glance -> HomeApps.move(list, index, delta)
        }
    return with(section, next)
}

/**
 * Edit Home: one mode that orders the app grid, the bottom bar and the Glance
 * widgets shown as frames while no app runs, and shows or hides the Active
 * Frames header (status dials, weather and date, search row). Drag, or with
 * the keyboard: Enter picks an item up and drops it, arrows move it while
 * picked up (all four on the grid, Left/Right on the bar, Up/Down in the
 * list), Alt+arrows (Fn+arrows) move the focused item at once, Menu or Space
 * opens its options, Back cancels the move or leaves without saving, Done
 * saves.
 */
@Composable
private fun EditHomeScreen(
    apps: List<AppEntry>,
    initial: HomeEdit,
    defaults: HomeEdit,
    backGeneration: Int,
    onCancel: () -> Unit,
    onDone: (HomeEdit) -> Unit,
    onAppearance: () -> Unit,
) {
    val appByKey = apps.associateBy { it.stableKey() }
    var draft by remember { mutableStateOf(initial) }
    var moving by remember { mutableStateOf<EditMove?>(null) }
    // "<section>:<key>" whose options are open.
    var optionsFor by remember { mutableStateOf<String?>(null) }
    // null: editing; "bar": adding a slot; "bar:<index>": replacing that slot.
    var picking by remember { mutableStateOf<String?>(null) }
    val startBack = remember { backGeneration }
    val doneFocus = remember { FocusRequester() }

    LaunchedEffect(backGeneration) {
        if (backGeneration > startBack) {
            val move = moving
            when {
                picking != null -> picking = null
                move != null -> {
                    draft = move.before
                    moving = null
                }
                optionsFor != null -> optionsFor = null
                else -> onCancel()
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { doneFocus.requestFocus() }
    }

    fun move(
        section: EditSection,
        key: String,
        delta: Int,
    ) {
        draft = draft.moved(section, key, delta)
    }

    fun itemKeys(
        section: EditSection,
        key: String,
        event: KeyEvent,
    ): Boolean {
        val native = event.nativeKeyEvent
        val down = event.type == KeyEventType.KeyDown
        val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
        val picked = moving?.let { it.section == section && it.key == key } == true
        val arrow = AppGrid.arrowDelta(native.keyCode)
        // Grid: all four arrows; bar: Left/Right; list: Up/Down (one place).
        val sectionDelta =
            when (section) {
                EditSection.Grid -> arrow
                EditSection.QuickBar -> arrow?.takeIf { it == -1 || it == 1 }
                EditSection.Glance -> arrow?.takeIf { it == -AppGrid.COLUMNS || it == AppGrid.COLUMNS }?.let { it / AppGrid.COLUMNS }
            }
        val delta = sectionDelta?.takeIf { alt || picked }
        val optionsKey = "${section.name}:$key"
        return when {
            delta != null -> {
                if (down) move(section, key, delta)
                true
            }
            native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && !alt -> {
                if (down && native.repeatCount == 0) {
                    moving = if (picked) null else EditMove(section, key, draft)
                }
                true
            }
            native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU ||
                native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE ||
                (native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt) -> {
                if (down && native.repeatCount == 0) optionsFor = if (optionsFor == optionsKey) null else optionsKey
                true
            }
            else -> false
        }
    }

    val pickKind = picking
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        if (pickKind != null) {
            val choices = quickBarChoices(apps, appByKey, draft.quickBar)
            item {
                MetroHeader(
                    title = if (pickKind == "bar") "add a slot" else "replace slot",
                    onBack = { picking = null },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (choices.isEmpty()) {
                item { EmptyState("Every available app is already there.") }
            }
            items(choices, key = { it.token }) { choice ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .sableFocusRing()
                            .clickable {
                                draft =
                                    if (pickKind == "bar") {
                                        draft.copy(quickBar = QuickBar.add(draft.quickBar, choice.token))
                                    } else {
                                        val index = pickKind.substringAfter(':').toIntOrNull() ?: -1
                                        draft.copy(quickBar = QuickBar.replace(draft.quickBar, index, choice.token))
                                    }
                                picking = null
                            }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickSlotMark(slot = choice, size = 32)
                    Spacer(Modifier.width(14.dp))
                    Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            return@LazyColumn
        }

        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).width(48.dp)) {
                    Text(text = "‹", fontSize = 32.sp, fontWeight = FontWeight.Light)
                }
                Text(
                    text = "Edit Home",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(
                    onClick = { onDone(draft) },
                    modifier = Modifier.focusRequester(doneFocus).sableFocusRing(),
                ) { Text("Done", style = MaterialTheme.typography.titleMedium) }
            }
            Text(
                text = "Drag or use keyboard order",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Enter picks up and drops · arrows move · Alt+arrows move at once · Menu for options · Back cancels",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SectionLabel("App grid")
            Spacer(Modifier.height(4.dp))
        }
        items(draft.gridOrder.chunked(AppGrid.COLUMNS), key = { row -> "grid:" + row.first() }) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val app = appByKey[key]
                    EditGridCell(
                        app = app,
                        picked = moving?.let { it.section == EditSection.Grid && it.key == key } == true,
                        onKey = { event -> itemKeys(EditSection.Grid, key, event) },
                        onClick = { optionsFor = if (optionsFor == "Grid:$key") null else "Grid:$key" },
                        onDragStep = { delta -> move(EditSection.Grid, key, delta) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(AppGrid.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
            val open = optionsFor?.takeIf { it.startsWith("Grid:") }?.substringAfter(':')
            if (open != null && open in row) {
                val index = draft.gridOrder.indexOf(open)
                EditOptions(
                    buildList {
                        if (index > 0) add("move left" to { move(EditSection.Grid, open, -1) })
                        if (index < draft.gridOrder.lastIndex) add("move right" to { move(EditSection.Grid, open, 1) })
                        if (index >= AppGrid.COLUMNS) add("move up a row" to { move(EditSection.Grid, open, -AppGrid.COLUMNS) })
                        if (index + AppGrid.COLUMNS <= draft.gridOrder.lastIndex) {
                            add("move down a row" to { move(EditSection.Grid, open, AppGrid.COLUMNS) })
                        }
                        add("move to the start" to { move(EditSection.Grid, open, -index) })
                    },
                )
            }
        }
        item(key = "bar") {
            Spacer(Modifier.height(10.dp))
            SectionLabel("Bottom bar")
            Spacer(Modifier.height(6.dp))
            EditQuickBarRow(
                tokens = draft.quickBar,
                apps = apps,
                appByKey = appByKey,
                moving = moving?.takeIf { it.section == EditSection.QuickBar }?.key,
                onKey = { token, event -> itemKeys(EditSection.QuickBar, token, event) },
                onClick = { token ->
                    optionsFor = if (optionsFor == "QuickBar:$token") null else "QuickBar:$token"
                },
                onDragStep = { token, delta -> move(EditSection.QuickBar, token, delta) },
            )
            val barOptions = optionsFor?.takeIf { it.startsWith("QuickBar:") }?.substringAfter(':')
            if (barOptions != null && barOptions in draft.quickBar) {
                val index = draft.quickBar.indexOf(barOptions)
                val required = QuickBar.isRequired(barOptions)
                EditOptions(
                    buildList {
                        if (index > 0) add("move left" to { move(EditSection.QuickBar, barOptions, -1) })
                        if (index < draft.quickBar.lastIndex) add("move right" to { move(EditSection.QuickBar, barOptions, 1) })
                        if (!required) {
                            add(
                                "replace" to {
                                    picking = "bar:$index"
                                    optionsFor = null
                                },
                            )
                            add(
                                "remove" to {
                                    draft = draft.copy(quickBar = QuickBar.remove(draft.quickBar, index))
                                    optionsFor = null
                                },
                            )
                        }
                    },
                )
                if (required) {
                    Text(
                        text = "Search always stays on the bar; it can move.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (draft.quickBar.size < QuickBar.MAX_SLOTS) {
                MetroLink(text = "+ add a slot", onClick = { picking = "bar" })
            }
            MetroLink(
                text = "reset bar to Phone · Search · Camera",
                onClick = { draft = draft.copy(quickBar = QuickBar.DEFAULT) },
            )
            Spacer(Modifier.height(10.dp))
            SectionLabel("Frames while no app is open")
            Spacer(Modifier.height(4.dp))
        }
        itemsIndexed(draft.glanceWidgets, key = { _, widget -> "glance:$widget" }) { index, widget ->
            EditListRow(
                label = GlanceWidgets.label(widget),
                leading = {},
                picked = moving?.let { it.section == EditSection.Glance && it.key == widget } == true,
                onKey = { event -> itemKeys(EditSection.Glance, widget, event) },
                onClick = { optionsFor = if (optionsFor == "Glance:$widget") null else "Glance:$widget" },
                onDragStep = { delta -> move(EditSection.Glance, widget, delta) },
            )
            if (optionsFor == "Glance:$widget") {
                EditOptions(
                    listOf(
                        "move up" to { move(EditSection.Glance, widget, -1) },
                        "move down" to { move(EditSection.Glance, widget, 1) },
                        "hide" to {
                            draft = draft.copy(glanceWidgets = GlanceWidgets.toggle(draft.glanceWidgets, widget))
                            optionsFor = null
                        },
                    ).filterIndexed { i, _ -> !(i == 0 && index == 0) && !(i == 1 && index == draft.glanceWidgets.lastIndex) },
                )
            }
        }
        item(key = "glance-add") {
            GlanceWidgets.ALL.filterNot { it in draft.glanceWidgets }.forEach { widget ->
                MetroLink(
                    text = "+ show ${GlanceWidgets.label(widget)}",
                    onClick = { draft = draft.copy(glanceWidgets = GlanceWidgets.toggle(draft.glanceWidgets, widget)) },
                )
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Active Frames header")
            Spacer(Modifier.height(4.dp))
            val accent = MaterialTheme.colorScheme.primary
            HomeLayout.SECTIONS.forEach { section ->
                HomeToggleRow(
                    title = HomeLayout.label(section),
                    subtitle = HomeLayout.description(section),
                    selected = HomeLayout.isShown(draft.hiddenSections, section),
                    accent = accent,
                    onClick = { draft = draft.copy(hiddenSections = HomeLayout.toggle(draft.hiddenSections, section)) },
                )
            }
            Spacer(Modifier.height(10.dp))
            MetroLink(text = "appearance", onClick = onAppearance)
            MetroLink(text = "reset Home to default", onClick = { draft = defaults })
        }
    }
}

/** One app in the Edit Home grid: Enter picks it up, arrows move it, or drag it in any direction. */
@Composable
private fun EditGridCell(
    app: AppEntry?,
    picked: Boolean,
    onKey: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
    onDragStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = with(LocalDensity.current) { EditRowStep.toPx() }
    val currentStep by rememberUpdatedState(onDragStep)
    val label = app?.label ?: "App not installed"
    Column(
        modifier =
            modifier
                .padding(3.dp)
                .heightIn(min = 84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .sableFocusRing()
                .onPreviewKeyEvent(onKey)
                .clickable(onClick = onClick)
                .pointerInput(Unit) {
                    var travelX = 0f
                    var travelY = 0f
                    detectDragGestures(onDragStart = {
                        travelX = 0f
                        travelY = 0f
                    }) { change, amount ->
                        change.consume()
                        travelX += amount.x
                        travelY += amount.y
                        while (travelX > step) {
                            currentStep(1)
                            travelX -= step
                        }
                        while (travelX < -step) {
                            currentStep(-1)
                            travelX += step
                        }
                        while (travelY > step) {
                            currentStep(AppGrid.COLUMNS)
                            travelY -= step
                        }
                        while (travelY < -step) {
                            currentStep(-AppGrid.COLUMNS)
                            travelY += step
                        }
                    }
                }.semantics { contentDescription = "$label. Enter picks up, arrows move" }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (app != null) AppMark(app = app, size = 32)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A reorderable row: drag the handle, or the keys in [EditHomeScreen]. */
@Composable
private fun EditListRow(
    label: String,
    leading: @Composable () -> Unit,
    picked: Boolean,
    onKey: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
    onDragStep: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val step = with(LocalDensity.current) { EditRowStep.toPx() }
    val currentStep by rememberUpdatedState(onDragStep)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .sableFocusRing()
                .onPreviewKeyEvent(onKey)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "⠿",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .pointerInput(Unit) {
                        var travel = 0f
                        detectDragGestures(onDragStart = { travel = 0f }) { change, amount ->
                            change.consume()
                            travel += amount.y
                            while (travel > step) {
                                currentStep(1)
                                travel -= step
                            }
                            while (travel < -step) {
                                currentStep(-1)
                                travel += step
                            }
                        }
                    }.semantics { contentDescription = "Drag to reorder $label" }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

/** The base quick bar in Edit Home: slots in a row, dragged sideways or moved with Left/Right. */
@Composable
private fun EditQuickBarRow(
    tokens: List<String>,
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
    moving: String?,
    onKey: (String, KeyEvent) -> Boolean,
    onClick: (String) -> Unit,
    onDragStep: (String, Int) -> Unit,
) {
    val step = with(LocalDensity.current) { EditRowStep.toPx() }
    val currentStep by rememberUpdatedState(onDragStep)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tokens.forEach { token ->
            key(token) {
                val slot = resolveQuickSlot(token, apps, appByKey)
                val picked = token == moving
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .sableFocusRing()
                            .onPreviewKeyEvent { event -> onKey(token, event) }
                            .clickable { onClick(token) }
                            .pointerInput(token) {
                                var travel = 0f
                                detectDragGestures(onDragStart = { travel = 0f }) { change, amount ->
                                    change.consume()
                                    travel += amount.x
                                    while (travel > step) {
                                        currentStep(token, 1)
                                        travel -= step
                                    }
                                    while (travel < -step) {
                                        currentStep(token, -1)
                                        travel += step
                                    }
                                }
                            }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (slot != null) QuickSlotMark(slot = slot, size = 24)
                    Text(
                        text = slot?.label ?: quickSlotFallbackLabel(token),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Inline options under an Edit Home item. */
@Composable
private fun EditOptions(options: List<Pair<String, () -> Unit>>) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 4.dp)) {
        options.forEach { (label, action) ->
            Text(
                text = label,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .sableFocusRing()
                        .clickable(onClick = action)
                        .padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HomeToggleRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = "$title, " + if (selected) "on" else "off"
                }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (selected) "✓" else "",
            fontSize = 20.sp,
            color = accent,
            modifier = Modifier.width(28.dp),
        )
    }
}

/** Role slots first (Phone, Camera, Hub, All apps), then every other personal app A to Z. */
private fun quickBarChoices(
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
    current: List<String>,
): List<QuickSlot> {
    val roles =
        listOf(QuickBar.PHONE, QuickBar.CAMERA, QuickBar.HUB, QuickBar.ALL_APPS)
            .filterNot { it in current }
            .mapNotNull { resolveQuickSlot(it, apps, appByKey) }
    val taken =
        current.mapNotNull { resolveQuickSlot(it, apps, appByKey)?.app?.stableKey() }.toSet() +
            roles.mapNotNull { it.app?.stableKey() }
    val others =
        apps
            .filter { it.inLauncherUser && it.stableKey() !in taken }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .map { QuickSlot(QuickBar.appToken(it.stableKey()), it.label, it) }
    return roles + others
}

/** Null when the slot's app is not installed; the bar then skips it. */
private fun resolveQuickSlot(
    token: String,
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
): QuickSlot? {
    fun role(
        packages: List<String>,
        labels: List<String>,
    ): AppEntry? {
        val personal = apps.filter { it.inLauncherUser }
        return packages.firstNotNullOfOrNull { pkg ->
            personal.firstOrNull { CoreAppIdentity.canonicalPackage(it.component.packageName) == pkg }
        } ?: personal.firstOrNull { app -> labels.any { app.label.equals(it, ignoreCase = true) } }
    }
    return when (token) {
        QuickBar.COMMAND -> QuickSlot(token, "Search", null)
        QuickBar.ALL_APPS -> QuickSlot(token, "All apps", null)
        QuickBar.PHONE ->
            role(listOf(DIALER_PACKAGE), listOf("Phone"))?.let { QuickSlot(token, "Phone", it) }
        // Start's own Hub page: always available, whatever messaging apps are installed.
        QuickBar.HUB -> QuickSlot(token, "Hub", null)
        QuickBar.CAMERA ->
            role(CoreAppIdentity.startTileCandidates(CoreAppIdentity.CAMERA), listOf("Camera"))
                ?.let { QuickSlot(token, "Camera", it) }
        else ->
            QuickBar.appKey(token)?.let(appByKey::get)?.let { QuickSlot(token, it.label, it) }
    }
}

private fun quickSlotFallbackLabel(token: String): String =
    when (token) {
        QuickBar.PHONE -> "Phone (not installed)"
        QuickBar.HUB -> "Hub (not installed)"
        QuickBar.CAMERA -> "Camera (not installed)"
        else -> "App not installed"
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
                .sableFocusRing()
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
    focusMemory: SableFocusMemory,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onLaunchSecond: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    privateSpaceLocked: Boolean?,
    onTogglePrivateSpace: () -> Unit,
) {
    // Private Space apps live in their own section at the end, never in the A-Z list.
    val (privateApps, listedApps) = remember(apps) { apps.partition { it.profileLabel == PROFILE_PRIVATE_LABEL } }
    var filter by remember { mutableStateOf(PrivacyFilter.All) }
    val rows = remember(listedApps, filter) { buildRailRows(listedApps.filter { filter.matches(it.privacy) }) }
    val keys = remember(rows) { rows.map { it.app.stableKey() } }
    val labels = remember(rows) { rows.map { it.app.label } }
    // One alphabet-index concept: section headers, the right-hand rail and
    // keyboard letters all come from this index (no second, hidden rail).
    val alphabet = remember(labels) { SableAlphabetIndex.build(labels) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val typeToJump = remember { SableTypeToJump() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedIndex by remember { mutableStateOf(-1) }
    var expandedKeys by remember { mutableStateOf(emptySet<String>()) }
    var restoreDone by remember { mutableStateOf(false) }

    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun focusIndex(index: Int) {
        val key = keys.getOrNull(index) ?: return
        scope.launch {
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { requesterFor(key).requestFocus() }
        }
    }

    // FOCUS_RESTORATION: back from Peek / App info / Search lands on the same app
    // (or the app now at its old position if it was removed). Runs once per visit
    // so inventory refreshes never move focus under the user.
    LaunchedEffect(keys.isNotEmpty()) {
        if (!restoreDone && keys.isNotEmpty()) {
            restoreDone = true
            focusMemory.restore(FOCUS_SURFACE_ALL_APPS, keys)?.let(::focusIndex)
        }
    }

    fun openActions(index: Int) {
        val row = rows.getOrNull(index) ?: return
        focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
        onOpenPeek(row.app)
    }

    fun onRowKey(
        index: Int,
        event: KeyEvent,
    ): Boolean {
        val native = event.nativeKeyEvent
        val decision =
            AllAppsKeyPolicy.decide(
                keyCode = native.keyCode,
                unicode = native.unicodeChar,
                metaState = native.metaState,
                repeat = native.repeatCount > 0,
            )
        if (!decision.consumed) return false
        // Act on key down only; swallow the matching key up so clickable does not fire twice.
        if (event.type != KeyEventType.KeyDown) return true
        val row = rows.getOrNull(index) ?: return true
        when (decision.action) {
            AllAppsKeyAction.Open -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onLaunchApp(row.app)
            }
            AllAppsKeyAction.OpenSecond -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onLaunchSecond(row.app)
            }
            AllAppsKeyAction.ToggleDetail -> {
                val key = keys[index]
                expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
            }
            AllAppsKeyAction.Actions -> openActions(index)
            AllAppsKeyAction.AppInfo -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onOpenAppInfo(row.app)
            }
            AllAppsKeyAction.Search -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onSearch()
            }
            AllAppsKeyAction.TypeToJump -> {
                val target =
                    typeToJump.onChar(decision.char, SystemClock.uptimeMillis(), labels, index)
                        ?: alphabet.indexForChar(decision.char)
                if (target != null) focusIndex(target)
            }
            AllAppsKeyAction.IgnoreRepeat, AllAppsKeyAction.PassThrough -> Unit
        }
        return true
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
                    .padding(start = 20.dp, end = if (alphabet.isEmpty) 20.dp else SableAlphabetRailWidth + 4.dp),
        ) {
            MetroHeader(
                title = "all apps",
                onBack = onBack,
            )
            Spacer(Modifier.height(8.dp))
            SearchLaunchButton(onClick = onSearch)
            Spacer(Modifier.height(6.dp))
            PrivacyFilterChips(selected = filter, onSelect = { filter = it })
            Spacer(Modifier.height(8.dp))

            if (rows.isEmpty() && filter != PrivacyFilter.All) {
                EmptyState("No apps match “${filter.label}”.")
            } else if (rows.isEmpty() && privateSpaceLocked == null) {
                EmptyState("No launchable apps are available.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.app.stableKey() },
                    ) { index, row ->
                        if (row.startsSection) {
                            Text(
                                text = row.section,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .padding(top = 12.dp, bottom = 4.dp)
                                        .semantics { heading() },
                            )
                        }
                        val key = keys[index]
                        AppRow(
                            app = row.app,
                            expanded = key in expandedKeys,
                            onClick = {
                                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, key, index)
                                onLaunchApp(row.app)
                            },
                            onContext = { openActions(index) },
                            modifier =
                                Modifier
                                    .focusRequester(requesterFor(key))
                                    .onFocusChanged { state ->
                                        if (state.hasFocus) {
                                            focusedIndex = index
                                            focusMemory.remember(FOCUS_SURFACE_ALL_APPS, key, index)
                                        }
                                    }.onPreviewKeyEvent { event -> onRowKey(index, event) },
                        )
                    }
                    if (privateSpaceLocked != null) {
                        item(key = "private-space") {
                            PrivateSpaceSection(
                                locked = privateSpaceLocked,
                                apps = privateApps,
                                onToggle = onTogglePrivateSpace,
                                onLaunchApp = onLaunchApp,
                                onOpenPeek = onOpenPeek,
                            )
                        }
                    }
                }
            }
        }

        if (!alphabet.isEmpty) {
            val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
            SableAlphabetRail(
                index = alphabet,
                activeKey = alphabet.sectionKeyAt(if (focusedIndex >= 0) focusedIndex else firstVisible),
                onJump = { target ->
                    scope.launch { listState.animateScrollToItem(target) }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * All Apps privacy filters (Sensitive · All · No sensitive): Left/Right move
 * between the chips, Enter applies; the applied chip is filled.
 */
@Composable
private fun PrivacyFilterChips(
    selected: PrivacyFilter,
    onSelect: (PrivacyFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrivacyFilter.entries.forEach { chip ->
            val on = chip == selected
            Text(
                text = chip.label,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .sableFocusRing()
                        .clickable { onSelect(chip) }
                        .semantics { contentDescription = "Filter ${chip.label}" + if (on) ", applied" else "" }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Text(
        text = "←→ filters · Enter applies",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Private Space at the end of All Apps: one row that locks or unlocks it
 * (the system asks for its credential), and its apps only while unlocked.
 */
@Composable
private fun PrivateSpaceSection(
    locked: Boolean,
    apps: List<AppEntry>,
    onToggle: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            text = "private space",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .sableFocusRing()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (locked) "🔒" else "🔓",
                modifier = Modifier.width(38.dp),
                fontSize = 20.sp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (locked) "Unlock private space" else "Lock private space",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text =
                        if (locked) {
                            "Its apps stay hidden and stopped until you unlock"
                        } else {
                            "${apps.size} apps · locking stops them and hides them again"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!locked) {
            apps.forEach { app ->
                AppRow(
                    app = app,
                    onClick = { onLaunchApp(app) },
                    onContext = { onOpenPeek(app) },
                )
            }
        }
    }
}

/**
 * Command / Search (sable-command-titan2). One field; a Best match row on top;
 * then grouped results (apps, permission matches, people, settings, Hub,
 * actions). Up/Down move through one result list, Left/Right on a result (or
 * at either end of the query) move between the group chips, Enter opens,
 * Shift+Enter opens an app's Dual copy, Menu or Alt+Enter shows the result's
 * actions, Back closes. `/private`, `/dual` and `/work` list one profile's
 * apps; a locked Private Space shows the locked state with Unlock.
 */
@Composable
private fun SearchScreen(
    apps: List<AppEntry>,
    recentKeys: List<String>,
    initialQuery: String,
    privateSpaceLocked: Boolean?,
    onTogglePrivateSpace: () -> Unit,
    onBack: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    secondInstanceOf: (AppEntry) -> AppEntry?,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onAppearance: () -> Unit,
    onFrames: () -> Unit,
    onGrid: () -> Unit,
    onHub: (HubFilter) -> Unit,
    onEditHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
) {
    // Seeded by type-to-launch; the cursor sits after the seed so typing continues.
    var query by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    var chip by remember { mutableStateOf<CommandScope?>(null) }
    // The result whose actions are open ("app:<key>" or "cmd:<title>").
    var actionsFor by remember { mutableStateOf<String?>(null) }
    val typed = CommandQuery.parse(query.text)
    val parsed = CommandGroups.apply(typed, chip)
    val term = parsed.term
    val locale = Locale.getDefault()

    fun moveChip(delta: Int) {
        chip = CommandGroups.next(chip ?: parsed.scope, delta)
        actionsFor = null
    }

    val profileApps =
        when (parsed.scope) {
            CommandScope.Private -> apps.filter { it.profileLabel == AppProfiles.PRIVATE }
            CommandScope.Dual -> apps.filter { it.profileLabel == AppProfiles.DUAL }
            CommandScope.Work -> apps.filter { it.profileLabel == AppProfiles.WORK }
            else -> null
        }
    val (filteredApps, privacyMatches) =
        remember(apps, recentKeys, parsed) {
            if (profileApps != null) {
                CommandRanking.apps(profileApps, { it.label }, { null }, term) to emptyList()
            } else if (!parsed.includes(CommandScope.Apps)) {
                emptyList<AppEntry>() to emptyList()
            } else {
                // Recents first, label prefix before contains; apps whose effective
                // access matches privacy words ("camera", "mic") are their own group.
                val byName =
                    CommandRanking.apps(
                        items = apps,
                        label = { it.label },
                        recentRank = { app -> recentKeys.indexOf(app.stableKey()).takeIf { it >= 0 } },
                        term = term,
                    ).take(if (term.isEmpty()) 6 else 12)
                val byPrivacy =
                    if (term.isEmpty()) {
                        emptyList()
                    } else {
                        apps.filter { app ->
                            app !in byName && PrivacySummaryPolicy.matchesPrivacyQuery(app.privacy, term)
                        }.take(8)
                    }
                byName to byPrivacy
            }
        }

    val commands =
        listOf(
            SearchCommand("All apps", "A to Z with privacy summaries", "A", "Start", onAllApps),
            SearchCommand("Active Frames", "Open apps, Home", "▦", "Start", onFrames),
            SearchCommand("App grid", "Every app, in your order", "▤", "Start", onGrid),
            SearchCommand("Hub page", "Messages, mail, calls, contacts and notifications", "◇", "Start", { onHub(HubFilter.All) }),
            SearchCommand("Edit Home", "Order the app grid, the bar and the empty frames", "▭", "Start", onEditHome),
            SearchCommand("Appearance", "Theme and Sable accent", "◐", "Start", onAppearance),
            SearchCommand("System Settings", "Open Android settings", "⚙", "Settings", onOpenSettings),
        )
    val filteredCommands =
        if (!parsed.includes(CommandScope.Actions) || parsed.profileScope) {
            emptyList()
        } else {
            commands.filter { command ->
                term.isEmpty() ||
                    command.title.lowercase(locale).contains(term.lowercase(locale)) ||
                    command.subtitle.lowercase(locale).contains(term.lowercase(locale))
            }
        }
    val settingsResults = if (parsed.profileScope) emptyList() else settingsResults(parsed, onStartIntent, onOpenSettings)
    val peopleResults = if (parsed.profileScope) emptyList() else peopleResults(parsed, onStartIntent, onHub)
    val hubResults = if (parsed.profileScope) emptyList() else hubResults(parsed, onHub)
    val firstOther =
        (filteredCommands.takeIf { term.isNotEmpty() }.orEmpty() + settingsResults + peopleResults + hubResults)
            .firstOrNull()
    val bestIsApp = CommandBestMatch.appWins(filteredApps.firstOrNull()?.label, firstOther?.title, term)
    val bestApp = filteredApps.firstOrNull()?.takeIf { bestIsApp == true }
    val bestOther = firstOther?.takeIf { bestIsApp == false }
    val lockedPrivate = privateSpaceLocked == true && (parsed.scope == CommandScope.Private || term.isNotEmpty())

    fun appActions(app: AppEntry): List<Pair<String, () -> Unit>> =
        buildList {
            add("open" to { onLaunchApp(app) })
            secondInstanceOf(app)?.let { other ->
                add((if (other.profileLabel == AppProfiles.DUAL) "open Dual copy" else "open personal app") to { onLaunchApp(other) })
            }
            add("app info" to { onOpenAppInfo(app) })
            add("more actions" to { onOpenPeek(app) })
        }

    @Composable
    fun AppResult(app: AppEntry) {
        val key = "app:" + app.stableKey()
        AppRow(
            app = app,
            onClick = { onLaunchApp(app) },
            onContext = { actionsFor = if (actionsFor == key) null else key },
            modifier =
                Modifier.onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val decision = AllAppsKeyPolicy.decide(native.keyCode, 0, native.metaState, native.repeatCount > 0)
                    val action: (() -> Unit)? =
                        when (decision.action) {
                            AllAppsKeyAction.OpenSecond -> {
                                { onLaunchApp(secondInstanceOf(app) ?: app) }
                            }
                            AllAppsKeyAction.Actions -> {
                                { actionsFor = if (actionsFor == key) null else key }
                            }
                            else -> null
                        }
                    if (action != null && event.type == KeyEventType.KeyDown) action()
                    action != null
                },
        )
        if (actionsFor == key) EditOptions(appActions(app))
    }

    @Composable
    fun CommandResult(command: SearchCommand) {
        val key = "cmd:" + command.source + ":" + command.title
        SearchCommandRow(
            command = command,
            onActions = { actionsFor = if (actionsFor == key) null else key },
        )
        if (actionsFor == key) EditOptions(command.actions.ifEmpty { listOf("open" to command.onClick) })
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
            onValueChange = {
                query = it
                actionsFor = null
            },
            // Enter opens the best match: else the first app, else the first other result.
            onSubmit = {
                bestApp?.let(onLaunchApp)
                    ?: bestOther?.onClick?.invoke()
                    ?: filteredApps.firstOrNull()?.let(onLaunchApp)
                    ?: firstOther?.onClick?.invoke()
            },
            onGroupDelta = ::moveChip,
        )
        Spacer(Modifier.height(8.dp))
        CommandChips(selected = if (parsed.profileScope) null else parsed.scope, onSelect = { chip = it })
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        // ←→ on a result moves between the groups; ↑↓ stay one list.
                        val native = event.nativeKeyEvent
                        val delta = AppGrid.arrowDelta(native.keyCode)?.takeIf { it == -1 || it == 1 }
                        if (delta == null || native.metaState != 0) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown) moveChip(delta)
                        true
                    },
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (lockedPrivate) {
                item(key = "locked") {
                    PrivateLockedCard(onUnlock = onTogglePrivateSpace, full = parsed.scope == CommandScope.Private)
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (bestApp != null || bestOther != null) {
                item(key = "best") {
                    SectionLabel("best match")
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp),
                    ) {
                        Column {
                            if (bestApp != null) AppResult(bestApp) else bestOther?.let { CommandResult(it) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            val apps1 = filteredApps.filterNot { it == bestApp }
            if (apps1.isNotEmpty()) {
                item { SectionLabel(if (profileApps != null) CommandGroups.label(parsed.scope).lowercase(locale) + " apps" else if (term.isEmpty()) "recent + apps" else "apps") }
                items(apps1, key = { "a:" + it.stableKey() }) { app -> AppResult(app) }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (privacyMatches.isNotEmpty()) {
                item { SectionLabel("permission matches") }
                items(privacyMatches, key = { "p:" + it.stableKey() }) { app -> AppResult(app) }
                item { Spacer(Modifier.height(12.dp)) }
            }
            listOf(
                "people" to peopleResults,
                "settings" to settingsResults,
                "hub" to hubResults,
                (if (term.isEmpty()) "quick actions" else "actions") to filteredCommands,
            ).forEach { (label, rows) ->
                val shown = rows.filterNot { it == bestOther }
                if (shown.isNotEmpty()) {
                    item { SectionLabel(label) }
                    items(shown, key = { "$label:" + it.source + it.title }) { command -> CommandResult(command) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
            if (filteredApps.isEmpty() && privacyMatches.isEmpty() && filteredCommands.isEmpty() && settingsResults.isEmpty() &&
                peopleResults.isEmpty() && hubResults.isEmpty() && !lockedPrivate
            ) {
                item {
                    EmptyState(
                        "No local results match “$term”. Prefixes: ?app @person /settings /hub !action /private /dual /work",
                    )
                }
            }
            item {
                Text(
                    text = "↑↓ results · ←→ groups · Enter opens · Shift+Enter Dual · Menu actions · Back closes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The group chips over the results; the current scope is filled. */
@Composable
private fun CommandChips(
    selected: CommandScope?,
    onSelect: (CommandScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CommandGroups.CHIPS.forEach { scope ->
            val on = scope == selected
            Text(
                text = CommandGroups.label(scope),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .clickable { onSelect(scope) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Command's locked state (sable-command-titan2 "4. Locked / Private"): the
 * Private Space is locked, so its apps are neither listed nor searched.
 */
@Composable
private fun PrivateLockedCard(
    onUnlock: () -> Unit,
    full: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(12.dp),
        horizontalAlignment = if (full) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (full) Text("🔒", fontSize = 28.sp)
        Text(
            text = if (full) "Content hidden" else "Private space locked",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Unlock to show and search private space apps. Locked mode shows nothing from it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Unlock",
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .sableFocusRing()
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun settingsResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
    onOpenSettings: () -> Unit,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Settings)) return emptyList()
    val limit = if (parsed.scope == CommandScope.Settings) 12 else 4
    return CommandRanking.settings(parsed.term, limit = limit).map { dest ->
        val open = { onStartIntent(Intent(dest.action)) }
        SearchCommand(
            title = dest.title,
            subtitle = "Open ${dest.title} settings",
            mark = "⚙",
            source = "Settings",
            onClick = open,
            actions = listOf("open ${dest.title}" to open, "open Settings home" to onOpenSettings),
        )
    }
}

/**
 * People: dial a typed number, or hand the name to Contacts' own search
 * (Start holds no contacts permission and keeps no copy).
 */
private fun peopleResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
    onHub: (HubFilter) -> Unit,
): List<SearchCommand> {
    val term = parsed.term
    if (term.isEmpty() || !parsed.includes(CommandScope.People)) return emptyList()
    val dial =
        CommandQuery.dialable(term)?.let { number ->
            val call = { onStartIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            SearchCommand(
                title = "Call $number",
                subtitle = "Opens the dialer with the number",
                mark = "☎",
                source = "Phone",
                onClick = call,
                actions =
                    listOf(
                        "call" to call,
                        "send a message" to { onStartIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) },
                        "add to contacts" to {
                            onStartIntent(
                                Intent(Intent.ACTION_INSERT)
                                    .setType("vnd.android.cursor.dir/contact")
                                    .putExtra("phone", number),
                            )
                        },
                    ),
            )
        }
    val find = {
        onStartIntent(
            Intent(Intent.ACTION_SEARCH)
                .setPackage(CONTACTS_PACKAGE)
                .putExtra(SearchManager.QUERY, term),
        )
    }
    val contacts =
        SearchCommand(
            title = "Find “$term” in Contacts",
            subtitle = "Search people by name, number or email",
            mark = "@",
            source = "Contacts",
            onClick = find,
            actions = listOf("find in Contacts" to find, "Hub page · Contacts" to { onHub(HubFilter.Contacts) }),
        ).takeIf { parsed.scope == CommandScope.People || term.length >= 2 }
    return listOfNotNull(dial, contacts)
}

/**
 * Hub results open Start's own Hub page on a tab (Start never shows message, mail or call
 * content). Shown for "/hub", or when the term names Hub or one of its tabs.
 */
private fun hubResults(
    parsed: CommandQuery,
    onHub: (HubFilter) -> Unit,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Hub)) return emptyList()
    val term = parsed.term.lowercase(Locale.ROOT)
    val tab = HubFilter.entries.firstOrNull { term.isNotEmpty() && it.label.lowercase(Locale.ROOT).startsWith(term) }
    val named = term.isNotEmpty() && ("hub".startsWith(term) || tab != null)
    if (parsed.scope != CommandScope.Hub && !named) return emptyList()
    val target = tab ?: HubFilter.All
    return listOf(
        SearchCommand(
            title = if (target == HubFilter.All) "Open Hub" else "Hub · ${target.label}",
            subtitle = "Messages, calls, contacts and notifications",
            mark = "◉",
            source = "Hub",
            onClick = { onHub(target) },
            actions = HubFilter.entries.map { filter -> "Hub · ${filter.label}" to { onHub(filter) } },
        ),
    )
}

/** Hub's own settings: connected apps, private mode, history (org.sableos.hub). */
private fun hubSettingsIntent(): Intent = Intent(HUB_ACTION_CONNECTED_APPS).setPackage(HUB_PACKAGE)

@Composable
private fun SableSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onGroupDelta: (Int) -> Unit,
) {
    // The field takes focus on arrival so the keyboard types straight into it.
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }
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
                        .focusRequester(fieldFocus)
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            val selection = value.selection
                            when {
                                event.type != KeyEventType.KeyDown -> false
                                // Holding Backspace clears the whole query.
                                native.keyCode == KEYCODE_DEL && native.repeatCount > 0 -> {
                                    if (value.text.isNotEmpty()) onValueChange(TextFieldValue(""))
                                    true
                                }
                                native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && native.repeatCount == 0 -> {
                                    onSubmit()
                                    true
                                }
                                // Left at the start or Right at the end of the query: previous / next group.
                                native.metaState == 0 && selection.collapsed &&
                                    native.keyCode == KEYCODE_DPAD_LEFT && selection.start == 0 -> {
                                    onGroupDelta(-1)
                                    true
                                }
                                native.metaState == 0 && selection.collapsed &&
                                    native.keyCode == KEYCODE_DPAD_RIGHT && selection.end == value.text.length -> {
                                    onGroupDelta(1)
                                    true
                                }
                                else -> false
                            }
                        }.padding(vertical = 8.dp),
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
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
    onActions: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .sableFocusRing()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
                    val menu = native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU || (native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt)
                    if (menu && event.type == KeyEventType.KeyDown && native.repeatCount == 0) onActions()
                    menu
                }.combinedClickable(onClick = command.onClick, onLongClick = onActions)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = command.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = command.source,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    onClick: () -> Unit,
    onContext: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    modifier
                        .weight(1f)
                        .heightIn(min = 58.dp)
                        .sableFocusRing()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onContext,
                        ).padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppMark(app = app, size = 42)
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.label,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        app.profileLabel?.let { profile ->
                            Spacer(Modifier.width(6.dp))
                            ProfileBadge(profile)
                        }
                    }
                    PrivacySummaryLine(app)
                }
            }

            // Actions are also reachable with Menu / Fn+Enter; never touch-only.
            TextButton(
                onClick = onContext,
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Actions for ${app.label}" },
            ) {
                Text(
                    text = "›",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            PrivacyDetail(
                app = app,
                modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
            )
        }
    }
}

/** WORK_PROFILE_BADGE=REQUIRED: visible text plus a spoken label, beside the app name. */
@Composable
private fun ProfileBadge(profile: String) {
    Text(
        text = AppProfiles.badge(profile),
        modifier =
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .semantics { contentDescription = "${AppProfiles.badge(profile)} profile" },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * The one-line privacy/security summary. Whole labels fold into "+N" until the
 * line fits the row width at the current font scale (no mid-word clipping);
 * special access shows a warning mark that always carries a spoken label.
 * Reads only the precomputed snapshot on [AppEntry]; no platform query here.
 */
@Composable
private fun PrivacySummaryLine(app: AppEntry) {
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val badgePx = with(density) { PrivacyBadgeWidth.roundToPx() }
        val row =
            remember(app.privacy, maxWidthPx, style, density.fontScale) {
                val probe = PrivacySummaryPolicy.compact(app.privacy)
                val available = if (probe.badgeLabels.isNotEmpty()) maxWidthPx - badgePx else maxWidthPx
                PrivacySummaryPolicy.compact(app.privacy) { text ->
                    measurer.measure(text, style, maxLines = 1).size.width <= available
                }
            }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.badgeLabels.isNotEmpty()) {
                Text(
                    text = "⚠",
                    modifier =
                        Modifier
                            .width(PrivacyBadgeWidth)
                            .semantics {
                                contentDescription = "Special access: " + row.badgeLabels.joinToString(", ")
                            },
                    style = style,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = row.text,
                modifier = Modifier.semantics { contentDescription = row.accessibleText(app.privacy) },
                style = style,
                color =
                    if (row.warning && row.badgeLabels.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Expanded privacy detail (Space in All Apps, and in Peek). */
@Composable
private fun PrivacyDetail(
    app: AppEntry,
    modifier: Modifier = Modifier,
) {
    val lines = PrivacySummaryPolicy.detail(app.privacy)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = PrivacySummaryPolicy.compact(app.privacy).text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lines.forEach { line ->
            Text(
                text = if (line.special) "special access · ${line.label}" else line.label,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (line.special) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
        Text(
            text = PrivacySummaryPolicy.DETAIL_FOOTER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/**
 * An app's icon in Start's one style (Q4 device review, Q7): the app's Sable
 * glyph when it has one ([AppGlyphs], by package only), else its own themed
 * monochrome layer shaded like the glyphs, else its own icon at the same size.
 * Never a tile behind it.
 */
@Composable
internal fun AppMark(
    app: AppEntry,
    size: Int,
) {
    val glyph = AppGlyphs.forPackage(app.component.packageName)
    if (glyph != null) {
        SableGlyphIcon(glyph = glyph, modifier = Modifier.size(size.dp))
        return
    }

    val context = LocalContext.current
    val densityDpi = context.resources.displayMetrics.densityDpi
    val iconKey = "${app.stableKey()}@$size@$densityDpi"
    // Loaded off the main thread and cached for the process: binding a row never
    // waits on LauncherApps (ROW_BIND_BLOCKING_QUERY=NO).
    val icon by
        produceState<LauncherIcon?>(initialValue = LauncherIconCache.get(iconKey), iconKey) {
            if (value == null) {
                value =
                    withContext(Dispatchers.IO) {
                        loadLauncherIcon(context = context, app = app, sizeDp = size)
                    }?.also { LauncherIconCache.put(iconKey, it) }
            }
        }

    val loadedIcon = icon
    when {
        loadedIcon == null ->
            // Until the icon loads: the first letter in the glyph colours, no tile.
            Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = appMark(app),
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * 0.5f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        loadedIcon.monochrome ->
            SableMonochromeIcon(
                mask = loadedIcon.bitmap.asImageBitmap(),
                accent = appColor(app),
                modifier = Modifier.size(size.dp),
            )
        else ->
            Image(
                bitmap = loadedIcon.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Fit,
            )
    }
}

/** A loaded launcher icon: the app's themed monochrome layer (a mask) or its full icon. */
private class LauncherIcon(
    val bitmap: Bitmap,
    val monochrome: Boolean,
)

private object LauncherIconCache : LruCache<String, LauncherIcon>(LAUNCHER_ICON_CACHE_ENTRIES)

/**
 * The app's icon at [sizeDp]. An adaptive icon with a monochrome layer
 * (Android 13 themed icons) gives that layer, its 66dp safe zone scaled to
 * the glyph box so it sits at the glyphs' size; any other icon is drawn whole,
 * inset like the glyphs' own margin. Work and clone badges come from the
 * row's profile label, not from the icon.
 */
private fun loadLauncherIcon(
    context: Context,
    app: AppEntry,
    sizeDp: Int,
): LauncherIcon? =
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
        val bitmap =
            Bitmap.createBitmap(
                iconSizePx,
                iconSizePx,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        val drawable = activityInfo.getIcon(densityDpi)
        val monochrome =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
                drawable.monochrome
            } else {
                null
            }
        if (monochrome != null) {
            // The layer spans the 108dp adaptive canvas; its 66dp safe zone fills the icon box.
            val canvasPx = (iconSizePx * ADAPTIVE_CANVAS_DP / ADAPTIVE_SAFE_ZONE_DP).toInt()
            val offset = (iconSizePx - canvasPx) / 2
            monochrome.setBounds(offset, offset, offset + canvasPx, offset + canvasPx)
            monochrome.draw(canvas)
            return@runCatching LauncherIcon(bitmap, monochrome = true)
        }
        val insetPx = (iconSizePx * OWN_ICON_INSET).toInt()
        drawable.setBounds(
            insetPx,
            insetPx,
            iconSizePx - insetPx,
            iconSizePx - insetPx,
        )
        drawable.draw(canvas)
        LauncherIcon(bitmap, monochrome = false)
    }.getOrNull()

@Composable
private fun SablePeekScreen(
    app: AppEntry,
    inBaseBar: Boolean?,
    hasSecondInstance: Boolean,
    onToggleBaseBar: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onOpenSecond: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onUninstall: () -> Unit,
) {
    val actions =
        remember(app, inBaseBar, hasSecondInstance) {
            AppActionPolicy.actions(
                AppActionContext(
                    pinnedToStart = false,
                    // null when the app is in another profile; a full bar opens the slot picker.
                    inBaseBar = inBaseBar,
                    launcherUser = app.inLauncherUser,
                    canUninstall = app.canUninstall && app.inLauncherUser,
                    // The BB10 app grid holds every app: nothing to pin.
                    pinnable = false,
                    hasSecondInstance = hasSecondInstance,
                ),
            )
        }
    // UNINSTALL_ONE_KEY=NO: a destructive action first arms, then needs an explicit confirm.
    var pendingConfirm by remember(app) { mutableStateOf<AppAction?>(null) }
    val openFocus = remember { FocusRequester() }
    LaunchedEffect(app) {
        withFrameNanos { }
        runCatching { openFocus.requestFocus() }
    }

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
                    .sableFocusRing()
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
                    .verticalScroll(rememberScrollState())
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "privacy & security",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        app.profileLabel?.let { profile ->
                            Spacer(Modifier.width(6.dp))
                            ProfileBadge(profile)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrivacyDetail(app = app)

            Spacer(Modifier.height(18.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .focusRequester(openFocus)
                        .sableFocusRing()
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

            actions.filterNot { it == AppAction.Open }.forEach { action ->
                val confirming = pendingConfirm == action
                ContextAction(
                    mark =
                        when (action) {
                            AppAction.PinToStart, AppAction.AddToBaseBar -> "+"
                            AppAction.RemoveFromStart, AppAction.RemoveFromBaseBar -> "−"
                            AppAction.AppInfo -> "ⓘ"
                            AppAction.NotificationSettings -> "◔"
                            AppAction.Uninstall, AppAction.Disable -> "⌫"
                            AppAction.Open, AppAction.OpenSecond -> "→"
                        },
                    label = if (confirming) AppActionPolicy.confirmationLabel(action) else action.label,
                    onClick = {
                        if (AppActionPolicy.requiresConfirmation(action) && !confirming) {
                            pendingConfirm = action
                        } else {
                            pendingConfirm = null
                            when (action) {
                                AppAction.Open -> onOpen()
                                AppAction.OpenSecond -> onOpenSecond()
                                AppAction.AppInfo -> onOpenAppInfo()
                                AppAction.NotificationSettings -> onOpenNotificationSettings()
                                AppAction.PinToStart, AppAction.RemoveFromStart -> Unit
                                AppAction.Uninstall -> onUninstall()
                                AppAction.AddToBaseBar, AppAction.RemoveFromBaseBar -> onToggleBaseBar()
                                AppAction.Disable -> Unit
                            }
                        }
                    },
                )
                if (confirming) {
                    ContextAction(
                        mark = "×",
                        label = "cancel",
                        onClick = { pendingConfirm = null },
                    )
                }
            }

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
                        .sableFocusRing()
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
                .sableFocusRing()
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
                .sableFocusRing()
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

private fun findLiveApp(
    apps: List<AppEntry>,
    packageName: String,
): AppEntry? =
    apps.firstOrNull { CoreAppIdentity.canonicalPackage(it.component.packageName) == packageName }
        ?: when (packageName) {
            WEATHER_PACKAGE ->
                apps.firstOrNull { it.label.equals(WEATHER_LABEL, ignoreCase = true) }
            MAIL_PACKAGE ->
                apps.firstOrNull { it.label.equals(MAIL_LABEL, ignoreCase = true) }
            else -> null
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
                .sableFocusRing()
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
                .sableFocusRing()
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
        val section = SableAlphabetIndex.keyFor(app.label, locale)
        AppRailRow(
            app = app,
            section = section,
            startsSection =
                index == 0 ||
                    SableAlphabetIndex.keyFor(sorted[index - 1].label, locale) != section,
        )
    }
}

/**
 * Home apps before the user orders them: the screen design's list (Messages,
 * Mail, Calendar, Contacts, Phone, Camera), each the first installed app of
 * its group in the launcher's own profile.
 */
private fun defaultHomeApps(apps: List<AppEntry>): List<AppEntry> {
    val personal = apps.filter { it.inLauncherUser }
    return HOME_APP_DEFAULT_GROUPS.mapNotNull { group ->
        group.packageNames.firstNotNullOfOrNull { packageName ->
            personal.firstOrNull { CoreAppIdentity.canonicalPackage(it.component.packageName) == packageName }
        } ?: personal.firstOrNull { app -> group.labels.any { app.label.equals(it, ignoreCase = true) } }
    }.distinctBy { it.stableKey() }
}

private fun appMark(app: AppEntry): String =
    app.label
        .trim()
        .take(1)
        .uppercase()
        .ifEmpty { "•" }

/** The app's accent: its glyph's ([AppGlyphs], by package), else a stable pick from the Sable palette. */
private fun appColor(app: AppEntry): Color {
    AppGlyphs.forPackage(app.component.packageName)?.let { return Color(it.accent) }
    val index =
        Math.floorMod(
            app.stableKey().hashCode(),
            SableTilePalette.size,
        )
    return SableTilePalette[index]
}

/** The live line for an app's Active Frame, by package only (a "Calendar" label is not Sable Calendar). */
private fun liveDatumForApp(
    app: AppEntry,
    snapshot: LiveSurfaceSnapshot,
): LiveDatum? =
    when (CoreAppIdentity.canonicalPackage(app.component.packageName)) {
        CALENDAR_PACKAGE -> snapshot.calendar
        HUB_PACKAGE, MESSAGES_PACKAGE -> snapshot.messages
        MAIL_PACKAGE -> snapshot.mail
        WEATHER_PACKAGE -> snapshot.weather
        MEDIA_PACKAGE -> snapshot.music
        CoreAppIdentity.PHOTOS -> snapshot.photos
        else -> null
    }

private data class PreferredStartAppGroup(
    /** Canonical packages for this tile, best first. */
    val packageNames: List<String> = emptyList(),
    val labels: List<String>,
)

private val HOME_APP_DEFAULT_GROUPS =
    listOf(
        PreferredStartAppGroup(
            packageNames = listOf(MESSAGES_PACKAGE, HUB_PACKAGE),
            labels = listOf(MESSAGES_LABEL, "Messages", HUB_LABEL, "Hub"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(MAIL_PACKAGE),
            labels = listOf(MAIL_LABEL, "Mail"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(CALENDAR_PACKAGE),
            labels = listOf("Sable Calendar", "Calendar"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(CONTACTS_PACKAGE),
            labels = listOf("Contacts"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(DIALER_PACKAGE),
            labels = listOf("Phone"),
        ),
        PreferredStartAppGroup(
            packageNames = CoreAppIdentity.startTileCandidates(CoreAppIdentity.CAMERA),
            labels = listOf("Camera"),
        ),
    )

private const val MAX_SESSION_RECENTS = 8
private const val KEYCODE_DEL = 67
private const val HUB_PACKAGE = "org.sableos.hub"
private const val MESSAGES_PACKAGE = HubBar.MESSAGES_PACKAGE
private const val HUB_ACTION_CONNECTED_APPS = "org.sableos.hub.action.CONNECTED_APPS"
private const val ACTION_NOTIFICATION_HISTORY = "android.settings.NOTIFICATION_HISTORY"
private const val HUB_BAR_ICON_DP = 20
private val HUB_BAR_ITEM_HEIGHT = 32.dp

/** Alt, Sym, Fn, Ctrl and Meta: with these held a letter is not a Contacts-tab jump. */
private const val HUB_JUMP_BLOCKING_META = 0x02 or 0x04 or 0x08 or 0x1000 or 0x10000
private const val MAIL_PACKAGE = "org.sableos.mail"
private const val CALENDAR_PACKAGE = "org.sableos.calendar"
private const val WEATHER_PACKAGE = "org.sableos.weather"
private const val MEDIA_PACKAGE = "org.sableos.media"
private const val DIALER_PACKAGE = "com.android.dialer"
private const val GALLERY_PACKAGE = "com.android.gallery3d"
private const val HUB_LABEL = "Sable Hub"
private const val WEATHER_LABEL = "Sable Weather"
private const val MAIL_LABEL = "Sable Mail"
private const val MESSAGES_LABEL = "Sable Messages"
private const val CONTACTS_PACKAGE = "com.android.contacts"
private const val KEYCODE_DPAD_LEFT = 21
private const val KEYCODE_DPAD_RIGHT = 22
private const val PERCENT = 100
private val EditRowStep = 48.dp

// Matches LauncherAppsRepository.PROFILE_PRIVATE.
private const val PROFILE_PRIVATE_LABEL = "private"
private const val LAUNCHER_ICON_CACHE_ENTRIES = 160
private const val ADAPTIVE_CANVAS_DP = 108f
private const val ADAPTIVE_SAFE_ZONE_DP = 66f

/** An app's own (non-glyph) icon keeps the margin a Phosphor glyph has in its 256-unit box. */
private const val OWN_ICON_INSET = 0.06f
private const val FOCUS_SURFACE_ALL_APPS = "all-apps"
private val PrivacyBadgeWidth = 18.dp

// BB10 strip metrics, designed for the Q25's 720x720 panel at density 280
// (411dp square, about 387dp tall under the status bar). The grid's rows and
// icon size, and the Active Frames card height, come from the window.
private val FRAME_PADDING = 6.dp
private val FRAME_GAP = 6.dp
private val FRAME_LABEL_HEIGHT = 28.dp
private val FRAME_CORNER = 6.dp
private const val FRAME_STRIP_ICON_DP = 18

/** Two rows of frames always fit under the dials and the weather on a 411dp square. */
private val FRAME_MIN_HEIGHT = 72.dp
private const val FRAME_ICON_DP = 40
private val STATUS_RING_SIZE = 34.dp

/** A grid cell is at least this tall: a 40dp icon, its one-line label and the focus padding. */
private const val GRID_CELL_MIN_DP = 74
private val GRID_PADDING = 4.dp
private val GRID_CELL_GAP = 2.dp
private val GRID_CELL_INNER_PADDING = 4.dp
private val GRID_ICON_LABEL_GAP = 3.dp

/** labelMedium's line height. */
private const val GRID_LABEL_LINE_DP = 16

/** Cell gap, inner padding and icon-to-label gap, top and bottom. */
private const val GRID_CELL_CHROME_DP = 2 * 2 + 4 * 2 + 3
private const val INDICATOR_HEIGHT_DP = 16
private const val INDICATOR_SLOT_DP = 18
private const val BAR_HEIGHT_DP = 48
private const val BAR_GLYPH_DP = 26

/** Before the window is measured: the status bar the configuration's height still includes. */
private const val STATUS_BAR_FALLBACK_DP = 24

/** Page indicator, bar and the grid's own padding: what the grid rows do not get. */
private const val START_CHROME_DP = INDICATOR_HEIGHT_DP + BAR_HEIGHT_DP + 2 * 4
private const val NANOS_PER_MILLI = 1_000_000L
