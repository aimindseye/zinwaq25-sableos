package org.sableos.start

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import org.sableos.design.applySableSystemBars
import org.sableos.design.rememberGlobalSableAppearance
import org.sableos.design.resolveSableDarkAppearance
import org.sableos.design.writeGlobalSableAppearance
import org.sableos.start.model.AppEntry
import org.sableos.start.platform.LauncherAppsRepository
import org.sableos.start.platform.LiveSurfaceRepository
import org.sableos.start.platform.StartStateRepository
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
    private lateinit var liveSurfaceRepository: LiveSurfaceRepository
    private lateinit var startStateRepository: StartStateRepository

    private val appsState = mutableStateOf<List<AppEntry>>(emptyList())

    /** null: no private space; true: locked; false: unlocked. */
    private val privateSpaceLockedState = mutableStateOf<Boolean?>(null)
    private val liveRefreshGeneration = mutableStateOf(0)
    private val homeGeneration = mutableStateOf(0)
    private val backGeneration = mutableStateOf(0)

    /** Screen asked for by Settings (EDIT_QUICK_BAR), and a serial so a repeat opens it again. */
    private val startRequest = mutableStateOf(StartRequest(SCREEN_START, 0))

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

        launcherAppsRepository = LauncherAppsRepository(this)
        liveSurfaceRepository = LiveSurfaceRepository(this)
        startStateRepository = StartStateRepository(this)

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

            val request = startRequest.value
            key(request.serial) {
                SableStartScreen(
                    apps = appsState.value,
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
                    },
                    initialFavoriteKeys = startStateRepository.loadFavoriteKeys(),
                    onFavoriteKeysChanged = startStateRepository::saveFavoriteKeys,
                    initialStartTileKeys = startStateRepository.loadStartTileKeys(),
                    onStartTileKeysChanged = startStateRepository::saveStartTileKeys,
                    initialQuickBar = startStateRepository.loadQuickBar(),
                    onQuickBarChanged = startStateRepository::saveQuickBar,
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
        scheduleInventoryRefresh()
        liveRefreshGeneration.value += 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_EDIT_QUICK_BAR) {
            // The Screen is recomposed fresh for the request: its Home/Back effects act only on
            // counts above zero, so they restart from zero rather than undo the request at once.
            homeGeneration.value = 0
            backGeneration.value = 0
            startRequest.value = StartRequest(screenFor(intent), startRequest.value.serial + 1)
        } else if (intent.action == Intent.ACTION_MAIN) {
            homeGeneration.value += 1
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
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LIVE_PERMISSION_REQUEST_CODE) {
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

    private data class StartRequest(
        val screen: String,
        val serial: Int,
    )

    private companion object {
        const val LIVE_PERMISSION_REQUEST_CODE = 0x534D

        /** Settings > Sable > Quick bar: open Sable Start's quick bar editor. */
        const val ACTION_EDIT_QUICK_BAR = "org.sableos.start.action.EDIT_QUICK_BAR"
        const val SCREEN_START = "start"
        const val SCREEN_QUICK_BAR = "quickbar"

        fun screenFor(intent: Intent?): String =
            if (intent?.action == ACTION_EDIT_QUICK_BAR) SCREEN_QUICK_BAR else SCREEN_START
    }
}
