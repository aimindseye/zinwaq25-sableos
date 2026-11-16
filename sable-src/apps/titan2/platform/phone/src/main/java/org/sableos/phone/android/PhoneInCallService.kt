package org.sableos.phone.android

import android.content.Intent
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

/**
 * Sable Phone's InCallService: Telecom binds it while there is a call because Sable Phone holds the Phone role.
 * It keeps [CallBook], shows the incoming call as a heads-up (full screen when the phone is locked or asleep),
 * opens the call screen for outgoing calls, and turns the screen off near the ear with the proximity sensor.
 */
open class PhoneInCallService :
    InCallService(),
    CallBook.Listener {
    private var proximity: PowerManager.WakeLock? = null
    private val ringer by lazy { Ringer(this) }

    /** False for a second service of Sable Phone's started alongside the first: it ignores everything. */
    private var owner = false

    override fun onCreate() {
        super.onCreate()
        proximity = getSystemService(PowerManager::class.java)
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "SablePhone:proximity")
            ?.apply { setReferenceCounted(false) }
        owner = CallBook.service == null
        if (!owner) return
        CallBook.attach(this)
        CallBook.listen(this)
    }

    override fun onDestroy() {
        if (owner) {
            CallBook.unlisten(this)
            CallBook.attach(null)
            proximity?.release()
            ringer.release()
            CallNotifications.cancelCall(this)
        }
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        if (!owner) return
        CallBook.add(call)
        // Every call opens the call screen, a ringing one too (it also has the full-screen notification for a
        // locked or sleeping phone); the notification alone left an unlocked phone showing no call screen.
        openCallScreen(false)
    }

    override fun onCallRemoved(call: Call) {
        if (!owner) return
        CallBook.remove(call)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        if (!owner) return
        CallBook.setAudio(audioState)
    }

    /** The Silence button (or the volume key): stop ringing, the call keeps ringing silently. */
    override fun onSilenceRinger() {
        if (owner) ringer.silence()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        if (owner) openCallScreen(showDialpad)
    }

    // A notification or proximity failure must never take the service down: Telecom would unbind it and the
    // call would have no screen at all.
    @Suppress("TooGenericExceptionCaught")
    override fun onCallsChanged() {
        if (CallBook.ringing() != null) ringer.start() else ringer.reset()
        try {
            CallNotifications.update(this)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Call notification failed", e)
        }
        try {
            updateProximity()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Proximity lock failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun openCallScreen(keypad: Boolean) {
        try {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(InCallActivity.EXTRA_KEYPAD, keypad)
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "Cannot open the call screen", e)
        }
    }

    /** The screen goes dark at the ear only on the earpiece or a wired headset, never on speaker or Bluetooth. */
    private fun updateProximity() {
        val lock = proximity ?: return
        val route = CallBook.audio?.route ?: CallAudioState.ROUTE_EARPIECE
        val atEar =
            route == CallAudioState.ROUTE_EARPIECE || route == CallAudioState.ROUTE_WIRED_HEADSET
        val live = CallBook.topLevel().any { it.details.state in AT_EAR_STATES }
        if (live && atEar) {
            if (!lock.isHeld) lock.acquire()
        } else if (lock.isHeld) {
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    private companion object {
        const val TAG = "SablePhoneInCall"
        val AT_EAR_STATES = setOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE)
    }
}
