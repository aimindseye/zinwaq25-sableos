package org.sableos.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/**
 * Drives the FM tuner through the FM bridge in the LineageOS FM Radio app (SableOS framework patch
 * packages/apps/FMRadio 1062). The MediaTek tuner (/dev/fm, libfmjni) is only reachable from that
 * platform-signed app, so Sable Media sends it commands over a Messenger and shows the state it reports.
 *
 * FM_HAL_OR_VENDOR_SERVICE_GATE: without the bridge, [FmUiState.available] is false and the FM mode
 * is not offered.
 */
internal class FmController(
    private val context: Context,
    private val onState: (FmUiState) -> Unit,
) {
    private val handler =
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == FmBridgeProtocol.MSG_STATE) {
                publish(parse(message.data))
                true
            } else {
                false
            }
        }
    private val replyTo = Messenger(handler)
    private var bridge: Messenger? = null
    private var bound = false

    var state = FmUiState(available = isAvailable(context))
        private set

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                bridge = service?.let(::Messenger)
                publish(state.copy(connected = bridge != null))
                send(FmBridgeProtocol.MSG_REGISTER)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bridge = null
                publish(state.copy(connected = false, powered = false, scanning = false, seeking = false))
            }
        }

    /** Binds the bridge; called when the FM screen is first opened. */
    fun connect() {
        if (bound || !state.available) return
        bound = context.bindService(bridgeIntent(), connection, Context.BIND_AUTO_CREATE)
        if (!bound) publish(state.copy(available = false))
    }

    /** Unbinds; the radio keeps playing with its notification until it is switched off. */
    fun disconnect() {
        if (!bound) return
        send(FmBridgeProtocol.MSG_UNREGISTER)
        context.unbindService(connection)
        bound = false
        bridge = null
    }

    fun togglePower(favorites: Set<Int>) {
        if (state.powered) {
            send(FmBridgeProtocol.MSG_POWER_DOWN)
        } else {
            val start = state.station.takeIf(FmBand::isValid) ?: FmPresets.ordered(favorites).firstOrNull() ?: 0
            send(FmBridgeProtocol.MSG_POWER_UP, start)
        }
    }

    fun tune(station: Int) {
        if (FmBand.isValid(station)) send(FmBridgeProtocol.MSG_TUNE, station)
    }

    fun step(up: Boolean) = tune(FmBand.step(state.station, up))

    fun seek(up: Boolean) = send(FmBridgeProtocol.MSG_SEEK, if (up) 1 else 0)

    fun toggleScan() = send(if (state.scanning) FmBridgeProtocol.MSG_STOP_SCAN else FmBridgeProtocol.MSG_SCAN)

    fun toggleSpeaker() = send(FmBridgeProtocol.MSG_SET_SPEAKER, if (state.speaker) 0 else 1)

    fun toggleMute() = send(FmBridgeProtocol.MSG_SET_MUTE, if (state.muted) 0 else 1)

    private fun send(
        what: Int,
        arg1: Int = 0,
    ) {
        val target = bridge ?: return
        val message = Message.obtain(null, what, arg1, 0)
        message.replyTo = replyTo
        // RemoteException: the FM Radio process died; onServiceDisconnected follows.
        runCatching { target.send(message) }.onFailure {
            bridge = null
            publish(state.copy(connected = false, powered = false))
        }
    }

    private fun publish(next: FmUiState) {
        state = next
        onState(next)
    }

    private fun parse(data: Bundle): FmUiState {
        val stations = data.getIntArray(FmBridgeProtocol.KEY_STATIONS) ?: IntArray(0)
        val names = data.getStringArray(FmBridgeProtocol.KEY_STATION_NAMES) ?: emptyArray()
        return state.copy(
            connected = true,
            powered = data.getBoolean(FmBridgeProtocol.KEY_POWERED, state.powered),
            station = data.getInt(FmBridgeProtocol.KEY_STATION, state.station),
            antenna = data.getBoolean(FmBridgeProtocol.KEY_ANTENNA, state.antenna),
            speaker = data.getBoolean(FmBridgeProtocol.KEY_SPEAKER, state.speaker),
            muted = data.getBoolean(FmBridgeProtocol.KEY_MUTED, state.muted),
            scanning = data.getBoolean(FmBridgeProtocol.KEY_SCANNING, false),
            seeking = data.getBoolean(FmBridgeProtocol.KEY_SEEKING, false),
            deviceOpen = data.getBoolean(FmBridgeProtocol.KEY_DEVICE_OPEN, state.deviceOpen),
            programService = data.getString(FmBridgeProtocol.KEY_PS).orEmpty(),
            radioText = data.getString(FmBridgeProtocol.KEY_RT).orEmpty(),
            stations = stations.mapIndexed { index, station -> FmStation(station, names.getOrNull(index).orEmpty()) },
            lastOk = data.getBoolean(FmBridgeProtocol.KEY_OK, true),
            lastEvent = data.getInt(FmBridgeProtocol.KEY_EVENT, 0),
        )
    }

    companion object {
        fun bridgeIntent(): Intent = Intent(FmBridgeProtocol.ACTION_CONTROL).setPackage(FmBridgeProtocol.BRIDGE_PACKAGE)

        /** The FM bridge is installed (the SableOS FM Radio app, on devices whose tree builds it). */
        fun isAvailable(context: Context): Boolean {
            val pm = context.packageManager
            val matches =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentServices(bridgeIntent(), PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentServices(bridgeIntent(), 0)
                }
            return matches.isNotEmpty()
        }
    }
}

/** Starred FM stations, kept with the other Sable Media preferences. */
internal class FmPresetStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("sable_media", Context.MODE_PRIVATE)

    fun load(): Set<Int> =
        preferences
            .getStringSet(KEY_FAVORITES, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .filter(FmBand::isValid)
            .toSet()

    fun save(favorites: Set<Int>) {
        preferences.edit().putStringSet(KEY_FAVORITES, favorites.map(Int::toString).toSet()).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "fm_favorites"
    }
}
