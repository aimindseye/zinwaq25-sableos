package org.sableos.start.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.CallLog
import android.util.LruCache
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import org.sableos.start.model.CommandQuery
import org.sableos.start.model.CommandRanking
import org.sableos.start.model.CommandScope
import org.sableos.start.model.CoreAppIdentity
import org.sableos.start.model.GlanceWidgets
import org.sableos.start.model.HomeApps
import org.sableos.start.model.HomeLayout
import org.sableos.start.model.QuickBar
import org.sableos.start.model.StartPages
import org.sableos.start.model.StartTypeToSearch
import org.sableos.start.model.inLauncherUser
import org.sableos.start.model.stableKey
import org.sableos.start.platform.LiveSurfaceRepository
import org.sableos.start.privacy.AllAppsKeyAction
import org.sableos.start.privacy.AllAppsKeyPolicy
import org.sableos.start.privacy.AppAction
import org.sableos.start.privacy.AppActionContext
import org.sableos.start.privacy.AppActionPolicy
import org.sableos.start.privacy.PrivacySummaryPolicy
import java.time.LocalTime
import java.util.Locale

private enum class SableStartDestination {
    /** The three Start pages: Glance, Home, Hub ([StartPages]). */
    Start,
    Apps,
    Search,
    Appearance,
    Peek,
    EditHome,
    SlotOptions,
}

/** Right page tabs: "All · Messages · Calls". */
private enum class HubFilter(
    val label: String,
) {
    All("All"),
    Messages("Messages"),
    Calls("Calls"),
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
)

/** What Edit Home saves with Done. */
private data class HomeEdit(
    val homeApps: List<String>,
    val quickBar: List<String>,
    val glanceWidgets: List<String>,
    val hiddenSections: Set<String>,
    val mode: String,
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

/**
 * Production Sable Start UI.
 *
 * The Titan 2 three-page model (SABLE_START_THREE_PAGE_MODEL.md and the
 * source-controlled visual artifacts/sable-start-titan2-3page.svg): Glance
 * widgets on the left, Home in the center (Weather module, the user's ordered
 * Home apps, the configurable base quick bar) and Hub on the right. All Apps,
 * Command/Search, Peek, Edit Home and Appearance open over the pages.
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
    /** Start tiles chosen before Home became an ordered list; the first Home order when [initialHomeApps] is null. */
    initialStartTileKeys: Set<String>? = null,
    initialHomeApps: List<String>? = null,
    onHomeAppsChanged: (List<String>?) -> Unit = {},
    initialQuickBar: List<String>? = null,
    onQuickBarChanged: (List<String>?) -> Unit = {},
    initialGlanceWidgets: List<String>? = null,
    onGlanceWidgetsChanged: (List<String>?) -> Unit = {},
    initialHiddenHomeSections: Set<String>? = null,
    onHiddenHomeSectionsChanged: (Set<String>?) -> Unit = {},
    initialHomeMode: String? = null,
    onHomeModeChanged: (String) -> Unit = {},
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
) {
    var destination by remember(initialScreen) {
        mutableStateOf(destinationFor(initialScreen))
    }
    var returnDestination by remember { mutableStateOf(SableStartDestination.Start) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var selectedSlot by remember { mutableStateOf<QuickSlot?>(null) }
    var savedHomeApps by remember(initialHomeApps) { mutableStateOf(initialHomeApps) }
    var recentKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    // Text typed on Home before Command/Search opened (type-to-launch).
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
    var homeMode by remember(initialHomeMode) { mutableStateOf(HomeLayout.normalizeMode(initialHomeMode)) }
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
    val defaultHomeKeys = defaultHomeApps(apps).map { it.stableKey() }
    val homeKeys = HomeApps.resolve(savedHomeApps, initialStartTileKeys, defaultHomeKeys)
    val homeApps = homeKeys.mapNotNull(appByKey::get)
    val quickSlots =
        quickBarTokens.mapNotNull { token ->
            resolveQuickSlot(token, apps, appByKey)
        }

    val startPager =
        rememberPagerState(initialPage = StartPages.forScreen(initialScreen)) { StartPages.COUNT }
    val pagerScope = rememberCoroutineScope()

    fun movePage(delta: Int) {
        val target = StartPages.target(startPager.currentPage, delta)
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

    fun setHomeApps(next: List<String>) {
        savedHomeApps = next
        onHomeAppsChanged(next)
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

    fun openHubApp(query: String = "") {
        onStartIntent(
            Intent(Intent.ACTION_MAIN)
                .setPackage(HUB_PACKAGE)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .putExtra(HUB_EXTRA_QUERY, query),
        )
    }

    fun dial(digits: String) {
        onStartIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(digits))))
    }

    fun activateSlot(slot: QuickSlot) {
        when (slot.token) {
            QuickBar.COMMAND -> openSearch(SableStartDestination.Start)
            QuickBar.ALL_APPS -> destination = SableStartDestination.Apps
            else -> slot.app?.let(::launchApp)
        }
    }

    LaunchedEffect(homeGeneration) {
        if (homeGeneration > 0) {
            destination = SableStartDestination.Start
            returnDestination = SableStartDestination.Start
            selectedApp = null
            startPager.animateScrollToPage(StartPages.HOME)
        }
    }

    LaunchedEffect(backGeneration) {
        if (backGeneration > 0) {
            when {
                // Edit Home decides: cancel the item being moved, else leave without saving.
                destination == SableStartDestination.EditHome -> editBackGeneration += 1
                destination != SableStartDestination.Start -> navigateBack()
                // Back on Glance or Hub returns to Home.
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
        if (startPager.settledPage != StartPages.HOME) onRefreshLive()
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
                        // Three pages: Glance (left), Home (center), Hub (right).
                        StartPager(
                            state = startPager,
                            onMovePage = { delta -> movePage(delta) },
                            glance = {
                                SableRefreshableSurface(
                                    isRefreshing = liveRefreshing,
                                    onRefresh = onRefreshLive,
                                ) {
                                    GlancePage(
                                        snapshot = liveSnapshot,
                                        apps = apps,
                                        widgets = glanceWidgets,
                                        refreshGeneration = refreshGeneration,
                                        active = startPager.settledPage == StartPages.GLANCE,
                                        onLaunchApp = ::launchApp,
                                        onStartIntent = onStartIntent,
                                        onRequestPermissions = onRequestLivePermissions,
                                        onEdit = { destination = SableStartDestination.EditHome },
                                    )
                                }
                            },
                            hub = {
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
                                        onOpenHub = { openHubApp() },
                                        onStartIntent = onStartIntent,
                                    )
                                }
                            },
                        ) {
                            SableRefreshableSurface(
                                isRefreshing = liveRefreshing,
                                onRefresh = onRefreshLive,
                            ) {
                                HomePage(
                                    snapshot = liveSnapshot,
                                    apps = apps,
                                    homeApps = homeApps,
                                    hiddenSections = hiddenSections,
                                    homeMode = homeMode,
                                    quickSlots = quickSlots,
                                    onLaunchApp = ::launchApp,
                                    onOpenPeek = { app ->
                                        openPeek(app, SableStartDestination.Start)
                                    },
                                    onOpenAppInfo = onOpenAppInfo,
                                    onSearch = {
                                        openSearch(SableStartDestination.Start)
                                    },
                                    onTypeToSearch = { char ->
                                        openSearch(SableStartDestination.Start, char.toString())
                                    },
                                    onEditHome = {
                                        destination = SableStartDestination.EditHome
                                    },
                                    onQuickSlot = ::activateSlot,
                                    onSlotOptions = { slot ->
                                        selectedSlot = slot
                                        destination = SableStartDestination.SlotOptions
                                    },
                                    onHubPeek = { showPage(StartPages.HUB) },
                                    onDial = { char -> dial(char.toString()) },
                                )
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
                                onHubFilter = { filter ->
                                    hubFilter = filter
                                    showPage(StartPages.HUB)
                                },
                                onOpenHub = {
                                    destination = SableStartDestination.Start
                                    openHubApp()
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

                    SableStartDestination.EditHome ->
                        EditHomeScreen(
                            apps = apps,
                            initial =
                                HomeEdit(
                                    homeApps = homeKeys.filter { it in appByKey },
                                    quickBar = quickBarTokens,
                                    glanceWidgets = glanceWidgets,
                                    hiddenSections = hiddenSections,
                                    mode = homeMode,
                                ),
                            defaults =
                                HomeEdit(
                                    homeApps = defaultHomeKeys,
                                    quickBar = QuickBar.DEFAULT,
                                    glanceWidgets = GlanceWidgets.DEFAULT,
                                    hiddenSections = HomeLayout.DEFAULT_HIDDEN,
                                    mode = HomeLayout.MODE_LIST,
                                ),
                            backGeneration = editBackGeneration,
                            onCancel = { destination = SableStartDestination.Start },
                            onDone = { edit ->
                                setHomeApps(edit.homeApps)
                                quickBarTokens = QuickBar.normalize(edit.quickBar)
                                onQuickBarChanged(if (quickBarTokens == QuickBar.DEFAULT) null else quickBarTokens)
                                glanceWidgets = edit.glanceWidgets
                                onGlanceWidgetsChanged(edit.glanceWidgets)
                                hiddenSections = edit.hiddenSections
                                onHiddenHomeSectionsChanged(edit.hiddenSections)
                                homeMode = edit.mode
                                onHomeModeChanged(edit.mode)
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
                            onBack = ::navigateBack,
                            onLaunchApp = ::launchApp,
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
                            onGlance = { showPage(StartPages.GLANCE) },
                            onHub = { showPage(StartPages.HUB) },
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
                                inBaseBar = QuickBar.appState(app.inLauncherUser, barIndex >= 0, quickBarTokens),
                                onToggleBaseBar = {
                                    val next =
                                        QuickBar.toggleApp(quickBarTokens, quickSlots.getOrNull(barIndex)?.token, key)
                                    if (next != quickBarTokens) {
                                        quickBarTokens = next
                                        onQuickBarChanged(next)
                                    }
                                },
                                isStartTile = key in homeKeys,
                                onClose = ::navigateBack,
                                onOpen = { launchApp(app) },
                                onOpenAppInfo = { onOpenAppInfo(app) },
                                onOpenNotificationSettings = { onOpenNotificationSettings(app) },
                                onUninstall = { onUninstallApp(app) },
                                onToggleStartTile = { setHomeApps(HomeApps.toggle(homeKeys, key)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Center page (visual "2. Home / Start"): the Weather module instead of a
 * logo, an optional Search or command row, the user's Home apps as a list (or
 * tiles in touch mode) and the base quick bar.
 *
 * Keys: Up/Down move focus, Enter opens, Space or Menu on an app opens its
 * actions (Peek), Alt+Enter App info, Menu elsewhere opens Edit Home,
 * Alt+1..Alt+5 (Fn+1..Fn+5) open the quick bar slots, and any other printable
 * key opens Command/Search seeded with it.
 */
@Composable
private fun HomePage(
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    homeApps: List<AppEntry>,
    hiddenSections: Set<String>,
    homeMode: String,
    quickSlots: List<QuickSlot>,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    onTypeToSearch: (Char) -> Unit,
    onEditHome: () -> Unit,
    onQuickSlot: (QuickSlot) -> Unit,
    onSlotOptions: (QuickSlot) -> Unit,
    onHubPeek: () -> Unit,
    onDial: (Char) -> Unit,
) {
    fun shown(section: String) = HomeLayout.isShown(hiddenSections, section)

    val homeFocus = remember { FocusRequester() }
    val firstAppFocus = remember { FocusRequester() }
    var phoneSlotFocused by remember { mutableStateOf(false) }
    val firstKey = homeApps.firstOrNull()?.stableKey()
    // Focus starts on the first Home app (the visual's highlighted row), else on Home itself.
    LaunchedEffect(firstKey) {
        withFrameNanos { }
        val focused = firstKey != null && runCatching { firstAppFocus.requestFocus() }.isSuccess
        if (!focused) runCatching { homeFocus.requestFocus() }
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .focusRequester(homeFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (event.type != KeyEventType.KeyDown || native.repeatCount > 0) {
                        return@onPreviewKeyEvent false
                    }
                    // Digits typed on the focused Phone slot open the dialer with them.
                    val dial = if (phoneSlotFocused) QuickBar.dialChar(native.unicodeChar) else null
                    if (dial != null) {
                        onDial(dial)
                        return@onPreviewKeyEvent true
                    }
                    val index =
                        QuickBar.shortcutSlot(
                            keyCode = native.keyCode,
                            metaState = native.metaState,
                            slotCount = quickSlots.size,
                            unicode = native.unicodeChar,
                        )
                    if (index != null) {
                        onQuickSlot(quickSlots[index])
                        return@onPreviewKeyEvent true
                    }
                    val seed =
                        StartTypeToSearch.seed(
                            unicode = native.unicodeChar,
                            metaState = native.metaState,
                            repeat = false,
                        ) ?: return@onPreviewKeyEvent false
                    onTypeToSearch(seed)
                    true
                }.onKeyEvent { event ->
                    // Menu that no app row or slot took: Edit Home.
                    val native = event.nativeKeyEvent
                    val menu = native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU
                    if (menu && event.type == KeyEventType.KeyDown) onEditHome()
                    menu
                },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Long press on an empty part of Home opens Edit Home.
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onEditHome() })
                    },
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shown(HomeLayout.WEATHER)) {
                item(key = "weather") {
                    WeatherModule(
                        datum = snapshot.weather,
                        expanded = false,
                        onOpen = { findLiveApp(apps, WEATHER_PACKAGE)?.let(onLaunchApp) },
                    )
                }
            }
            if (shown(HomeLayout.SEARCH_ROW)) {
                item(key = "search") { SearchLaunchButton(onClick = onSearch) }
            }
            if (shown(HomeLayout.STATUS_RINGS)) {
                item(key = "rings") {
                    SableStatusRings(modifier = Modifier.fillMaxWidth())
                }
            }
            if (homeApps.isEmpty()) {
                item(key = "empty") {
                    EmptyState("No Home apps. Press Menu or long press here to edit Home.")
                }
            } else if (homeMode == HomeLayout.MODE_TILES) {
                item(key = "tiles") {
                    TileGrid(
                        homeApps.map { app ->
                            HomeTile(
                                title = app.label,
                                app = app,
                                accent = appColor(app),
                                liveDatum = liveDatumForApp(app, snapshot),
                                wide = isWideStartTile(app),
                                onClick = { onLaunchApp(app) },
                                onLongClick = { onOpenPeek(app) },
                            )
                        },
                    )
                }
            } else {
                itemsIndexed(homeApps, key = { _, app -> app.stableKey() }) { index, app ->
                    HomeAppRow(
                        app = app,
                        modifier = if (index == 0) Modifier.focusRequester(firstAppFocus) else Modifier,
                        onOpen = { onLaunchApp(app) },
                        onActions = { onOpenPeek(app) },
                        onAppInfo = { onOpenAppInfo(app) },
                    )
                }
            }
        }
        SableQuickBar(
            slots = quickSlots,
            onSlot = onQuickSlot,
            onOptions = onSlotOptions,
            onHubPeek = onHubPeek,
            onPhoneFocusChanged = { phoneSlotFocused = it },
        )
    }
}

/**
 * One Home app row: the app mark, its name and an actions mark. The focused
 * row is filled with the accent (the visual's blue focus bar).
 */
@Composable
private fun HomeAppRow(
    app: AppEntry,
    modifier: Modifier,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    onAppInfo: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
                    val action =
                        when {
                            native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt -> onAppInfo
                            native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU -> onActions
                            native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE -> onActions
                            else -> return@onPreviewKeyEvent false
                        }
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) action()
                    true
                }.combinedClickable(
                    onClick = onOpen,
                    onLongClick = onActions,
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(app = app, size = 28)
        Spacer(Modifier.width(14.dp))
        Text(
            text = app.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "⋮",
            fontSize = 20.sp,
            color = content.copy(alpha = 0.7f),
            modifier =
                Modifier
                    .clickable(onClick = onActions)
                    .semantics { contentDescription = "Actions for ${app.label}" }
                    .padding(horizontal = 8.dp),
        )
    }
}

/**
 * The curated Weather module (HOME_TOP_MODULE): temperature and condition,
 * with the place and range under it. Opens Sable Weather.
 */
@Composable
private fun WeatherModule(
    datum: LiveDatum,
    expanded: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (headline, detail) = weatherLines(datum)
    GlanceCard(
        title = headline,
        detail = detail,
        accent = SableSun,
        round = true,
        large = expanded,
        onClick = onOpen,
        modifier = modifier,
    )
}

/** "72° Partly cloudy" and "Seattle · H 75 / L 61" from the Weather snapshot. */
private fun weatherLines(datum: LiveDatum): Pair<String, String> {
    if (datum.availability != LiveAvailability.LIVE && datum.availability != LiveAvailability.EMPTY) {
        return "Weather" to datum.detail.ifBlank { "unavailable" }
    }
    val parts = datum.detail.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    val tempIndex = parts.indexOfFirst { it.endsWith("°") }
    if (tempIndex < 0) return "Weather" to datum.detail
    val condition = parts.getOrNull(tempIndex + 1)
    val headline = listOfNotNull(parts[tempIndex], condition).joinToString(" ")
    val rest = parts.filterIndexed { i, _ -> i != tempIndex && i != tempIndex + 1 }
    return headline to rest.joinToString(" · ").ifBlank { "Weather" }
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
 * Left page (visual "1. Glance Widgets"): the user's curated widgets in their
 * order, Weather expanded. No ambient polling: values refresh with Start's
 * live snapshot.
 */
@Composable
private fun GlancePage(
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    widgets: List<String>,
    refreshGeneration: Int,
    active: Boolean,
    onLaunchApp: (AppEntry) -> Unit,
    onStartIntent: (Intent) -> Unit,
    onRequestPermissions: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val battery by
        produceState<Pair<Int?, Boolean>>(initialValue = null to false, refreshGeneration) {
            value = withContext(Dispatchers.IO) { readBattery(context) }
        }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    fun open(packageName: String) = findLiveApp(apps, packageName)?.let(onLaunchApp)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (widgets.isEmpty()) {
            item { EmptyState("No Glance widgets. Add some in Edit Home.") }
        }
        itemsIndexed(widgets, key = { _, widget -> widget }) { index, widget ->
            val modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
            when (widget) {
                GlanceWidgets.WEATHER ->
                    WeatherModule(
                        datum = snapshot.weather,
                        expanded = true,
                        onOpen = { open(WEATHER_PACKAGE) },
                        modifier = modifier,
                    )
                GlanceWidgets.BATTERY -> {
                    val (level, charging) = battery
                    GlanceCard(
                        title = "Battery",
                        detail =
                            when {
                                level == null -> "unavailable"
                                charging -> "$level% · charging"
                                else -> "$level%"
                            },
                        accent = SableGreen,
                        onClick = { onStartIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
                        modifier = modifier,
                    )
                }
                GlanceWidgets.PHOTOS -> {
                    val datum = snapshot.photos
                    GlanceCard(
                        title = "Photos",
                        detail =
                            if (datum.availability == LiveAvailability.PERMISSION_REQUIRED) {
                                "Enter to allow local photo counts"
                            } else {
                                datum.detail
                            },
                        accent = SablePurple,
                        onClick = {
                            if (datum.availability == LiveAvailability.PERMISSION_REQUIRED) {
                                onRequestPermissions()
                            } else {
                                open(GALLERY_PACKAGE)
                            }
                        },
                        modifier = modifier,
                    )
                }
                else -> {
                    val (datum, packageName, accent) =
                        when (widget) {
                            GlanceWidgets.CALENDAR -> Triple(snapshot.calendar, CALENDAR_PACKAGE, SableBlue)
                            GlanceWidgets.MEDIA -> Triple(snapshot.music, MEDIA_PACKAGE, SablePurple)
                            GlanceWidgets.HUB -> Triple(snapshot.messages, HUB_PACKAGE, SableGreen)
                            else -> Triple(snapshot.mail, MAIL_PACKAGE, SableOrange)
                        }
                    GlanceCard(
                        title = GlanceWidgets.label(widget),
                        detail = liveDetail(datum, findLiveApp(apps, packageName)),
                        accent = accent,
                        onClick = { open(packageName) },
                        modifier = modifier,
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            MetroLink(text = "edit widgets", onClick = onEdit)
        }
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
        else -> SableStartDestination.Start
    }

/**
 * The three-page Start (SABLE_START_THREE_PAGE_MODEL.md): swipe right or
 * Alt+Left (Fn+Left on Fn keyboards) from Home opens Glance, swipe left or
 * Alt+Right opens Hub. Only the current page is composed into focus traversal.
 */
@Composable
private fun StartPager(
    state: PagerState,
    onMovePage: (Int) -> Unit,
    glance: @Composable () -> Unit,
    hub: @Composable () -> Unit,
    home: @Composable () -> Unit,
) {
    HorizontalPager(
        state = state,
        beyondViewportPageCount = 0,
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val delta =
                        StartPages.keyDelta(
                            keyCode = native.keyCode,
                            metaState = native.metaState,
                        ) ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) onMovePage(delta)
                    true
                },
    ) { page ->
        when (page) {
            StartPages.GLANCE -> glance()
            StartPages.HUB -> hub()
            else -> home()
        }
    }
}

/**
 * Right page (visual "5. Hub"): Sable Hub's close communications with the
 * All / Messages / Calls tabs. Start shows counts only (the snapshots carry
 * no message text) and opens Sable Hub or the owning app for the rest.
 */
@Composable
private fun HubPage(
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    filter: HubFilter,
    active: Boolean,
    onFilter: (HubFilter) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenHub: () -> Unit,
    onStartIntent: (Intent) -> Unit,
) {
    val hubApp = findLiveApp(apps, HUB_PACKAGE)
    val mailApp = findLiveApp(apps, MAIL_PACKAGE)
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { tabFocus.requestFocus() }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = HUB_LABEL,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubFilter.entries.forEach { tab ->
                    val selected = tab == filter
                    Text(
                        text = tab.label,
                        modifier =
                            Modifier
                                .then(if (tab == HubFilter.All) Modifier.focusRequester(tabFocus) else Modifier)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                ).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .sableFocusRing()
                                .clickable { onFilter(tab) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        val showMessages = filter != HubFilter.Calls
        val showCalls = filter != HubFilter.Messages
        if (showMessages) {
            item(key = "messages") {
                GlanceCard(
                    title = "Messages",
                    detail = liveDetail(snapshot.messages, hubApp),
                    accent = SableBlue,
                    onClick = hubApp?.let { { onLaunchApp(it) } },
                )
            }
            item(key = "mail") {
                GlanceCard(
                    title = "Mail",
                    detail = liveDetail(snapshot.mail, mailApp),
                    accent = Color(0xFFE5484D),
                    onClick = mailApp?.let { { onLaunchApp(it) } },
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
                    onClick = { onStartIntent(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)) },
                )
            }
        }
        if (filter == HubFilter.All) {
            item(key = "notifications") {
                GlanceCard(
                    title = "Notifications",
                    detail = if (hubApp == null) "not installed" else "in Sable Hub",
                    accent = SableSun,
                    onClick = if (hubApp == null) null else onOpenHub,
                )
            }
        }
        if (hubApp != null) {
            item(key = "open") {
                Spacer(Modifier.height(4.dp))
                MetroLink(text = "open Sable Hub", onClick = onOpenHub)
            }
        }
    }
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
    onOpenHub: () -> Unit,
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
                    ContextAction(mark = "◉", label = "open Sable Hub", onClick = onOpenHub)
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
 * Persistent base quick bar: labelled slots across the bottom of Home.
 * Enter or a tap opens a slot; long press, Menu or Alt+Enter (Fn+Enter) opens
 * the slot's options; Space on Hub peeks at the Hub page; digits typed while
 * Phone is focused go to the dialer (handled by [HomePage]).
 */
@Composable
private fun SableQuickBar(
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
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        slots.forEachIndexed { index, slot ->
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .sableFocusRing()
                        .onFocusChanged { state ->
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
                                    native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE && slot.token == QuickBar.HUB -> onHubPeek
                                    else -> null
                                }
                            if (action != null && event.type == KeyEventType.KeyDown && native.repeatCount == 0) action()
                            action != null
                        }.combinedClickable(
                            onClick = { onSlot(slot) },
                            onLongClick = { onOptions(slot) },
                        ).semantics {
                            contentDescription = "${slot.label}, ${QuickBar.shortcutLabel(index)}"
                        }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                QuickSlotMark(slot = slot, size = 26)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickSlotMark(
    slot: QuickSlot,
    size: Int,
) {
    val app = slot.app
    if (app != null) {
        AppMark(app = app, size = size)
        return
    }
    val color = MaterialTheme.colorScheme.primary
    ComposeCanvas(Modifier.size(size.dp)) {
        val unit = this.size.minDimension
        val stroke = unit * 0.1f
        if (slot.token == QuickBar.COMMAND) {
            // Magnifier: Command / Search.
            drawCircle(
                color = color,
                radius = unit * 0.28f,
                center = Offset(unit * 0.42f, unit * 0.42f),
                style = Stroke(stroke),
            )
            drawLine(
                color = color,
                start = Offset(unit * 0.62f, unit * 0.62f),
                end = Offset(unit * 0.88f, unit * 0.88f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        } else {
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

/** The three lists Edit Home reorders. */
private enum class EditSection {
    HomeApps,
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
        EditSection.HomeApps -> homeApps
        EditSection.QuickBar -> quickBar
        EditSection.Glance -> glanceWidgets
    }

private fun HomeEdit.with(
    section: EditSection,
    list: List<String>,
): HomeEdit =
    when (section) {
        EditSection.HomeApps -> copy(homeApps = list)
        EditSection.QuickBar -> copy(quickBar = list)
        EditSection.Glance -> copy(glanceWidgets = list)
    }

/** Moves [key] one place in [section] (Command and All Apps move like any slot). */
private fun HomeEdit.moved(
    section: EditSection,
    key: String,
    delta: Int,
): HomeEdit {
    val list = list(section)
    val index = list.indexOf(key)
    val next = if (section == EditSection.QuickBar) QuickBar.move(list, index, delta) else HomeApps.move(list, index, delta)
    return with(section, next)
}

/**
 * Edit Home (visual "3. Edit / Reorder Home"): one mode that reorders the
 * Home apps, the base quick bar and the Glance widgets and chooses the Home
 * modules. Drag the handle, or with the keyboard: Enter picks an item up and
 * drops it, arrows move it while picked up (Up/Down in lists, Left/Right on the
 * bar), Alt+arrows (Fn+arrows) move the focused item at once, Menu or Space
 * opens its options (move, replace, remove, reset), Back cancels the move or
 * leaves without saving, Done saves.
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
    // null: editing; "home": adding a Home app; "bar": adding a slot; "bar:<index>": replacing that slot.
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
        val horizontal = section == EditSection.QuickBar
        val altDelta =
            if (horizontal) {
                StartPages.keyDelta(native.keyCode, native.metaState)
            } else {
                StartPages.reorderDelta(native.keyCode, native.metaState)
            }
        val picked = moving?.let { it.section == section && it.key == key } == true
        val plainDelta =
            when (native.keyCode) {
                KEYCODE_DPAD_UP -> if (horizontal) null else -1
                KEYCODE_DPAD_DOWN -> if (horizontal) null else 1
                KEYCODE_DPAD_LEFT -> if (horizontal) -1 else null
                KEYCODE_DPAD_RIGHT -> if (horizontal) 1 else null
                else -> null
            }.takeIf { picked && !alt }
        val delta = altDelta ?: plainDelta
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
            val choices =
                if (pickKind == "home") {
                    apps
                        .filter { it.inLauncherUser && it.stableKey() !in draft.homeApps }
                        .sortedBy { it.label.lowercase(Locale.getDefault()) }
                        .map { QuickSlot(it.stableKey(), it.label, it) }
                } else {
                    quickBarChoices(apps, appByKey, draft.quickBar)
                }
            item {
                MetroHeader(
                    title = if (pickKind == "home") "add a Home app" else if (pickKind == "bar") "add a slot" else "replace slot",
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
                                    when {
                                        pickKind == "home" -> draft.copy(homeApps = draft.homeApps + choice.token)
                                        pickKind == "bar" -> draft.copy(quickBar = QuickBar.add(draft.quickBar, choice.token))
                                        else -> {
                                            val index = pickKind.substringAfter(':').toIntOrNull() ?: -1
                                            draft.copy(quickBar = QuickBar.replace(draft.quickBar, index, choice.token))
                                        }
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
            SectionLabel("Home apps")
            Spacer(Modifier.height(4.dp))
        }
        itemsIndexed(draft.homeApps, key = { _, key -> "home:$key" }) { index, key ->
            val app = appByKey[key]
            EditListRow(
                label = app?.label ?: "App not installed",
                leading = { if (app != null) AppMark(app = app, size = 24) },
                picked = moving?.let { it.section == EditSection.HomeApps && it.key == key } == true,
                onKey = { event -> itemKeys(EditSection.HomeApps, key, event) },
                onClick = { optionsFor = if (optionsFor == "HomeApps:$key") null else "HomeApps:$key" },
                onDragStep = { delta -> move(EditSection.HomeApps, key, delta) },
            )
            if (optionsFor == "HomeApps:$key") {
                EditOptions(
                    listOf(
                        "move up" to { move(EditSection.HomeApps, key, -1) },
                        "move down" to { move(EditSection.HomeApps, key, 1) },
                        "remove from Home" to {
                            draft = draft.copy(homeApps = draft.homeApps.filterNot { it == key })
                            optionsFor = null
                        },
                    ).filterIndexed { i, _ -> !(i == 0 && index == 0) && !(i == 1 && index == draft.homeApps.lastIndex) },
                )
            }
        }
        item(key = "home-add") {
            MetroLink(text = "+ add a Home app", onClick = { picking = "home" })
            Spacer(Modifier.height(10.dp))
            SectionLabel("Base Quick Bar")
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
                        text = "Command and All Apps always stay on the bar; they can move.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (draft.quickBar.size < QuickBar.MAX_SLOTS) {
                MetroLink(text = "+ add a slot", onClick = { picking = "bar" })
            }
            MetroLink(
                text = "reset bar to Phone · Hub · Command · Apps",
                onClick = { draft = draft.copy(quickBar = QuickBar.DEFAULT) },
            )
            Spacer(Modifier.height(10.dp))
            SectionLabel("Glance widgets")
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
            SectionLabel("Home")
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
            HomeToggleRow(
                title = "Tiles instead of a list",
                subtitle = "Touch tile mode with live details",
                selected = draft.mode == HomeLayout.MODE_TILES,
                accent = accent,
                onClick = {
                    draft =
                        draft.copy(
                            mode = if (draft.mode == HomeLayout.MODE_TILES) HomeLayout.MODE_LIST else HomeLayout.MODE_TILES,
                        )
                },
            )
            Spacer(Modifier.height(10.dp))
            MetroLink(text = "appearance", onClick = onAppearance)
            MetroLink(text = "reset Home to default", onClick = { draft = defaults })
        }
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

/** Role slots first (Phone, Hub, Camera), then every other personal app A to Z. */
private fun quickBarChoices(
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
    current: List<String>,
): List<QuickSlot> {
    val roles =
        listOf(QuickBar.PHONE, QuickBar.HUB, QuickBar.CAMERA)
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
        QuickBar.COMMAND -> QuickSlot(token, "Command", null)
        QuickBar.ALL_APPS -> QuickSlot(token, "Apps", null)
        QuickBar.PHONE ->
            role(listOf(DIALER_PACKAGE), listOf("Phone"))?.let { QuickSlot(token, "Phone", it) }
        QuickBar.HUB ->
            role(listOf(HUB_PACKAGE), listOf(HUB_LABEL, "Hub"))?.let { QuickSlot(token, "Hub", it) }
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
            setOf(HUB_PACKAGE, WEATHER_PACKAGE, MAIL_PACKAGE) ||
            tile.app.label.equals(HUB_LABEL, ignoreCase = true) ||
            tile.app.label.equals(MESSAGES_LABEL, ignoreCase = true) ||
            tile.app.label.equals(WEATHER_LABEL, ignoreCase = true) ||
            tile.app.label.equals(MAIL_LABEL, ignoreCase = true)

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
                ).sableFocusRing()
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
        CALENDAR_PACKAGE ->
            ApprovedWideContent(
                first = "NEXT",
                second = live?.detail ?: "nothing scheduled",
                third = "Calendar",
            )

        MEDIA_PACKAGE -> {
            val parts = live?.detail.orEmpty().split(" · ")
            ApprovedWideContent(
                first = "NOW PLAYING",
                second = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Nothing playing",
                third =
                    parts.drop(1).joinToString(" · ")
                        .replace("playing", "▶")
                        .ifBlank { MEDIA_LABEL },
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
        app.component.packageName == WEATHER_PACKAGE ||
            app.label.equals(WEATHER_LABEL, ignoreCase = true) -> {
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

        app.component.packageName == HUB_PACKAGE ||
            app.label.equals(HUB_LABEL, ignoreCase = true) ||
            app.label.equals(MESSAGES_LABEL, ignoreCase = true) -> {
            val count =
                detail.substringBefore(" ")
                    .takeIf { it.all(Char::isDigit) }
                    ?: "0"
            val label =
                detail.substringAfter(" ", "active")
                    .ifBlank { "active" }
            count to label
        }

        app.component.packageName == MAIL_PACKAGE ||
            app.label.equals(MAIL_LABEL, ignoreCase = true) -> {
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
    when (CoreAppIdentity.canonicalPackage(app.component.packageName)) {
        CoreAppIdentity.PHOTOS ->
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
    onOpenPeek: (AppEntry) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    privateSpaceLocked: Boolean?,
    onTogglePrivateSpace: () -> Unit,
) {
    // Private Space apps live in their own section at the end, never in the A-Z list.
    val (privateApps, listedApps) = remember(apps) { apps.partition { it.profileLabel == PROFILE_PRIVATE_LABEL } }
    val rows = remember(listedApps) { buildRailRows(listedApps) }
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
            Spacer(Modifier.height(12.dp))

            if (rows.isEmpty() && privateSpaceLocked == null) {
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

@Composable
private fun SearchScreen(
    apps: List<AppEntry>,
    recentKeys: List<String>,
    initialQuery: String,
    onBack: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onAppearance: () -> Unit,
    onGlance: () -> Unit,
    onHub: () -> Unit,
    onEditHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
) {
    // Seeded by type-to-launch; the cursor sits after the seed so typing continues.
    var query by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    val parsed = CommandQuery.parse(query.text)
    val term = parsed.term
    val locale = Locale.getDefault()
    val filteredApps =
        remember(apps, recentKeys, parsed) {
            if (!parsed.includes(CommandScope.Apps)) {
                emptyList()
            } else {
                // Recents first, label prefix before contains; then apps whose
                // effective access matches privacy words ("camera", "mic", ...).
                val byName =
                    CommandRanking.apps(
                        items = apps,
                        label = { it.label },
                        recentRank = { app -> recentKeys.indexOf(app.stableKey()).takeIf { it >= 0 } },
                        term = term,
                    )
                val byPrivacy =
                    if (term.isEmpty()) {
                        emptyList()
                    } else {
                        apps.filter { app ->
                            app !in byName && PrivacySummaryPolicy.matchesPrivacyQuery(app.privacy, term)
                        }
                    }
                (byName + byPrivacy).take(if (term.isEmpty()) 6 else 12)
            }
        }

    val commands =
        listOf(
            SearchCommand(
                title = "All apps",
                subtitle = "Alphabetical launcher list",
                mark = "A",
                source = "Start",
                onClick = onAllApps,
            ),
            SearchCommand(
                title = "Edit Home",
                subtitle = "Reorder Home apps, the quick bar and Glance widgets",
                mark = "▭",
                source = "Start",
                onClick = onEditHome,
            ),
            SearchCommand(
                title = "Appearance",
                subtitle = "Theme and Sable accent",
                mark = "◐",
                source = "Start",
                onClick = onAppearance,
            ),
            SearchCommand(
                title = "Glance",
                subtitle = "Weather, calendar, battery and media",
                mark = "◫",
                source = "Start",
                onClick = onGlance,
            ),
            SearchCommand(
                title = "Hub page",
                subtitle = "Messages, mail, missed calls and notifications",
                mark = "◇",
                source = "Start",
                onClick = onHub,
            ),
            SearchCommand(
                title = "System Settings",
                subtitle = "Open Android settings",
                mark = "⚙",
                source = "Settings",
                onClick = onOpenSettings,
            ),
        )
    val filteredCommands =
        if (!parsed.includes(CommandScope.Actions)) {
            emptyList()
        } else {
            commands.filter { command ->
                term.isEmpty() ||
                    command.title.lowercase(locale).contains(term.lowercase(locale)) ||
                    command.subtitle.lowercase(locale).contains(term.lowercase(locale))
            }
        }
    val settingsResults = settingsResults(parsed, onStartIntent)
    val peopleResults = peopleResults(parsed, onStartIntent)
    val hubResults = hubResults(parsed, onStartIntent)
    val topCommand =
        (filteredCommands.takeIf { term.isNotEmpty() }.orEmpty() + settingsResults + peopleResults + hubResults)
            .firstOrNull()

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
            // Enter opens the top result: first app, else the first other result.
            onSubmit = {
                filteredApps.firstOrNull()?.let(onLaunchApp)
                    ?: topCommand?.onClick?.invoke()
            },
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (filteredApps.isNotEmpty()) {
                item {
                    SectionLabel(if (term.isEmpty()) "recent + apps" else "apps")
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
                item {
                    Spacer(Modifier.height(12.dp))
                }
            }
            listOf(
                "people" to peopleResults,
                "settings" to settingsResults,
                "hub" to hubResults,
                (if (term.isEmpty()) "quick actions" else "actions") to filteredCommands,
            ).forEach { (label, rows) ->
                if (rows.isNotEmpty()) {
                    item { SectionLabel(label) }
                    items(rows) { command -> SearchCommandRow(command) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
            if (filteredApps.isEmpty() && filteredCommands.isEmpty() && settingsResults.isEmpty() &&
                peopleResults.isEmpty() && hubResults.isEmpty()
            ) {
                item {
                    EmptyState("No local results match “$term”. Prefixes: ?app @person /settings /hub !action")
                }
            }
        }
    }
}

private fun settingsResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Settings)) return emptyList()
    val limit = if (parsed.scope == CommandScope.Settings) 12 else 4
    return CommandRanking.settings(parsed.term, limit = limit).map { dest ->
        SearchCommand(
            title = dest.title,
            subtitle = "Open ${dest.title} settings",
            mark = "⚙",
            source = "Settings",
            onClick = { onStartIntent(Intent(dest.action)) },
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
): List<SearchCommand> {
    val term = parsed.term
    if (term.isEmpty() || !parsed.includes(CommandScope.People)) return emptyList()
    val dial =
        CommandQuery.dialable(term)?.let { number ->
            SearchCommand(
                title = "Call $number",
                subtitle = "Opens the dialer with the number",
                mark = "☎",
                source = "Phone",
                onClick = { onStartIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) },
            )
        }
    val contacts =
        SearchCommand(
            title = "Find “$term” in Contacts",
            subtitle = "Search people by name, number or email",
            mark = "@",
            source = "Contacts",
            onClick = {
                onStartIntent(
                    Intent(Intent.ACTION_SEARCH)
                        .setPackage("com.android.contacts")
                        .putExtra(SearchManager.QUERY, term),
                )
            },
        ).takeIf { parsed.scope == CommandScope.People || term.length >= 2 }
    return listOfNotNull(dial, contacts)
}

private fun hubResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
): List<SearchCommand> {
    val term = parsed.term
    if (!parsed.includes(CommandScope.Hub) || (term.isEmpty() && parsed.scope != CommandScope.Hub)) return emptyList()
    return listOf(
        SearchCommand(
            title = if (term.isEmpty()) "Open Hub" else "Search Hub for “$term”",
            subtitle = "Messages, calls and notifications",
            mark = "◉",
            source = "Hub",
            onClick = {
                onStartIntent(
                    Intent(Intent.ACTION_MAIN)
                        .setPackage(HUB_PACKAGE)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .putExtra(HUB_EXTRA_QUERY, term),
                )
            },
        ),
    )
}

@Composable
private fun SableSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
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
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .sableFocusRing()
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
        text = profile,
        modifier =
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .semantics { contentDescription = "$profile profile" },
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
    val iconKey = "${app.stableKey()}@$size@$densityDpi"
    // Loaded off the main thread and cached for the process: binding a row never
    // waits on LauncherApps (ROW_BIND_BLOCKING_QUERY=NO).
    val icon by
        produceState<Bitmap?>(initialValue = LauncherIconCache.get(iconKey), iconKey) {
            if (value == null) {
                value =
                    withContext(Dispatchers.IO) {
                        loadLauncherIcon(context = context, app = app, sizeDp = size)
                    }?.also { LauncherIconCache.put(iconKey, it) }
            }
        }

    val loadedIcon = icon
    if (loadedIcon != null) {
        Image(
            bitmap = loadedIcon.asImageBitmap(),
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
        app.label.equals("Phone", ignoreCase = true) -> DIALER_PACKAGE
        app.label.equals(HUB_LABEL, ignoreCase = true) ||
            app.label.equals("Hub", ignoreCase = true) ||
            app.label.equals(MESSAGES_LABEL, ignoreCase = true) ||
            app.label.equals("Messages", ignoreCase = true) -> HUB_PACKAGE
        app.label.equals(MAIL_LABEL, ignoreCase = true) ||
            app.label.equals("Mail", ignoreCase = true) -> MAIL_PACKAGE
        app.label.equals("Sable Calendar", ignoreCase = true) ||
            app.label.equals("Calendar", ignoreCase = true) -> CALENDAR_PACKAGE
        app.label.equals(WEATHER_LABEL, ignoreCase = true) ||
            app.label.equals("Weather", ignoreCase = true) -> WEATHER_PACKAGE
        app.label.equals("Photos", ignoreCase = true) ||
            app.label.equals("Gallery", ignoreCase = true) -> GALLERY_PACKAGE
        app.label.equals(MEDIA_LABEL, ignoreCase = true) ||
            app.label.equals("Media", ignoreCase = true) -> MEDIA_PACKAGE
        app.label.equals("Camera", ignoreCase = true) -> "app.grapheneos.camera"
        app.label.equals(CALCULATOR_LABEL, ignoreCase = true) ||
            app.label.equals("Calculator", ignoreCase = true) -> CALCULATOR_PACKAGE
        app.label.equals("Files", ignoreCase = true) -> FILES_PACKAGE
        app.label.equals("Clock", ignoreCase = true) -> "com.android.deskclock"
        app.label.equals("Vanadium", ignoreCase = true) -> "app.vanadium.browser"
        app.label.equals("Settings", ignoreCase = true) -> SETTINGS_PACKAGE
        else -> null
    }
}

private object LauncherIconCache : LruCache<String, Bitmap>(LAUNCHER_ICON_CACHE_ENTRIES)

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
    isStartTile: Boolean,
    inBaseBar: Boolean?,
    onToggleBaseBar: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onUninstall: () -> Unit,
    onToggleStartTile: () -> Unit,
) {
    val actions =
        remember(app, isStartTile, inBaseBar) {
            AppActionPolicy.actions(
                AppActionContext(
                    pinnedToStart = isStartTile,
                    // null when the bar is full or the app is in another profile.
                    inBaseBar = inBaseBar,
                    launcherUser = app.inLauncherUser,
                    canUninstall = app.canUninstall && app.inLauncherUser,
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
                            AppAction.Open -> "→"
                        },
                    label = if (confirming) AppActionPolicy.confirmationLabel(action) else action.label,
                    onClick = {
                        if (AppActionPolicy.requiresConfirmation(action) && !confirming) {
                            pendingConfirm = action
                        } else {
                            pendingConfirm = null
                            when (action) {
                                AppAction.Open -> onOpen()
                                AppAction.AppInfo -> onOpenAppInfo()
                                AppAction.NotificationSettings -> onOpenNotificationSettings()
                                AppAction.PinToStart, AppAction.RemoveFromStart -> onToggleStartTile()
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

private fun appColor(app: AppEntry): Color =
    when {
        app.component.packageName == HUB_PACKAGE ||
            app.label.equals(HUB_LABEL, ignoreCase = true) ||
            app.label.equals(MESSAGES_LABEL, ignoreCase = true) -> SableGreen
        app.component.packageName == MAIL_PACKAGE ||
            app.label.equals(MAIL_LABEL, ignoreCase = true) -> SableSlate
        app.component.packageName == CALENDAR_PACKAGE ||
            app.label.equals("Calendar", ignoreCase = true) -> SableOrange
        app.component.packageName == WEATHER_PACKAGE ||
            app.label.equals(WEATHER_LABEL, ignoreCase = true) -> SableBlue
        app.component.packageName == MEDIA_PACKAGE ||
            app.label.equals(MEDIA_LABEL, ignoreCase = true) -> SablePurple
        CoreAppIdentity.isRole(app.component.packageName, CoreAppIdentity.PHOTOS) ||
            app.label.equals("Photos", ignoreCase = true) -> SablePurple
        CoreAppIdentity.isRole(app.component.packageName, CoreAppIdentity.CAMERA) ||
            app.component.packageName == CoreAppIdentity.SABLE_CAMERA ||
            app.label.equals("Camera", ignoreCase = true) -> SableSlate
        app.component.packageName == CALCULATOR_PACKAGE ||
            app.label.equals(CALCULATOR_LABEL, ignoreCase = true) -> SableBlue
        app.component.packageName == FILES_PACKAGE ||
            app.label.equals("Files", ignoreCase = true) -> SableOrange
        app.component.packageName == DIALER_PACKAGE ||
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
        app.component.packageName == CALENDAR_PACKAGE ||
            app.label.equals("Calendar", ignoreCase = true) -> snapshot.calendar
        app.component.packageName == HUB_PACKAGE ||
            app.label.equals(HUB_LABEL, ignoreCase = true) ||
            app.label.equals(MESSAGES_LABEL, ignoreCase = true) -> snapshot.messages
        app.component.packageName == MAIL_PACKAGE ||
            app.label.equals(MAIL_LABEL, ignoreCase = true) -> snapshot.mail
        app.component.packageName == WEATHER_PACKAGE ||
            app.label.equals(WEATHER_LABEL, ignoreCase = true) -> snapshot.weather
        app.component.packageName == MEDIA_PACKAGE ||
            app.label.equals(MEDIA_LABEL, ignoreCase = true) -> snapshot.music
        CoreAppIdentity.isRole(app.component.packageName, CoreAppIdentity.PHOTOS) ||
            app.label.equals("Photos", ignoreCase = true) -> snapshot.photos
        else -> null
    }

private fun isWideStartTile(app: AppEntry): Boolean =
    app.component.packageName == CALENDAR_PACKAGE ||
        app.component.packageName == MEDIA_PACKAGE

private data class PreferredStartAppGroup(
    /** Canonical packages for this tile, best first. */
    val packageNames: List<String> = emptyList(),
    val labels: List<String>,
)

private val HOME_APP_DEFAULT_GROUPS =
    listOf(
        PreferredStartAppGroup(
            packageNames = listOf(HUB_PACKAGE),
            labels = listOf(HUB_LABEL, "Hub", MESSAGES_LABEL, "Messages"),
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
private const val MAIL_PACKAGE = "org.sableos.mail"
private const val CALENDAR_PACKAGE = "org.sableos.calendar"
private const val WEATHER_PACKAGE = "org.sableos.weather"
private const val MEDIA_PACKAGE = "org.sableos.media"
private const val DIALER_PACKAGE = "com.android.dialer"
private const val GALLERY_PACKAGE = "com.android.gallery3d"
private const val CALCULATOR_PACKAGE = "org.sableos.calculator"
private const val FILES_PACKAGE = "com.android.documentsui"
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val HUB_LABEL = "Sable Hub"
private const val WEATHER_LABEL = "Sable Weather"
private const val MAIL_LABEL = "Sable Mail"
private const val MESSAGES_LABEL = "Sable Messages"
private const val MEDIA_LABEL = "Sable Media"
private const val CALCULATOR_LABEL = "Sable Calculator"
private const val CONTACTS_PACKAGE = "com.android.contacts"
private const val KEYCODE_DPAD_UP = 19
private const val KEYCODE_DPAD_DOWN = 20
private const val KEYCODE_DPAD_LEFT = 21
private const val KEYCODE_DPAD_RIGHT = 22
private const val PERCENT = 100
private val EditRowStep = 48.dp

// Matches LauncherAppsRepository.PROFILE_PRIVATE.
private const val PROFILE_PRIVATE_LABEL = "private"
private const val HUB_EXTRA_QUERY = "org.sableos.hub.extra.QUERY"
private const val LAUNCHER_ICON_CACHE_ENTRIES = 160
private const val FOCUS_SURFACE_ALL_APPS = "all-apps"
private val PrivacyBadgeWidth = 18.dp
