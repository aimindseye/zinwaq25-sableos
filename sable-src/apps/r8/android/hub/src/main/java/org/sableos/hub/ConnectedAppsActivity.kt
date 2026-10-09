package org.sableos.hub

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.design.SableGlobalTheme
import org.sableos.hub.notifications.ConnectedReplyRegistry
import org.sableos.hub.notifications.SableNotificationListenerService
import org.sableos.hub.platform.AndroidSettingsLauncher
import org.sableos.hub.platform.DeviceAttention
import org.sableos.hub.policy.AndroidSettingsRoutes
import org.sableos.hub.policy.AttentionOutput
import org.sableos.hub.policy.AttentionSelection
import org.sableos.hub.policy.AttentionSelections
import org.sableos.hub.policy.HubIntents
import org.sableos.hub.policy.NotificationConcept
import org.sableos.hub.policy.SettingsRoute
import org.sableos.hub.policy.SettingsTarget
import org.sableos.hub.ui.AttentionUi
import org.sableos.hub.ui.ConnectedAppsScreen

/**
 * Connected apps: Hub inclusion, Hub priority, preview and history per Android user/profile +
 * package, plus Sable Attention outputs the device profile supports. Android delivery policy is
 * reached through Android Settings, never mirrored here. Also handles
 * [HubIntents.ACTION_APP_NOTIFICATION_SETTINGS] from Settings' per-app notification page.
 */
class ConnectedAppsActivity : ComponentActivity() {
    private lateinit var repository: ConnectedAppsRepository
    private lateinit var inventory: ConnectedAppsInventory
    private lateinit var history: ConnectedNotificationHistoryStore
    private lateinit var preferences: HubPreferences
    private val workerScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        )
    private var refreshGeneration by mutableIntStateOf(0)
    private var focusedKey by mutableStateOf<ConnectedAppKey?>(null)
    private val attentionProfile by lazy { DeviceAttention.profile(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ConnectedAppsRepository(applicationContext)
        inventory = ConnectedAppsInventory(applicationContext)
        history = ConnectedNotificationHistoryStore(applicationContext)
        preferences = HubPreferences(applicationContext)
        focusedKey = focusedKeyFrom(intent)

        setContent {
            var candidates by remember {
                mutableStateOf<List<ConnectedAppCandidate>>(emptyList())
            }
            var policies by remember {
                mutableStateOf<Map<ConnectedAppKey, ConnectedAppPolicy>>(emptyMap())
            }
            var loading by remember {
                mutableStateOf(true)
            }
            var notificationAccessGranted by remember {
                mutableStateOf(false)
            }
            var attention by remember {
                mutableStateOf(AttentionUi(emptyList(), emptyMap()))
            }
            var privateMode by remember {
                mutableStateOf(false)
            }

            LaunchedEffect(refreshGeneration) {
                loading = true
                val loaded =
                    withContext(Dispatchers.IO) {
                        inventory.loadCandidates() to repository.loadPolicies()
                    }
                candidates = loaded.first
                policies =
                    loaded.second.associateBy { policy ->
                        policy.key
                    }
                val stored = withContext(Dispatchers.IO) { preferences.loadAttentionSelections() }
                attention =
                    AttentionUi(
                        selectableOutputs = attentionProfile.selectableOutputs(),
                        selections =
                            candidates.associate { candidate ->
                                candidate.key to
                                    AttentionSelections.effectiveFor(candidate.key, stored, attentionProfile)
                            },
                    )
                privateMode = preferences.privateMode()
                notificationAccessGranted = hasNotificationAccess()
                loading = false
            }

            SableGlobalTheme(window = window) {
                ConnectedAppsScreen(
                    candidates = candidates,
                    policies = policies,
                    notificationAccessGranted = notificationAccessGranted,
                    loading = loading,
                    onRefresh = {
                        refreshGeneration += 1
                    },
                    onOpenNotificationAccess = ::openNotificationAccess,
                    attention = attention,
                    focusedKey = focusedKey,
                    privateMode = privateMode,
                    onPrivateModeChanged = { enabled ->
                        preferences.setPrivateMode(enabled)
                        refreshGeneration += 1
                    },
                    onAttentionChanged = ::updateAttention,
                    onOpenAndroidNotificationSettings = ::openAndroidNotificationSettings,
                    onOpenAttentionSettings = {
                        AndroidSettingsLauncher(this@ConnectedAppsActivity).open(SettingsRoute.SableAttention)
                    },
                    onPolicyChanged = { policy ->
                        val normalized = policy.normalized()
                        repository.update(normalized)
                        if (!normalized.includeInMessages) {
                            workerScope.launch {
                                history.clearFor(normalized.key)
                            }
                        }
                        if (!normalized.allowQuickReply) {
                            ConnectedReplyRegistry.removeFor(normalized.key)
                        }
                        refreshGeneration += 1
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focusedKey = focusedKeyFrom(intent)
        refreshGeneration += 1
    }

    /** Package + uid from Settings (or Hub's own handoff) select one app in its own profile. */
    private fun focusedKeyFrom(intent: Intent?): ConnectedAppKey? {
        val packageName = intent?.getStringExtra(HubIntents.EXTRA_APP_PACKAGE) ?: return null
        val uid = intent.getIntExtra(HubIntents.EXTRA_APP_UID, -1)
        return if (uid >= 0) {
            inventory.keyForUid(packageName, uid)
        } else {
            inventory.keyForUid(packageName, android.os.Process.myUid())
        }
    }

    private fun updateAttention(
        key: ConnectedAppKey,
        outputs: Set<AttentionOutput>,
    ) {
        // Only profile-supported Sable outputs can be stored; this never touches importance.
        val allowed = outputs.intersect(attentionProfile.selectableOutputs().toSet())
        workerScope.launch {
            preferences.updateAttention(AttentionSelection(key, allowed))
        }
        refreshGeneration += 1
    }

    private fun openAndroidNotificationSettings(key: ConnectedAppKey) {
        AndroidSettingsLauncher(this).open(
            AndroidSettingsRoutes.routeFor(
                NotificationConcept.DeliveryImportance,
                SettingsTarget(packageName = key.packageName, appUid = inventory.uidFor(key)),
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            refreshGeneration += 1
        }
    }

    override fun onDestroy() {
        workerScope.cancel()
        super.onDestroy()
    }

    private fun hasNotificationAccess(): Boolean {
        val manager = checkNotNull(getSystemService(NotificationManager::class.java))
        return manager.isNotificationListenerAccessGranted(listenerComponent())
    }

    /** Notification access is Android's: open Hub's own listener page (list page as fallback). */
    private fun openNotificationAccess() {
        AndroidSettingsLauncher(this).open(
            AndroidSettingsRoutes.routeFor(
                NotificationConcept.NotificationAccess,
                SettingsTarget(listenerComponent = listenerComponent().flattenToString()),
            ),
        )
    }

    private fun listenerComponent(): ComponentName = ComponentName(this, SableNotificationListenerService::class.java)
}
