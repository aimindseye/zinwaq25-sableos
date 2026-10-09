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
import org.sableos.hub.ui.HubScreen

class MainActivity : ComponentActivity() {
    private lateinit var repository: HubRepository
    private var refreshGeneration by mutableIntStateOf(0)

    private val connectedHistoryObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshGeneration += 1
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = HubRepository(applicationContext)
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
        runCatching {
            contentResolver.unregisterContentObserver(connectedHistoryObserver)
        }
        super.onDestroy()
    }
}
