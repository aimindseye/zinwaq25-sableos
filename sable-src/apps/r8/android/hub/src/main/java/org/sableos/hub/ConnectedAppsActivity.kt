package org.sableos.hub

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import org.sableos.hub.ui.ConnectedAppsScreen

class ConnectedAppsActivity : ComponentActivity() {
    private lateinit var repository: ConnectedAppsRepository
    private lateinit var inventory: ConnectedAppsInventory
    private lateinit var history: ConnectedNotificationHistoryStore
    private val workerScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        )
    private var refreshGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ConnectedAppsRepository(applicationContext)
        inventory = ConnectedAppsInventory(applicationContext)
        history = ConnectedNotificationHistoryStore(applicationContext)

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

    private fun openNotificationAccess() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun listenerComponent(): ComponentName =
        ComponentName(
            packageName,
            NOTIFICATION_LISTENER_CLASS,
        )

    private companion object {
        const val NOTIFICATION_LISTENER_CLASS =
            "org.sableos.hub.notifications.SableNotificationListenerService"
    }
}
