package org.sableos.phone.android

import android.content.Intent
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Sable Phone's InCallService: Telecom binds it while there is a call because Sable Phone holds the Phone role.
 * It keeps [CallBook], shows the incoming call as a heads-up (full screen when the phone is locked or asleep),
 * opens the call screen for outgoing calls, and turns the screen off near the ear with the proximity sensor.
 */
class PhoneInCallService :
    InCallService(),
    CallBook.Listener {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        proximity = getSystemService(PowerManager::class.java)
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "SablePhone:proximity")
            ?.apply { setReferenceCounted(false) }
        CallBook.attach(this)
        CallBook.listen(this)
    }

    override fun onDestroy() {
        CallBook.unlisten(this)
        CallBook.attach(null)
        proximity?.release()
        CallNotifications.cancelCall(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallBook.add(call)
        if (call.details.state != Call.STATE_RINGING) openCallScreen(false)
    }

    override fun onCallRemoved(call: Call) {
        CallBook.remove(call)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallBook.setAudio(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) = openCallScreen(showDialpad)

    override fun onCallsChanged() {
        CallNotifications.update(this)
        updateProximity()
    }

    private fun openCallScreen(keypad: Boolean) {
        startActivity(
            Intent(this, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(InCallActivity.EXTRA_KEYPAD, keypad)
        )
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
        val AT_EAR_STATES = setOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE)
    }
}
