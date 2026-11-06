package org.sableos.phone.android

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile

/**
 * The calls Telecom has given Sable Phone, kept by [PhoneInCallService] while Telecom has it bound, and read by
 * the call screen and the notifications. Main thread only: Telecom calls back and the UI reads on the main looper.
 */
object CallBook {
    fun interface Listener {
        fun onCallsChanged()
    }

    private val listeners = linkedSetOf<Listener>()
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()

        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = changed()

        override fun onConferenceableCallsChanged(
            call: Call,
            conferenceableCalls: MutableList<Call>
        ) = changed()
    }

    var service: InCallService? = null
        private set
    val calls = mutableListOf<Call>()
    var audio: CallAudioState? = null
        private set

    fun attach(s: InCallService?) {
        service = s
        if (s == null) {
            calls.toList().forEach { it.unregisterCallback(callCallback) }
            calls.clear()
            audio = null
        }
        changed()
    }

    fun add(call: Call) {
        call.registerCallback(callCallback)
        calls += call
        changed()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callCallback)
        calls -= call
        changed()
    }

    fun setAudio(state: CallAudioState?) {
        audio = state
        changed()
    }

    fun listen(l: Listener) {
        listeners += l
    }

    fun unlisten(l: Listener) {
        listeners -= l
    }

    private fun changed() = listeners.toList().forEach { it.onCallsChanged() }

    /** Top-level calls (a conference's members are shown inside it). */
    fun topLevel(): List<Call> = calls.filter { it.parent == null }

    fun ringing(): Call? = topLevel().firstOrNull { it.details.state == Call.STATE_RINGING }

    /** The call the screen is about: ringing first, then dialling or active, then on hold. */
    fun primary(): Call? = topLevel().minByOrNull { order(it.details.state) }

    fun other(primary: Call?): Call? =
        topLevel().firstOrNull { it != primary && it.details.state != Call.STATE_DISCONNECTED }

    private fun order(state: Int): Int = ORDER[state] ?: ORDER_LAST

    private const val ORDER_LAST = 6
    private val ORDER = mapOf(
        Call.STATE_RINGING to 0,
        Call.STATE_SIMULATED_RINGING to 0,
        Call.STATE_SELECT_PHONE_ACCOUNT to 1,
        Call.STATE_DIALING to 2,
        Call.STATE_CONNECTING to 2,
        Call.STATE_PULLING_CALL to 2,
        Call.STATE_NEW to 2,
        Call.STATE_ACTIVE to 3,
        Call.STATE_HOLDING to 4,
        Call.STATE_AUDIO_PROCESSING to 5
    )
}

/** What the call screen, the notification buttons and the keys do to calls and to the call audio. */
object CallControl {
    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun decline(call: Call) = call.reject(Call.REJECT_REASON_DECLINED)

    fun hangUp(call: Call) = call.disconnect()

    fun toggleHold(call: Call) {
        if (call.details.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun chooseAccount(call: Call, account: PhoneAccountHandle) =
        call.phoneAccountSelected(account, false)

    // setMuted and setAudioRoute stay the simple way to switch an audio-only call.
    @Suppress("DEPRECATION")
    fun toggleMute() {
        CallBook.service?.setMuted(CallBook.audio?.isMuted != true)
    }

    /** Speaker on or off. With Bluetooth among the routes the call screen offers the routes instead. */
    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val onSpeaker = CallBook.audio?.route == CallAudioState.ROUTE_SPEAKER
        CallBook.service?.setAudioRoute(
            if (onSpeaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER
        )
    }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) {
        CallBook.service?.setAudioRoute(route)
    }

    fun hasBluetooth(): Boolean =
        (CallBook.audio?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0
}
