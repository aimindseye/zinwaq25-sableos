package org.sableos.start.visualreview

import android.Manifest
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import org.sableos.design.AppearanceMode
import org.sableos.design.SableAppearance
import org.sableos.design.applySableSystemBars
import org.sableos.design.resolveSableDarkAppearance
import org.sableos.start.model.AppEntry
import org.sableos.start.model.stableKey
import org.sableos.start.platform.LiveSurfaceRepository
import org.sableos.start.ui.SableStartScreen
import java.util.Locale

/**
 * Non-product Android Studio / Pixel 7 emulator host for the production R9
 * SableStartScreen.
 *
 * This APK deliberately has no HOME intent filter and is never part of the
 * Panther product. Launcher3QuickStep remains the only product HOME. The
 * activity exists solely to make the exact production presentation source
 * fast to review on the established Mac mini emulator workflow.
 */
class R9LauncherVisualReviewActivity : ComponentActivity() {
    private val appsState = mutableStateOf<List<AppEntry>>(emptyList())
    private val appearanceState = mutableStateOf(SableAppearance())
    private val refreshGeneration = mutableStateOf(0)
    private val livePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            refreshLiveSurface()
        }

    private lateinit var launcherApps: LauncherApps
    private lateinit var liveSurfaceRepository: LiveSurfaceRepository

    private var favoriteKeys: Set<String> = emptySet()
    private var startTileKeys: Set<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        launcherApps = getSystemService(LauncherApps::class.java)
        liveSurfaceRepository = LiveSurfaceRepository(this)
        appearanceState.value =
            when (intent.getStringExtra(EXTRA_APPEARANCE_MODE)?.lowercase(Locale.ROOT)) {
                "light" -> SableAppearance(mode = AppearanceMode.Light)
                "dark" -> SableAppearance(mode = AppearanceMode.Dark)
                "system" -> SableAppearance(mode = AppearanceMode.FollowSystem)
                else -> SableAppearance()
            }

        refreshInventory()
        favoriteKeys = defaultFavoriteKeys(appsState.value)
        startTileKeys =
            if (intent.getBooleanExtra(EXTRA_EXTENDED_START_TILES, false)) {
                extendedStartTileKeys(appsState.value)
            } else {
                null
            }

        applyCurrentSystemBars()

        setContent {
            SableStartScreen(
                apps = appsState.value,
                liveSurfaceRepository = liveSurfaceRepository,
                appearance = appearanceState.value,
                refreshGeneration = refreshGeneration.value,
                initialScreen = intent.getStringExtra(EXTRA_INITIAL_SCREEN) ?: "start",
                initialFavoriteKeys = favoriteKeys,
                onFavoriteKeysChanged = { keys ->
                    favoriteKeys = keys
                },
                initialStartTileKeys = startTileKeys,
                onStartTileKeysChanged = { keys ->
                    startTileKeys = keys
                },
                onAppearanceChanged = { appearance ->
                    appearanceState.value = appearance
                    applyCurrentSystemBars()
                },
                onResetAppearance = {
                    appearanceState.value = SableAppearance()
                    applyCurrentSystemBars()
                },
                onLaunchApp = ::launchApp,
                onOpenSystemSettings = ::openSettings,
                onOpenAppInfo = ::openAppInfo,
                onRequestLivePermissions = ::requestLivePermissions,
                onRefreshLive = ::refreshLiveSurface,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::launcherApps.isInitialized) {
            refreshInventory()
            applyCurrentSystemBars()
            refreshLiveSurface()
        }
    }

    private fun refreshLiveSurface() {
        refreshGeneration.value += 1
    }

    private fun applyCurrentSystemBars() {
        applySableSystemBars(
            window = window,
            darkTheme =
                resolveSableDarkAppearance(
                    context = this,
                    appearance = appearanceState.value,
                ),
        )
    }

    private fun refreshInventory() {
        val locale = Locale.getDefault()

        appsState.value =
            launcherApps.profiles
                .flatMap { user ->
                    launcherApps.getActivityList(null, user).mapNotNull { info ->
                        val component = info.componentName
                        val rawLabel =
                            info.label
                                ?.toString()
                                ?.trim()
                                .orEmpty()
                        val label = rawLabel
                        if (
                            label.isEmpty() ||
                            component.className in REVIEW_INTERNAL_COMPONENTS ||
                            component.packageName == GRAPHENE_MESSAGING_PACKAGE ||
                            component.packageName == HOST_AVD_GOOGLE_MESSAGES_PACKAGE ||
                            component.packageName == HOST_AVD_CHROME_PACKAGE ||
                            component.packageName == HOST_AVD_GOOGLE_CALENDAR_PACKAGE ||
                            component.packageName == HOST_AVD_AOSP_CALENDAR_PACKAGE ||
                            component.packageName == RETIRED_WEATHER_REVIEW_PACKAGE
                        ) {
                            null
                        } else {
                            AppEntry(
                                label = label,
                                component = component,
                                user = info.user,
                                privacySummary =
                                    buildPrivacyPermissionSummary(
                                        packageName = component.packageName,
                                    ),
                            )
                        }
                    }
                }.distinctBy { entry -> entry.stableKey() }
                .sortedWith(
                    compareBy<AppEntry> { entry ->
                        entry.label.lowercase(locale)
                    }.thenBy { entry ->
                        entry.component.flattenToString()
                    }.thenBy { entry ->
                        entry.user.hashCode()
                    },
                )
    }

    private fun buildPrivacyPermissionSummary(packageName: String): String {
        val packageInfo =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(
                            PackageManager.GET_PERMISSIONS.toLong(),
                        ),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_PERMISSIONS,
                    )
                }
            }.getOrNull() ?: return "permissions · unavailable"

        val requested = packageInfo.requestedPermissions.orEmpty()
        val flags = packageInfo.requestedPermissionsFlags ?: IntArray(0)

        val granted =
            buildSet {
                requested.forEachIndexed { index, permission ->
                    val permissionFlags = flags.getOrNull(index) ?: 0
                    if (
                        permissionFlags and
                        PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    ) {
                        add(permission)
                    }
                }
            }

        val labels =
            PRIVACY_PERMISSION_GROUPS.mapNotNull { group ->
                group.label.takeIf { _ ->
                    group.permissions.any(granted::contains)
                }
            }

        return if (labels.isEmpty()) {
            "permissions · none sensitive"
        } else {
            "permissions · " + labels.joinToString(" · ")
        }
    }

    private fun launchApp(entry: AppEntry): Boolean =
        runCatching {
            launcherApps.startMainActivity(
                entry.component,
                entry.user,
                null,
                null,
            )
            true
        }.getOrElse {
            false
        }

    private fun openSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openAppInfo(entry: AppEntry) {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${entry.component.packageName}"),
                ),
            )
        }
    }

    private fun requestLivePermissions() {
        val permissions =
            buildList {
                if (Build.VERSION.SDK_INT >= 33) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

        livePermissionLauncher.launch(
            permissions.toTypedArray(),
        )
    }

    private fun extendedStartTileKeys(apps: List<AppEntry>): Set<String> {
        val selected = linkedMapOf<String, AppEntry>()

        REVIEW_START_LABELS.forEach { preferred ->
            apps
                .firstOrNull { entry ->
                    entry.label.equals(preferred, ignoreCase = true)
                }?.let { entry ->
                    selected.putIfAbsent(entry.stableKey(), entry)
                }
        }

        apps.forEach { entry ->
            if (selected.size < REVIEW_START_TILE_COUNT) {
                selected.putIfAbsent(entry.stableKey(), entry)
            }
        }

        return selected.values
            .take(REVIEW_START_TILE_COUNT)
            .mapTo(linkedSetOf()) { entry -> entry.stableKey() }
    }

    private fun defaultFavoriteKeys(apps: List<AppEntry>): Set<String> {
        val selected = linkedMapOf<String, AppEntry>()

        REVIEW_PIN_LABELS.forEach { preferred ->
            apps
                .firstOrNull { entry ->
                    entry.label.equals(preferred, ignoreCase = true)
                }?.let { entry ->
                    selected.putIfAbsent(entry.stableKey(), entry)
                }
        }

        apps.forEach { entry ->
            if (selected.size < REVIEW_PIN_COUNT) {
                selected.putIfAbsent(entry.stableKey(), entry)
            }
        }

        return selected.values
            .take(REVIEW_PIN_COUNT)
            .mapTo(linkedSetOf()) { entry -> entry.stableKey() }
    }

    private data class PrivacyPermissionGroup(
        val label: String,
        val permissions: Set<String>,
    )

    private companion object {
        const val GRAPHENE_MESSAGING_PACKAGE = "com.android.messaging"
        val REVIEW_INTERNAL_COMPONENTS =
            setOf(
                "org.sableos.start.visualreview.R9LauncherVisualReviewActivity",
                "org.sableos.start.visualreview.R9DailyDriverDesignReviewActivity",
            )

        const val HOST_AVD_GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        const val HOST_AVD_CHROME_PACKAGE = "com.android.chrome"
        const val HOST_AVD_GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"
        const val HOST_AVD_AOSP_CALENDAR_PACKAGE = "com.android.calendar"
        const val RETIRED_WEATHER_REVIEW_PACKAGE = "org.sableos.weather.visualreview"
        const val EXTRA_EXTENDED_START_TILES = "extendedStartTiles"
        const val EXTRA_APPEARANCE_MODE = "appearanceMode"
        const val EXTRA_INITIAL_SCREEN = "initialScreen"
        const val REVIEW_PIN_COUNT = 3
        const val REVIEW_START_TILE_COUNT = 14

        val REVIEW_PIN_LABELS =
            listOf(
                "Phone",
                "Calendar",
                "Calculator",
                "Files",
                "Camera",
            )

        val REVIEW_START_LABELS =
            listOf(
                "Phone",
                "Messages",
                "Sable Mail",
                "Mail",
                "Calendar",
                "Sable Weather",
                "Weather",
                "Photos",
                "Sable Media",
                "Media",
                "Camera",
                "Sable Calculator",
                "Calculator",
                "Files",
                "Settings",
            )

        val PRIVACY_PERMISSION_GROUPS =
            listOf(
                PrivacyPermissionGroup(
                    label = "location",
                    permissions =
                        setOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        ),
                ),
                PrivacyPermissionGroup(
                    label = "camera",
                    permissions = setOf(Manifest.permission.CAMERA),
                ),
                PrivacyPermissionGroup(
                    label = "microphone",
                    permissions = setOf(Manifest.permission.RECORD_AUDIO),
                ),
                PrivacyPermissionGroup(
                    label = "contacts",
                    permissions =
                        setOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS,
                            Manifest.permission.GET_ACCOUNTS,
                        ),
                ),
                PrivacyPermissionGroup(
                    label = "phone",
                    permissions =
                        setOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_PHONE_NUMBERS,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.ANSWER_PHONE_CALLS,
                        ),
                ),
                PrivacyPermissionGroup(
                    label = "messages",
                    permissions =
                        setOf(
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.RECEIVE_MMS,
                        ),
                ),
                PrivacyPermissionGroup(
                    label = "calendar",
                    permissions =
                        setOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        ),
                ),
                PrivacyPermissionGroup(
                    label = "photos",
                    permissions =
                        setOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                        ),
                ),
                PrivacyPermissionGroup(
                    label = "audio",
                    permissions = setOf(Manifest.permission.READ_MEDIA_AUDIO),
                ),
                PrivacyPermissionGroup(
                    label = "nearby",
                    permissions =
                        setOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.NEARBY_WIFI_DEVICES,
                        ),
                ),
                PrivacyPermissionGroup(
                    label = "notifications",
                    permissions = setOf(Manifest.permission.POST_NOTIFICATIONS),
                ),
            )
    }
}
