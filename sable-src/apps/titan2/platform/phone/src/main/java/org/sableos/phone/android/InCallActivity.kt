package org.sableos.phone.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountSuggestion
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.phone.core.CallFormat
import org.sableos.phone.core.DialKeys
import org.sableos.phone.core.InCallKeys
import org.sableos.phone.core.PhoneText.t
import org.sableos.titan2.theme.SablePalette

/**
 * The call screen, BlackBerry 10 style: who and how long at the top, then either Answer and Decline for a ringing
 * call or the in-call controls (mute, speaker, keypad, hold, add, merge or swap) over a red End button. Shown over
 * the lock screen. Keys: Call or Space answers, End declines or hangs up, M S H D toggle mute, speaker, hold and
 * the keypad; with the keypad open every dial key sends its tone.
 */
class InCallActivity :
    Activity(),
    CallBook.Listener {
    private lateinit var ui: Ui
    private lateinit var palette: SablePalette
    private lateinit var state: TextView
    private lateinit var name: TextView
    private lateinit var number: TextView
    private lateinit var timer: TextView
    private lateinit var second: TextView
    private lateinit var ringing: View
    private lateinit var controls: View
    private lateinit var accounts: LinearLayout
    private lateinit var keypad: View
    private lateinit var tones: TextView
    private lateinit var hint: TextView
    private lateinit var mute: TextView
    private lateinit var speaker: TextView
    private lateinit var hold: TextView
    private lateinit var merge: TextView
    private lateinit var answer: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tick: Runnable =
        Runnable {
            showTimer()
            postTick()
        }
    private var keypadOpen = false
    private var answeredKey = -1
    private var accountsFor: Call? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // The call screen is always dark, like the BlackBerry 10 one, with Sable Blue for the timer and focus.
        palette = SablePalette.of(dark = true, accent = null)
        palette.applyTo(this)
        ui = Ui(this, palette)
        keypadOpen = intent.getBooleanExtra(EXTRA_KEYPAD, false)
        setContentView(build())
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.window)
            setPadding(ui.dp(Ui.PAD), ui.dp(Ui.PAD), ui.dp(Ui.PAD), ui.dp(Ui.PAD))
        }
        state = ui.text(Ui.SMALL_SP, palette.muted).centered().also(root::addView)
        name = ui.text(NAME_SP, bold = true).centered().apply { maxLines = 2 }.also(root::addView)
        number = ui.text(Ui.BODY_SP, palette.muted).centered().also(root::addView)
        timer = ui.text(Ui.BODY_SP, palette.accent).centered().also(root::addView)
        second = ui.text(Ui.SMALL_SP, palette.muted).centered().apply {
            isFocusable = true
            isClickable = true
            background = ui.focusBackground()
            setOnClickListener { swap() }
        }.also(root::addView)
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        accounts =
            LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also(root::addView)
        keypad = keypadView().also(root::addView)
        ringing = ringingView().also(root::addView)
        controls = controlsView().also(root::addView)
        hint = ui.text(Ui.SMALL_SP, palette.muted).centered().also(root::addView)
        return root
    }

    private fun TextView.centered(): TextView = apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun ringingView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val decline = ui.button(t("decline"), Ui.RED, Ui.ON_COLOR) {
            CallBook.ringing()?.let(CallControl::decline)
        }
        addView(decline, weighted())
        answer =
            ui.button(t("answer"), Ui.GREEN, Ui.ON_COLOR) {
                CallBook.ringing()?.let(CallControl::answer)
            }
        addView(answer, weighted())
    }

    private fun controlsView(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val grid = GridLayout(this).apply { columnCount = COLUMNS }
        mute = toggle(t("mute")) { CallControl.toggleMute() }
        speaker = toggle(t("speaker")) { speakerOrRoutes() }
        val pad = toggle(t("keypad")) { setKeypad(!keypadOpen) }
        hold = toggle(t("hold")) { CallBook.primary()?.let(CallControl::toggleHold) }
        val add = toggle(t("add_call")) { addCall() }
        merge = toggle(t("merge")) { mergeOrSwap() }
        listOf(mute, speaker, pad, hold, add, merge).forEach { grid.addView(it, cell()) }
        box.addView(grid)
        box.addView(
            ui.button(t("end_call"), Ui.RED, Ui.ON_COLOR) {
                CallBook.primary()?.let(CallControl::hangUp)
            },
            fullWidth().apply { topMargin = ui.dp(GAP) }
        )
        return box
    }

    private fun keypadView(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tones = ui.text(Ui.TITLE_SP, bold = true).centered().also(box::addView)
        val grid = GridLayout(this).apply { columnCount = KEYPAD_COLUMNS }
        "123456789*0#".forEach { c ->
            grid.addView(
                toggle(c.toString()) {
                    CallBook.primary()?.let { call ->
                        call.playDtmfTone(c)
                        call.stopDtmfTone()
                    }
                    tones.append(c.toString())
                },
                cell()
            )
        }
        box.addView(grid)
        return box
    }

    private fun toggle(label: String, onClick: () -> Unit): TextView =
        ui.text(Ui.BUTTON_SP, s = label).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            minHeight = ui.dp(TOGGLE_HEIGHT)
            background = ui.focusBackground(palette.surface)
            setOnClickListener { onClick() }
        }

    private fun fullWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun weighted() =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ui.dp(GAP)
            marginEnd = ui.dp(GAP)
        }

    private fun cell() = gridCell(ui, GAP)

    override fun onStart() {
        super.onStart()
        CallBook.listen(this)
        handler.post(tick)
        show()
    }

    override fun onStop() {
        CallBook.unlisten(this)
        handler.removeCallbacks(tick)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_KEYPAD, false)) setKeypad(true)
    }

    override fun onCallsChanged() = show()

    private fun show() {
        val call = CallBook.primary()
        if (call == null) {
            state.text = t("call_ended")
            handler.postDelayed({ if (CallBook.primary() == null) finish() }, END_LINGER_MS)
            return
        }
        state.text = CallInfo.stateLabel(call)
        name.text = CallInfo.name(this, call)
        number.text = CallInfo.subtitle(this, call)
        number.visibility = visibleIf(number.text.isNotEmpty())
        showSecond(call)
        showAccounts(call)
        showPanels(call)
        showToggles(call)
        showTimer()
    }

    /** Answer and Decline while ringing, the SIM list while choosing, else the controls (and the keypad). */
    private fun showPanels(call: Call) {
        val s = call.details.state
        val isRinging = s == Call.STATE_RINGING || s == Call.STATE_SIMULATED_RINGING
        val busy = isRinging || s == Call.STATE_SELECT_PHONE_ACCOUNT
        ringing.visibility = visibleIf(isRinging)
        controls.visibility = visibleIf(!busy)
        keypad.visibility = visibleIf(keypadOpen && !busy)
        if (isRinging && currentFocus == null) answer.requestFocus()
        hint.text = when {
            isRinging -> t("hint_ringing")
            keypadOpen -> t("hint_keypad")
            else -> t("hint_call")
        }
    }

    private fun visibleIf(shown: Boolean) = if (shown) View.VISIBLE else View.GONE

    private fun showToggles(call: Call) {
        val audio = CallBook.audio
        mark(mute, audio?.isMuted == true)
        speaker.text = if (CallControl.hasBluetooth()) t("audio") else t("speaker")
        mark(speaker, audio?.route == CallAudioState.ROUTE_SPEAKER)
        mark(hold, call.details.state == Call.STATE_HOLDING)
        hold.isEnabled =
            call.details.can(Call.Details.CAPABILITY_HOLD) ||
            call.details.state == Call.STATE_HOLDING
        val other = CallBook.other(call)
        val canMerge = call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
            call.conferenceableCalls.isNotEmpty()
        merge.text = if (canMerge || other == null) t("merge") else t("swap")
        merge.isEnabled = canMerge || other != null
    }

    private fun mark(view: TextView, on: Boolean) {
        view.setTextColor(if (on) palette.accent else palette.text)
        view.isSelected = on
    }

    private fun showSecond(primary: Call) {
        val other = CallBook.other(primary)
        second.visibility = if (other == null) View.GONE else View.VISIBLE
        if (other != null) {
            val what = CallInfo.stateLabel(other).ifEmpty { t("ongoing_call") }
            second.text = t("other_call", CallInfo.name(this, other), what)
        }
    }

    /** The call timer's next tick, a second on. */
    private fun postTick() {
        handler.postDelayed(tick, TICK_MS)
    }

    private fun showTimer() {
        val call = CallBook.primary()
        val since = call?.details?.connectTimeMillis ?: 0
        timer.text = if (call != null && since > 0 && call.details.state == Call.STATE_ACTIVE) {
            CallFormat.duration((System.currentTimeMillis() - since) / MILLIS)
        } else {
            ""
        }
    }

    /** Dual SIM with no default: the call waits until a SIM is chosen. */
    private fun showAccounts(call: Call) {
        val choosing = call.details.state == Call.STATE_SELECT_PHONE_ACCOUNT
        accounts.visibility = if (choosing) View.VISIBLE else View.GONE
        if (!choosing || accountsFor == call) return
        accountsFor = call
        accounts.removeAllViews()
        val telecom = getSystemService(TelecomManager::class.java)
        val suggested = call.details.intentExtras
            ?.getParcelableArrayList(
                Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS,
                PhoneAccountSuggestion::class.java
            )
            ?.map { it.phoneAccountHandle }
        val handles = suggested?.takeIf { it.isNotEmpty() }
            ?: runCatching { telecom.callCapablePhoneAccounts }.getOrNull().orEmpty()
        handles.forEach { h ->
            val label =
                runCatching { telecom.getPhoneAccount(h)?.label?.toString() }.getOrNull()
                    ?: t("sim")
            accounts.addView(toggle(label) { CallControl.chooseAccount(call, h) }, fullWidth())
        }
        accounts.addView(ui.button(t("cancel"), Ui.RED, Ui.ON_COLOR) { CallControl.hangUp(call) })
        accounts.getChildAt(0)?.requestFocus()
    }

    private fun setKeypad(open: Boolean) {
        keypadOpen = open
        if (!open) tones.text = ""
        show()
    }

    private fun speakerOrRoutes() {
        if (!CallControl.hasBluetooth()) {
            CallControl.toggleSpeaker()
            return
        }
        val routes = listOf(
            CallAudioState.ROUTE_WIRED_OR_EARPIECE to t("route_phone"),
            CallAudioState.ROUTE_SPEAKER to t("speaker"),
            CallAudioState.ROUTE_BLUETOOTH to t("route_bluetooth")
        )
        android.app.AlertDialog.Builder(this)
            .setTitle(t("audio"))
            .setItems(
                routes.map {
                    it.second
                }.toTypedArray()
            ) { _, i -> CallControl.setRoute(routes[i].first) }
            .show()
    }

    private fun addCall() {
        startActivity(
            Intent(this, PhoneActivity::class.java).setAction(PhoneActivity.ACTION_ADD_CALL)
        )
    }

    private fun mergeOrSwap() {
        val call = CallBook.primary() ?: return
        when {
            call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> call.mergeConference()

            call.conferenceableCalls.isNotEmpty() -> call.conference(
                call.conferenceableCalls.first()
            )

            else -> swap()
        }
    }

    private fun swap() {
        val call = CallBook.primary() ?: return
        if (call.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) {
            call.swapConference()
        } else {
            CallBook.other(call)?.unhold()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The repeats and release of the press that answered never reach the controls that replace Answer.
        val swallowed = event.keyCode == answeredKey
        if (swallowed && event.action == KeyEvent.ACTION_UP) answeredKey = -1
        return swallowed || (event.action == KeyEvent.ACTION_DOWN && handleKey(event)) ||
            super.dispatchKeyEvent(event)
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val ringingCall = CallBook.ringing()
        val screen = InCallKeys.Screen(
            ringing = ringingCall != null && CallBook.primary() == ringingCall,
            keypadOpen = keypadOpen,
            spaceAnswers = PhoneSettings(this).spaceAnswers
        )
        val action = InCallKeys.decide(event.keyCode, event.metaState, event.repeatCount, screen)
        val call = CallBook.primary()
        when (action) {
            InCallKeys.Action.ANSWER -> ringingCall?.let {
                answeredKey = event.keyCode
                CallControl.answer(it)
            }

            InCallKeys.Action.DECLINE -> ringingCall?.let(CallControl::decline)

            InCallKeys.Action.HANG_UP -> call?.let(CallControl::hangUp)

            InCallKeys.Action.MUTE -> CallControl.toggleMute()

            InCallKeys.Action.SPEAKER -> speakerOrRoutes()

            InCallKeys.Action.HOLD -> call?.let(CallControl::toggleHold)

            InCallKeys.Action.KEYPAD -> setKeypad(true)

            InCallKeys.Action.NONE -> return keypadTone(event)
        }
        return true
    }

    /** With the keypad open, a dial key (W E R = 1 2 3 on the phone pad) sends its tone. Back closes the keypad. */
    private fun keypadTone(event: KeyEvent): Boolean {
        val c = DialKeys.dialChar(
            event.keyCode,
            event.unicodeChar,
            event.number.code,
            event.metaState,
            true
        )
            ?.takeIf { it != '+' }
        if (!keypadOpen) return false
        val back = event.keyCode == KeyEvent.KEYCODE_BACK
        if (back) {
            setKeypad(false)
        } else if (c != null && event.repeatCount == 0) {
            tone(c)
        }
        return back || c != null
    }

    private fun tone(c: Char) {
        CallBook.primary()?.let { call ->
            call.playDtmfTone(c)
            handler.postDelayed({ call.stopDtmfTone() }, TONE_MS)
        }
        tones.append(c.toString())
    }

    companion object {
        const val EXTRA_KEYPAD = "org.sableos.phone.extra.KEYPAD"
        private const val NAME_SP = 28f
        private const val COLUMNS = 3
        private const val KEYPAD_COLUMNS = 3
        private const val TOGGLE_HEIGHT = 44
        private const val GAP = 8
        private const val TICK_MS = 1000L
        private const val MILLIS = 1000L
        private const val TONE_MS = 150L
        private const val END_LINGER_MS = 1200L
    }
}
