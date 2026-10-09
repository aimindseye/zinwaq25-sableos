package org.sableos.start.visualreview

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
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
import org.sableos.start.platform.PrivacyFactsReader
import org.sableos.start.privacy.AppPrivacy
import org.sableos.start.privacy.GroupState
import org.sableos.start.privacy.PrivacyGroup
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
                                privacy = privacyReader.read(component.packageName, info.user),
                                profileLabel = if (info.user == Process.myUserHandle()) null else "work",
                            )
                        }
                    }
                }.plus(
                    if (intent.getBooleanExtra(EXTRA_PRIVACY_FIXTURES, false)) privacyFixtureApps() else emptyList(),
                ).distinctBy { entry -> entry.stableKey() }
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

    /**
     * Same reader as SableLauncher (public SDK only); the review host reads only
     * its own user. Review-only: runs during inventory refresh on this activity.
     */
    private val privacyReader =
        PrivacyFactsReader(
            contextForUser = { user -> if (user == Process.myUserHandle()) this else null },
            isLauncherUser = { user -> user == Process.myUserHandle() },
        )

    /**
     * DESIGN-KF-D visual-confirmation fixtures (review APK only, never product):
     * no-sensitive, 1, 3 and >3 permissions, special access, partial access, and
     * the same package in a work profile with a different state.
     */
    private fun privacyFixtureApps(): List<AppEntry> {
        fun entry(
            label: String,
            privacy: AppPrivacy?,
            work: Boolean = false,
        ) = AppEntry(
            label = label,
            component = ComponentName(FIXTURE_PACKAGE, "$FIXTURE_PACKAGE.${label.filter(Char::isLetter)}"),
            user =
                if (work) {
                    UserHandle.getUserHandleForUid(FIXTURE_WORK_USER * PER_USER_RANGE + FIXTURE_APP_ID)
                } else {
                    Process.myUserHandle()
                },
            privacy = privacy,
            profileLabel = if (work) "work" else null,
        )
        fun allowed(vararg groups: PrivacyGroup) = AppPrivacy(groups.associateWith { GroupState.Allowed })
        return listOf(
            entry("Fixture Calculator", allowed()),
            entry("Fixture Camera", allowed(PrivacyGroup.Camera)),
            entry("Fixture Recorder", allowed(PrivacyGroup.Location, PrivacyGroup.Camera, PrivacyGroup.Microphone)),
            entry(
                "Fixture Maps",
                allowed(
                    PrivacyGroup.Location,
                    PrivacyGroup.Contacts,
                    PrivacyGroup.Notifications,
                    PrivacyGroup.Nearby,
                    PrivacyGroup.Overlay,
                ),
            ),
            entry("Fixture Maps", allowed(PrivacyGroup.Notifications), work = true),
            entry(
                "Fixture Gallery",
                AppPrivacy(
                    mapOf(
                        PrivacyGroup.Photos to GroupState.Partial("Photos limited"),
                        PrivacyGroup.Location to GroupState.Partial("Location approximate"),
                    ),
                ),
            ),
            entry("Fixture Unknown", AppPrivacy(mapOf(PrivacyGroup.Camera to GroupState.Unknown))),
            entry("Fixture Unreadable", null),
        )
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
        const val EXTRA_PRIVACY_FIXTURES = "privacyFixtures"
        const val FIXTURE_PACKAGE = "org.sableos.start.visualreview.fixture"
        const val FIXTURE_WORK_USER = 10
        const val FIXTURE_APP_ID = 10_999
        const val PER_USER_RANGE = 100_000
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
    }
}
