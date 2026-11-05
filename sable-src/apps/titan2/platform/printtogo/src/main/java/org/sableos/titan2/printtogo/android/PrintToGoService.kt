package org.sableos.titan2.printtogo.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.net.Inet4Address
import java.net.InetAddress
import java.util.UUID
import org.sableos.titan2.printtogo.R
import org.sableos.titan2.printtogo.android.PrintToGoState.Phase
import org.sableos.titan2.printtogo.core.IppPrinter
import org.sableos.titan2.printtogo.core.PrintToGo
import org.sableos.titan2.printtogo.core.PrintToGoText.t
import org.sableos.titan2.printtogo.core.PrinterInfo

/**
 * Print To Go while it is on: a foreground service (its notification says so and has Stop) that runs the IPP
 * server on the Wi-Fi address, announces it over DNS-SD, saves each print and turns itself off after
 * [PrintToGo.IDLE_MS] without one.
 */
class PrintToGoService : Service() {
    private var server: PrintServer? = null
    private var announcement: NsdManager.RegistrationListener? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastActivity = 0L
    private val started = SystemClock.elapsedRealtime()

    private val idleCheck =
        object : Runnable {
            override fun run() {
                if (PrintToGo.remaining(lastActivity, SystemClock.elapsedRealtime()) == 0L) {
                    shutDown(Phase.IdleOff)
                } else {
                    main.postDelayed(this, IDLE_CHECK_MS)
                }
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown(Phase.Off)
        } else if (server == null) {
            begin()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (server != null) shutDown(Phase.Off)
        super.onDestroy()
    }

    private fun begin() {
        channels()
        val wifi = wifi()
        val name = "Sable Print To Go (${Build.MODEL})"
        startForeground(RUNNING_ID, running(name), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (wifi == null) {
            PrintToGoState.update { it.copy(phase = Phase.NoWifi) }
            stopNow()
            return
        }
        val (network, address) = wifi
        val uri = "ipp://${address.hostAddress}:${PrintToGo.PORT}${PrintToGo.PATH}"
        val printer =
            IppPrinter(PrinterInfo(name, uri, uuid())) {
                ((SystemClock.elapsedRealtime() - started) / MS_PER_SECOND).toInt().coerceAtLeast(1)
            }
        val saver = DocumentSaver(this)
        val next =
            PrintServer(address, printer) { job, format, body ->
                main.post { lastActivity = SystemClock.elapsedRealtime() }
                val saved = saver.save(job, format, body)
                main.post { lastActivity = SystemClock.elapsedRealtime() }
                if (saved != null) {
                    notifySaved(saved)
                    PrintToGoState.update { it.copy(last = t("saved", saved.name)) }
                } else {
                    PrintToGoState.update { it.copy(last = t("save_failed", job.name)) }
                }
                saved?.name
            }
        try {
            next.start()
        } catch (_: Exception) {
            PrintToGoState.update { it.copy(phase = Phase.Failed) }
            stopNow()
            return
        }
        server = next
        announce(name, network)
        lastActivity = SystemClock.elapsedRealtime()
        main.postDelayed(idleCheck, IDLE_CHECK_MS)
        PrintToGoState.update { PrintToGoState.Snapshot(Phase.Ready, name, it.last) }
    }

    private fun shutDown(phase: Phase) {
        main.removeCallbacks(idleCheck)
        announcement?.let {
            runCatching { getSystemService(NsdManager::class.java).unregisterService(it) }
        }
        announcement = null
        server?.stop()
        server = null
        PrintToGoState.update { it.copy(phase = phase) }
        stopNow()
    }

    private fun stopNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The Wi-Fi network and its IPv4 address; null when the phone is not on Wi-Fi. */
    private fun wifi(): Pair<Network, InetAddress>? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val address =
            cm.getLinkProperties(network)?.linkAddresses?.map {
                it.address
            }?.firstOrNull { it is Inet4Address }
                ?: return null
        return network to address
    }

    private fun announce(name: String, network: Network) {
        val info =
            NsdServiceInfo().apply {
                serviceName = name
                serviceType = PrintToGo.SERVICE_TYPE
                port = PrintToGo.PORT
                setNetwork(network)
                subtypes = setOf(PrintToGo.AIRPRINT_SUBTYPE)
                PrintToGo.txtRecord(name, uuid()).forEach { (key, value) ->
                    setAttribute(key, value)
                }
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit

                override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

                override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
            }
        runCatching {
            getSystemService(
                NsdManager::class.java
            ).registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            announcement = listener
        }
    }

    /** One id per phone, so computers keep recognising the printer between sessions. */
    private fun uuid(): String {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_UUID, null)
        if (saved != null) return saved
        val made = "urn:uuid:${UUID.randomUUID()}"
        prefs.edit().putString(KEY_UUID, made).apply()
        return made
    }

    private fun channels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUNNING,
                t("channel_running"),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SAVED,
                t("channel_saved"),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun running(name: String): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, PrintToGoActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, PrintToGoService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE
            )
        return Notification.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_sable_printtogo_monochrome)
            .setContentTitle(t("running"))
            .setContentText(name)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, t("stop"), stop).build())
            .build()
    }

    private fun notifySaved(saved: DocumentSaver.Saved) {
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(saved.uri, IppPrinter.PDF)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(
            this,
            saved.uri.hashCode(),
            view,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification =
            Notification.Builder(this, CHANNEL_SAVED)
                .setSmallIcon(R.drawable.ic_sable_printtogo_monochrome)
                .setContentTitle(t("saved", saved.name))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        runCatching {
            getSystemService(
                NotificationManager::class.java
            ).notify(saved.uri.hashCode(), notification)
        }
    }

    companion object {
        const val ACTION_STOP = "org.sableos.printtogo.action.STOP"
        private const val CHANNEL_RUNNING = "running"
        private const val CHANNEL_SAVED = "saved"
        private const val RUNNING_ID = 1
        private const val IDLE_CHECK_MS = 30_000L
        private const val MS_PER_SECOND = 1000
        private const val PREFS = "print_to_go"
        private const val KEY_UUID = "uuid"
    }
}
