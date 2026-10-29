package org.sableos.start

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import com.sable.start.model.StartText
import com.sable.start.model.StartText.t
import org.sableos.design.DesignText
import org.sableos.design.applySableSystemBars
import org.sableos.design.rememberGlobalSableAppearance
import org.sableos.design.resolveSableDarkAppearance
import org.sableos.design.writeGlobalSableAppearance
import org.sableos.start.model.ActiveFrame
import org.sableos.start.model.AppEntry
import org.sableos.start.model.Bb7Detour
import org.sableos.start.model.HomeStyle
import org.sableos.start.platform.LauncherAppsRepository
import org.sableos.start.platform.LiveSurfaceRepository
import org.sableos.start.platform.RecentTasksRepository
import org.sableos.start.platform.SpeedDialRepository
import org.sableos.start.platform.StartStateRepository
import org.sableos.start.ui.Bb7HomeScreen
import org.sableos.start.ui.SableStartScreen
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Standalone SableOS HOME.
 *
 * Launcher3QuickStep is not an Activity superclass and does not own this view
 * hierarchy. It remains installed only as Android's Quickstep/Recents service.
 */
class SableStartActivity : ComponentActivity() {
    private lateinit var launcherAppsRepository: LauncherAppsRepository
    private val speedDialRepository by lazy { SpeedDialRepository(this) }
    private lateinit var liveSurfaceRepository: LiveSurfaceRepository
    private lateinit var startStateRepository: StartStateRepository
    private lateinit var recentTasksRepository: RecentTasksRepository

    private val appsState = mutableStateOf<List<AppEntry>>(emptyList())

    /** Active Frames: recent tasks, refreshed whenever Start comes back. */
    private val framesState = mutableStateOf<List<ActiveFrame>>(emptyList())
    private val framesGeneration = mutableStateOf(0)
    private val framesExecutor = Executors.newSingleThreadExecutor()

    /** null: no private space; true: locked; false: unlocked. */
    private val privateSpaceLockedState = mutableStateOf<Boolean?>(null)
    private val liveRefreshGeneration = mutableStateOf(0)
    private val homeGeneration = mutableStateOf(0)
    private val backGeneration = mutableStateOf(0)

    /** "Show the Hub page" requests (ACTION_SHOW_HUB) and the tab each asked for. */
    private val hubGeneration = mutableStateOf(0)
    private val hubRequestFilter = mutableStateOf<String?>(null)

    /** Screen asked for by Settings (EDIT_HOME, EDIT_QUICK_BAR), and a serial so a repeat opens it again. */
    private val startRequest = mutableStateOf(StartRequest(SCREEN_START, 0))

    /**
     * The home style chosen in Sable Setup (BlackBerry 10 or 7). The BlackBerry 7 home has no Hub,
     * Active Frames or Edit Home of its own, so those requests show the BlackBerry 10 screen
     * ([bb10Detour]) until Home is pressed again.
     */
    private val homeStyle = mutableStateOf(HomeStyle.DEFAULT)
    private val bb10Detour = mutableStateOf(false)
    private var bb7Detour = Bb7Detour()

    /** The BlackBerry 7 home's favourites (the Home apps) and its rows of icons, read in onCreate. */
    private val bb7Favorites = mutableStateOf<List<String>?>(null)
    private val bb7HomeRows = mutableStateOf(1)
    private var opaqueWindowBackground: Drawable? = null

    private var callbackRegistered = false
    private var canNavigateBack = false
    private val inventoryGeneration = AtomicLong(0L)
    private val inventoryExecutor = Executors.newSingleThreadExecutor()

    private val launcherCallback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onPackageEvent(packageName, user)

            override fun onPackageAdded(packageName: String, user: UserHandle) = onPackageEvent(packageName, user)

            override fun onPackageChanged(packageName: String, user: UserHandle) = onPackageEvent(packageName, user)

            private fun onPackageEvent(
                packageName: String,
                user: UserHandle,
            ) {
                launcherAppsRepository.invalidatePrivacy(packageName, user)
                scheduleInventoryRefresh()
            }

            override fun onPackagesAvailable(
                vararg packageNames: String,
                user: UserHandle,
                replacing: Boolean,
            ) {
                packageNames.forEach { launcherAppsRepository.invalidatePrivacy(it, user) }
                scheduleInventoryRefresh()
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                packageNames.forEach { launcherAppsRepository.invalidatePrivacy(it, user) }
                scheduleInventoryRefresh()
            }

            override fun onPackagesSuspended(
                vararg packageNames: String,
                user: UserHandle,
            ) = scheduleInventoryRefresh()

            override fun onPackagesUnsuspended(
                vararg packageNames: String,
                user: UserHandle,
            ) = scheduleInventoryRefresh()
        }

    /** Runtime-permission grant/revoke anywhere (signal carries a uid). */
    private val permissionsChangedListener =
        PackageManager.OnPermissionsChangedListener { uid ->
            launcherAppsRepository.invalidatePrivacyForUid(uid)
            runOnUiThread { scheduleInventoryRefresh() }
        }
    private var permissionsListenerRegistered = false

    /** Work/private profile added, removed, paused or unlocked: drop that user's snapshots. */
    private val profileReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                @Suppress("DEPRECATION")
                val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
                launcherAppsRepository.invalidatePrivacyForUser(user)
                scheduleInventoryRefresh()
            }
        }
    private var profileReceiverRegistered = false

    private val liveContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                liveRefreshGeneration.value += 1
            }
        }

    private val appInventoryObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleInventoryRefresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)

        launcherAppsRepository = LauncherAppsRepository(this)
        liveSurfaceRepository = LiveSurfaceRepository(this)
        startStateRepository = StartStateRepository(this)
        bb7Favorites.value = startStateRepository.loadHomeApps()
        bb7HomeRows.value = startStateRepository.loadBb7HomeRows()
        recentTasksRepository = RecentTasksRepository(this)
        homeStyle.value = startStateRepository.loadHomeStyle()
        opaqueWindowBackground = window.decorView.background

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (canNavigateBack) {
                        backGeneration.value += 1
                    }
                    // HOME root deliberately consumes Back.
                }
            },
        )

        LiveSurfaceRepository.OBSERVED_URIS.forEach { uri ->
            runCatching {
                contentResolver.registerContentObserver(
                    uri,
                    true,
                    liveContentObserver,
                )
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                LauncherAppsRepository.HIDDEN_APPS_URI,
                false,
                appInventoryObserver,
            )
        }

        registerPrivacySignals()
        scheduleInventoryRefresh()
        // A recreated activity keeps its own place instead of reopening the requested screen.
        if (savedInstanceState == null) {
            startRequest.value = StartRequest(screenFor(intent), 0)
            if (intent?.action == ACTION_SHOW_HUB) requestHub(intent)
            if (intent?.action in BB10_ONLY_ACTIONS) {
                startBb10Detour(returnOnHome = intent?.action != ACTION_SHOW_ACTIVE_FRAMES)
            }
        }

        setContent {
            val appearance = rememberGlobalSableAppearance()

            SideEffect {
                applySableSystemBars(
                    window = window,
                    darkTheme =
                        resolveSableDarkAppearance(
                            context = this@SableStartActivity,
                            appearance = appearance,
                        ),
                )
            }

            val bb7 = homeStyle.value == HomeStyle.Bb7 && !bb10Detour.value
            SideEffect { showWallpaper(bb7) }
            if (bb7) {
                Bb7HomeScreen(
                    apps = appsState.value,
                    favoriteKeys = bb7Favorites.value,
                    homeRows = bb7HomeRows.value,
                    liveSurfaceRepository = liveSurfaceRepository,
                    appearance = appearance,
                    refreshGeneration = liveRefreshGeneration.value,
                    homeGeneration = homeGeneration.value,
                    onLaunchApp = { entry -> launcherAppsRepository.launch(entry) },
                    onOpenAppInfo = { entry -> launcherAppsRepository.openAppDetails(entry) },
                    onOpenHub = {
                        hubRequestFilter.value = null
                        hubGeneration.value += 1
                        startBb10Detour(returnOnHome = true)
                    },
                    onFavoritesChanged = { keys ->
                        bb7Favorites.value = keys
                        startStateRepository.saveHomeApps(keys)
                    },
                    onHomeRowsChanged = { rows ->
                        bb7HomeRows.value = rows
                        startStateRepository.saveBb7HomeRows(rows)
                    },
                    onOpenMenu = ::showBb7Menu,
                )
                return@setContent
            }

            val request = startRequest.value
            key(request.serial) {
                SableStartScreen(
                    apps = appsState.value,
                    speedDial = speedDialRepository,
                    privateSpaceLocked = privateSpaceLockedState.value,
                    onTogglePrivateSpace = ::togglePrivateSpace,
                    liveSurfaceRepository = liveSurfaceRepository,
                    appearance = appearance,
                    refreshGeneration = liveRefreshGeneration.value,
                    homeGeneration = homeGeneration.value,
                    backGeneration = backGeneration.value,
                    initialScreen = request.screen,
                    onCanNavigateBackChanged = { value ->
                        canNavigateBack = value
                        bb7Detour = bb7Detour.onCanNavigateBack(value)
                        if (bb10Detour.value && bb7Detour.endsAt(value)) endBb10Detour()
                    },
                    initialStartTileKeys = startStateRepository.loadStartTileKeys(),
                    initialHomeApps = startStateRepository.loadHomeApps(),
                    initialGridOrder = startStateRepository.loadGridOrder(),
                    onGridOrderChanged = startStateRepository::saveGridOrder,
                    initialQuickBar = startStateRepository.loadQuickBar(),
                    onQuickBarChanged = startStateRepository::saveQuickBar,
                    initialGlanceWidgets = startStateRepository.loadGlanceWidgets(),
                    onGlanceWidgetsChanged = startStateRepository::saveGlanceWidgets,
                    initialHiddenHomeSections = startStateRepository.loadHiddenHomeSections(),
                    onHiddenHomeSectionsChanged = startStateRepository::saveHiddenHomeSections,
                    frames = framesState.value,
                    framesGeneration = framesGeneration.value,
                    frameSnapshot = { frame -> recentTasksRepository.snapshot(frame.taskId) },
                    onResumeFrame = ::resumeFrame,
                    onCloseFrame = ::closeFrame,
                    onAppearanceChanged = { next ->
                        writeGlobalSableAppearance(this@SableStartActivity, next)
                    },
                    onResetAppearance = {
                        writeGlobalSableAppearance(
                            this@SableStartActivity,
                            org.sableos.design.SableAppearance(),
                        )
                    },
                    onLaunchApp = { entry ->
                        launcherAppsRepository.launch(entry)
                    },
                    onOpenSystemSettings = ::openSystemSettings,
                    onStartIntent = { intent ->
                        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    onOpenAppInfo = { entry ->
                        launcherAppsRepository.openAppDetails(entry)
                    },
                    onOpenNotificationSettings = { entry ->
                        launcherAppsRepository.openNotificationSettings(entry)
                    },
                    onUninstallApp = { entry ->
                        launcherAppsRepository.requestUninstall(entry)
                    },
                    onRequestLivePermissions = ::requestLivePermissions,
                    onRefreshLive = {
                        liveRefreshGeneration.value += 1
                    },
                    hubGeneration = hubGeneration.value,
                    hubRequestFilter = hubRequestFilter.value,
                    onRequestContactsPermission = ::requestContactsPermission,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!callbackRegistered) {
            launcherAppsRepository.registerCallback(launcherCallback)
            callbackRegistered = true
        }
        scheduleInventoryRefresh()
    }

    override fun onResume() {
        super.onResume()
        val style = startStateRepository.loadHomeStyle()
        if (style != homeStyle.value) {
            homeStyle.value = style
            endBb10Detour()
        }
        // Edit Home on the BlackBerry 10 screen edits the same list.
        bb7Favorites.value = startStateRepository.loadHomeApps()
        scheduleInventoryRefresh()
        refreshFrames()
        liveRefreshGeneration.value += 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME -> {
                // The Screen is recomposed fresh for the request: its Home/Back effects act only on
                // counts above zero, so they restart from zero rather than undo the request at once.
                homeGeneration.value = 0
                backGeneration.value = 0
                hubGeneration.value = 0
                startRequest.value = StartRequest(screenFor(intent), startRequest.value.serial + 1)
                startBb10Detour(returnOnHome = true)
            }
            // Home, and the Recents key (Active Frames are the BB10 Recents): close anything open
            // over the pages and show Active Frames.
            Intent.ACTION_MAIN, ACTION_SHOW_ACTIVE_FRAMES -> {
                homeGeneration.value += 1
                refreshFrames()
                // On the BlackBerry 7 home, Home goes back to it; Recents shows the Active Frames.
                if (intent.action == ACTION_SHOW_ACTIVE_FRAMES) {
                    startBb10Detour(returnOnHome = false)
                } else {
                    endBb10Detour()
                }
            }
            // The one Hub is Start's Hub page (the Sable Hub app has no launcher entry).
            ACTION_SHOW_HUB -> {
                requestHub(intent)
                startBb10Detour(returnOnHome = true)
            }
        }
    }

    override fun onStop() {
        if (callbackRegistered) {
            launcherAppsRepository.unregisterCallback(launcherCallback)
            callbackRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (callbackRegistered) {
            launcherAppsRepository.unregisterCallback(launcherCallback)
            callbackRegistered = false
        }
        runCatching {
            contentResolver.unregisterContentObserver(liveContentObserver)
        }
        runCatching {
            contentResolver.unregisterContentObserver(appInventoryObserver)
        }
        unregisterPrivacySignals()
        inventoryGeneration.incrementAndGet()
        inventoryExecutor.shutdownNow()
        framesExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LIVE_PERMISSION_REQUEST_CODE || requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
            liveRefreshGeneration.value += 1
        }
    }

    private fun scheduleInventoryRefresh() {
        val generation = inventoryGeneration.incrementAndGet()
        runCatching {
            inventoryExecutor.execute {
                val apps = launcherAppsRepository.loadApps()
                val privateSpaceLocked =
                    launcherAppsRepository.privateSpace()?.let(launcherAppsRepository::isLocked)
                runOnUiThread {
                    if (
                        !isDestroyed &&
                        inventoryGeneration.get() == generation
                    ) {
                        appsState.value = apps
                        privateSpaceLockedState.value = privateSpaceLocked
                    }
                }
            }
        }
    }

    /** Private Space never shows as an Active Frame, locked or not. */
    private fun privateUsers(): Set<Int> =
        runCatching { setOfNotNull(launcherAppsRepository.privateSpace()?.identifier) }.getOrDefault(emptySet())

    /** Reads the recent tasks off the main thread; the snapshots repaint with them. */
    private fun refreshFrames() {
        runCatching {
            framesExecutor.execute {
                val frames = recentTasksRepository.loadFrames(privateUsers())
                runOnUiThread {
                    if (!isDestroyed) {
                        framesState.value = frames
                        framesGeneration.value += 1
                    }
                }
            }
        }
    }

    /** Enter on a frame: the task as it was; if Android refuses, the app starts fresh. */
    private fun resumeFrame(frame: ActiveFrame) {
        runCatching {
            framesExecutor.execute {
                if (!recentTasksRepository.resume(frame.taskId)) {
                    recentTasksRepository.launchIntent(frame)?.let { intent ->
                        runOnUiThread { runCatching { startActivity(intent) } }
                    }
                }
            }
        }
    }

    /** X or Backspace on a frame: gone from Start at once, then removed from Recents. */
    private fun closeFrame(frame: ActiveFrame) {
        framesState.value = framesState.value.filterNot { it.taskId == frame.taskId }
        runCatching {
            framesExecutor.execute {
                recentTasksRepository.close(frame.taskId)
                val frames = recentTasksRepository.loadFrames(privateUsers())
                runOnUiThread { if (!isDestroyed) framesState.value = frames }
            }
        }
    }

    private fun registerPrivacySignals() {
        if (!permissionsListenerRegistered) {
            permissionsListenerRegistered =
                runCatching {
                    packageManager.addOnPermissionsChangeListener(permissionsChangedListener)
                    true
                }.getOrDefault(false)
        }
        if (!profileReceiverRegistered) {
            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                    addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                    addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                    addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                    addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
                    addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                    addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
                    addAction(Intent.ACTION_PROFILE_AVAILABLE)
                    addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                    addAction(Intent.ACTION_PROFILE_ADDED)
                    addAction(Intent.ACTION_PROFILE_REMOVED)
                }
            profileReceiverRegistered =
                runCatching {
                    registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    true
                }.getOrDefault(false)
        }
    }

    private fun unregisterPrivacySignals() {
        if (permissionsListenerRegistered) {
            runCatching { packageManager.removeOnPermissionsChangeListener(permissionsChangedListener) }
            permissionsListenerRegistered = false
        }
        if (profileReceiverRegistered) {
            runCatching { unregisterReceiver(profileReceiver) }
            profileReceiverRegistered = false
        }
    }

    /** Lock or unlock the private space; the profile broadcasts refresh All Apps. */
    private fun togglePrivateSpace() {
        val locked = privateSpaceLockedState.value ?: return
        inventoryExecutor.execute {
            val user = launcherAppsRepository.privateSpace() ?: return@execute
            launcherAppsRepository.setLocked(user, !locked)
            runOnUiThread { scheduleInventoryRefresh() }
        }
    }

    /** The BlackBerry 7 home draws on the wallpaper; the BlackBerry 10 screen paints its own. */
    private fun showWallpaper(show: Boolean) {
        if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(opaqueWindowBackground)
        }
    }

    /**
     * Menu on the BlackBerry 7 home, a full menu as BlackBerry 7 asks: the highlighted icon's
     * items ([contextual]) first, then the home's own, then Close.
     */
    private fun showBb7Menu(contextual: List<Pair<String, () -> Unit>>) {
        val global =
            listOf<Pair<String, () -> Unit>>(
                t("hub") to {
                    hubRequestFilter.value = null
                    hubGeneration.value += 1
                    startBb10Detour(returnOnHome = true)
                },
                t("home_screen_style_2") to {
                    runCatching { startActivity(Intent(this, HomeStyleActivity::class.java)) }
                },
                t("settings") to ::openSystemSettings,
                t("close") to {},
            )
        val items = contextual + global
        AlertDialog.Builder(this)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun openSystemSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestLivePermissions() {
        val permissions =
            if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

        requestPermissions(
            permissions,
            LIVE_PERMISSION_REQUEST_CODE,
        )
    }

    /** The Hub page's Contacts tab: names, favourites and numbers, read on the device only. */
    private fun requestContactsPermission() {
        requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_PERMISSION_REQUEST_CODE)
    }

    private fun startBb10Detour(returnOnHome: Boolean) {
        bb10Detour.value = true
        bb7Detour = Bb7Detour(returnOnHome = returnOnHome)
    }

    private fun endBb10Detour() {
        bb10Detour.value = false
        bb7Detour = Bb7Detour()
    }

    private fun requestHub(intent: Intent) {
        hubRequestFilter.value = intent.getStringExtra(EXTRA_HUB_FILTER)
        hubGeneration.value += 1
    }

    private data class StartRequest(
        val screen: String,
        val serial: Int,
    )

    private companion object {
        const val LIVE_PERMISSION_REQUEST_CODE = 0x534D
        const val CONTACTS_PERMISSION_REQUEST_CODE = 0x534E

        /** Show the Hub page; [EXTRA_HUB_FILTER] picks the tab ("all", "messages", "calls", "contacts"). */
        const val ACTION_SHOW_HUB = "org.sableos.start.action.SHOW_HUB"
        const val EXTRA_HUB_FILTER = "org.sableos.start.extra.HUB_FILTER"

        /** Settings > Sable > Quick bar: Edit Home (the quick bar is edited there with the Home apps). */
        const val ACTION_EDIT_QUICK_BAR = "org.sableos.start.action.EDIT_QUICK_BAR"

        /** Settings > Sable Start > Home layout: Edit Home. */
        const val ACTION_EDIT_HOME = "org.sableos.start.action.EDIT_HOME"

        /** The Recents key or button (sent by Launcher3QuickStep): Active Frames, the BB10 Recents. */
        const val ACTION_SHOW_ACTIVE_FRAMES = "org.sableos.start.action.SHOW_ACTIVE_FRAMES"
        const val SCREEN_START = "start"
        const val SCREEN_EDIT_HOME = "home"

        /** Requests the BlackBerry 7 home shows on the BlackBerry 10 screen. */
        val BB10_ONLY_ACTIONS =
            setOf(ACTION_EDIT_HOME, ACTION_EDIT_QUICK_BAR, ACTION_SHOW_HUB, ACTION_SHOW_ACTIVE_FRAMES)

        fun screenFor(intent: Intent?): String =
            when (intent?.action) {
                ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME -> SCREEN_EDIT_HOME
                else -> SCREEN_START
            }
    }
}
