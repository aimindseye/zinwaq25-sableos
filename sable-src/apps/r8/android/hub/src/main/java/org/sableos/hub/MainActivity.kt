package org.sableos.hub

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.design.SableGlobalTheme
import org.sableos.hub.policy.HubDrafts
import org.sableos.hub.policy.HubHandoffTarget
import org.sableos.hub.policy.HubIntents
import org.sableos.hub.ui.HubScreen
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var repository: HubRepository
    private lateinit var draftStore: HubDraftStore
    private val drafts = mutableStateMapOf<String, String>()
    private var refreshGeneration by mutableIntStateOf(0)

    // Only a user pull shows the refresh indicator; provider changes reload silently.
    private var userRefreshGeneration = 0
    private var pendingHandoff by mutableStateOf<Intent?>(null)
    private var pendingThreadId by mutableStateOf<Long?>(null)
    private var pendingQuery by mutableStateOf<HubLaunchQuery?>(null)
    private var pendingAddress by mutableStateOf<String?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val providerRefresh = Runnable { refreshGeneration += 1 }
    private var providerObserversRegistered = false
    private var launchQuerySequence = 0

    /** Dock icons by source, loaded once per activity (main-thread access only). */
    private val appIcons = mutableMapOf<ConnectedAppKey, ImageBitmap?>()
    private val inventory by lazy { ConnectedAppsInventory(applicationContext) }

    private val connectedHistoryObserver =
        object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshGeneration += 1
            }
        }

    /**
     * SMS/MMS and call-log changes (incoming, sent, read state) refresh the open thread and the
     * list while Hub is visible. Bursts (multipart SMS, thread updates) collapse into one reload.
     */
    private val providerObserver =
        object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                mainHandler.removeCallbacks(providerRefresh)
                mainHandler.postDelayed(providerRefresh, PROVIDER_REFRESH_DELAY_MILLIS)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = HubRepository(applicationContext)
        draftStore = HubDraftStore(applicationContext)
        drafts.putAll(draftStore.load())
        pendingHandoff = intent.takeIf { it.action == HubIntents.ACTION_OPEN_NOTIFICATION }
        // A recreated activity keeps its own route; only a fresh launch applies the query.
        if (savedInstanceState == null) {
            pendingQuery = launchQueryFrom(intent)
            pendingAddress = conversationAddressFrom(intent)
        }
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
                    // Newly granted SMS/call-log access: observe the providers it opens.
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        registerProviderObservers()
                    }
                    refreshGeneration += 1
                }

            var snapshot by remember {
                mutableStateOf<HubSnapshot?>(null)
            }
            var refreshing by remember {
                mutableStateOf(false)
            }
            var combineConversations by remember {
                mutableStateOf(true)
            }

            LaunchedEffect(refreshGeneration) {
                refreshing = refreshGeneration == userRefreshGeneration
                snapshot =
                    withContext(Dispatchers.IO) {
                        repository.snapshot()
                    }
                combineConversations =
                    withContext(Dispatchers.IO) {
                        HubPreferences(applicationContext).combineConversations()
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
                        userRefreshGeneration = refreshGeneration
                    },
                    onRequestAccess = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_CALL_LOG,
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
                    onDial = { number ->
                        repository.dial(number)
                    },
                    drafts = drafts,
                    onDraftChange = { key, body ->
                        val updated = HubDrafts.update(drafts.toMap(), key, body)
                        drafts.keys.retainAll(updated.keys)
                        drafts.putAll(updated)
                        draftStore.save(updated)
                    },
                    launchQuery = pendingQuery,
                    onLaunchQueryConsumed = {
                        pendingQuery = null
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
                    pendingAddress = pendingAddress,
                    onPendingAddressConsumed = {
                        pendingAddress = null
                    },
                    combineConversations = combineConversations,
                    loadAppIcon = ::loadAppIcon,
                )
            }
        }
    }

    private suspend fun loadAppIcon(
        packageName: String,
        userSerial: Long,
    ): ImageBitmap? {
        val key = ConnectedAppKey(packageName, userSerial)
        if (key in appIcons) return appIcons[key]
        val sizePx = (DOCK_ICON_SIZE_DP * resources.displayMetrics.density).roundToInt()
        val icon =
            withContext(Dispatchers.IO) {
                inventory.iconFor(key, sizePx)?.asImageBitmap()
            }
        appIcons[key] = icon
        return icon
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == HubIntents.ACTION_OPEN_NOTIFICATION) {
            pendingHandoff = intent
            refreshGeneration += 1
        }
        launchQueryFrom(intent)?.let { query -> pendingQuery = query }
        conversationAddressFrom(intent)?.let { address -> pendingAddress = address }
    }

    /** Command's search text and/or Start's filter (ACTION_MAIN extras); null for a plain launch. */
    private fun launchQueryFrom(intent: Intent?): HubLaunchQuery? {
        val launch = intent?.takeIf { it.action == Intent.ACTION_MAIN } ?: return null
        val query = HubIntents.launchQuery(launch.getStringExtra(HubIntents.EXTRA_QUERY))
        val filter = HubIntents.launchFilter(launch.getStringExtra(HubIntents.EXTRA_FILTER))
        if (query == null && filter == null) return null
        launchQuerySequence += 1
        return HubLaunchQuery(query.orEmpty(), launchQuerySequence, filter)
    }

    /** Contacts' Hub route chip: the address whose conversation to open. */
    private fun conversationAddressFrom(intent: Intent?): String? =
        intent
            ?.takeIf { it.action == HubIntents.ACTION_OPEN_CONVERSATION }
            ?.let { HubIntents.launchAddress(it.getStringExtra(HubIntents.EXTRA_ADDRESS)) }

    override fun onStart() {
        super.onStart()
        registerProviderObservers()
    }

    override fun onStop() {
        unregisterProviderObservers()
        super.onStop()
    }

    private fun registerProviderObservers() {
        unregisterProviderObservers()
        // Without READ_SMS / READ_CALL_LOG the provider refuses the observer; resume refresh covers it.
        PROVIDER_URIS.forEach { uri ->
            runCatching {
                contentResolver.registerContentObserver(uri, true, providerObserver)
                providerObserversRegistered = true
            }
        }
    }

    private fun unregisterProviderObservers() {
        mainHandler.removeCallbacks(providerRefresh)
        if (providerObserversRegistered) {
            runCatching { contentResolver.unregisterContentObserver(providerObserver) }
            providerObserversRegistered = false
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

    private companion object {
        const val PROVIDER_REFRESH_DELAY_MILLIS = 250L
        const val DOCK_ICON_SIZE_DP = 40f
        val PROVIDER_URIS =
            listOf(
                Telephony.Sms.CONTENT_URI,
                Telephony.Mms.CONTENT_URI,
                Telephony.MmsSms.CONTENT_URI,
                CallLog.Calls.CONTENT_URI,
            )
    }
}
