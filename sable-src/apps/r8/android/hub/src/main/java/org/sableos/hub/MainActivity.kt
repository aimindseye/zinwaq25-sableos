package org.sableos.hub

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.design.SableGlobalTheme
import org.sableos.hub.policy.HubHandoffTarget
import org.sableos.hub.policy.HubIntents
import org.sableos.hub.ui.HubScreen

class MainActivity : ComponentActivity() {
    private lateinit var repository: HubRepository
    private var refreshGeneration by mutableIntStateOf(0)
    private var pendingHandoff by mutableStateOf<Intent?>(null)
    private var pendingThreadId by mutableStateOf<Long?>(null)

    private val connectedHistoryObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshGeneration += 1
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = HubRepository(applicationContext)
        pendingHandoff = intent.takeIf { it.action == HubIntents.ACTION_OPEN_NOTIFICATION }
        contentResolver.registerContentObserver(
            ConnectedAppsRepository.HISTORY_URI,
            false,
            connectedHistoryObserver,
        )

        setContent {
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    refreshGeneration += 1
                }

            var snapshot by remember {
                mutableStateOf<HubSnapshot?>(null)
            }
            var refreshing by remember {
                mutableStateOf(false)
            }

            LaunchedEffect(refreshGeneration) {
                refreshing = true
                snapshot =
                    withContext(Dispatchers.IO) {
                        repository.snapshot()
                    }
                refreshing = false
            }

            // Shade "H": Hub decides eligibility; it never includes a source silently.
            LaunchedEffect(snapshot, pendingHandoff) {
                val loaded = snapshot
                val handoff = pendingHandoff
                if (loaded != null && handoff != null) {
                    pendingHandoff = null
                    handleHandoff(handoff, loaded)
                }
            }

            SableGlobalTheme(window = window) {
                HubScreen(
                    snapshot = snapshot,
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshGeneration += 1
                    },
                    onRequestAccess = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.READ_CONTACTS,
                            ),
                        )
                    },
                    onSendSms = { recipient, body ->
                        runCatching {
                            repository.sendSms(
                                recipient = recipient,
                                body = body,
                            )
                        }.fold(
                            onSuccess = {
                                refreshGeneration += 1
                                HubSendResult(
                                    success = true,
                                    message = "Message handed to Android telephony.",
                                )
                            },
                            onFailure = { error ->
                                HubSendResult(
                                    success = false,
                                    message =
                                        error.message
                                            ?: "SMS send capability is unavailable.",
                                )
                            },
                        )
                    },
                    onOpenAndroidMessaging = { recipient, body ->
                        repository.openAndroidMessaging(
                            recipient = recipient,
                            body = body,
                        )
                    },
                    onOpenSableMail = {
                        repository.openSableMail()
                    },
                    onOpenConnectedApps = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                ConnectedAppsActivity::class.java,
                            ),
                        )
                    },
                    onReplyConnected = { notificationKey, body ->
                        repository.replyToConnected(
                            notificationKey = notificationKey,
                            body = body,
                        )
                    },
                    onOpenConnectedApp = { packageName, userSerial ->
                        repository.openConnectedApp(
                            packageName = packageName,
                            userSerial = userSerial,
                        )
                    },
                    onOpenSourceNotificationSettings = { packageName, userSerial ->
                        repository.openSourceNotificationSettings(
                            packageName = packageName,
                            userSerial = userSerial,
                        )
                    },
                    pendingThreadId = pendingThreadId,
                    onPendingThreadConsumed = {
                        pendingThreadId = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == HubIntents.ACTION_OPEN_NOTIFICATION) {
            pendingHandoff = intent
            refreshGeneration += 1
        }
    }

    private suspend fun handleHandoff(
        handoff: Intent,
        snapshot: HubSnapshot,
    ) {
        val packageName = handoff.getStringExtra(HubIntents.EXTRA_APP_PACKAGE)
        val uid = handoff.getIntExtra(HubIntents.EXTRA_APP_UID, -1).takeIf { it >= 0 }
        val target =
            withContext(Dispatchers.IO) {
                repository.handoffTarget(
                    packageName = packageName,
                    uid = uid,
                    notificationKey = handoff.getStringExtra(HubIntents.EXTRA_NOTIFICATION_KEY),
                    snapshot = snapshot,
                )
            }
        when (target) {
            is HubHandoffTarget.Conversation -> {
                pendingThreadId = target.threadId
            }

            is HubHandoffTarget.ConnectedAppSettings -> {
                startActivity(
                    Intent(this, ConnectedAppsActivity::class.java)
                        .putExtra(HubIntents.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(HubIntents.EXTRA_APP_UID, uid ?: -1),
                )
            }

            HubHandoffTarget.Home -> {
                Unit
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
        runCatching {
            contentResolver.unregisterContentObserver(connectedHistoryObserver)
        }
        super.onDestroy()
    }
}
