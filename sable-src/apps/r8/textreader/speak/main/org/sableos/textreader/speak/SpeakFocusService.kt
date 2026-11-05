package org.sableos.textreader.speak

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import org.vaachak.textreader.R

/**
 * "Speak what's focused": speaks the item the trackpad or keys move focus to, with the phone's
 * text-to-speech engine (Sable Speech, eSpeak NG). It is not a screen reader: it does not take over
 * touch (no explore by touch) or keys (no key filtering), so the Q25 keyboard and trackpad work as
 * they always do. It is quiet while the phone is locked and stops when the screen turns off.
 */
class SpeakFocusService :
    AccessibilityService(),
    TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var last: String? = null
    private var lastAt = 0L
    private lateinit var words: FocusWords

    private val screenOff =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                tts?.stop()
                last = null
            }
        }

    override fun onServiceConnected() {
        words =
            FocusWords(
                button = getString(R.string.sable_speak_focus_button),
                switch = getString(R.string.sable_speak_focus_switch),
                checkBox = getString(R.string.sable_speak_focus_check_box),
                editBox = getString(R.string.sable_speak_focus_edit_box),
                on = getString(R.string.sable_speak_focus_on),
                off = getString(R.string.sable_speak_focus_off),
                checked = getString(R.string.sable_speak_focus_checked),
                notChecked = getString(R.string.sable_speak_focus_not_checked),
                password = getString(R.string.sable_speak_focus_password),
            )
        tts = TextToSpeech(this, this)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOff, filter)
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::words.isInitialized) return
        val utterance = FocusSpeech.utterance(factsOf(event), words) ?: return
        val now = SystemClock.uptimeMillis()
        if (!FocusSpeech.shouldSpeak(last, lastAt, utterance, now)) return
        last = utterance
        lastAt = now
        if (ready) tts?.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun factsOf(event: AccessibilityEvent): FocusFacts {
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: true
        if (locked) return FocusFacts(locked = true)
        val node = event.source
        val text = event.text.joinToString(" ").ifBlank { node?.text?.toString().orEmpty() }
        return FocusFacts(
            text = text,
            description = (event.contentDescription ?: node?.contentDescription)?.toString(),
            hint = node?.hintText?.toString(),
            role =
                FocusSpeech.roleOf(
                    className = (node?.className ?: event.className)?.toString(),
                    editable = node?.isEditable == true,
                    checkable = node?.isCheckable == true,
                ),
            password = event.isPassword || node?.isPassword == true,
            checked = if (node?.isCheckable == true) node.isChecked else null,
            state = node?.stateDescription?.toString(),
        )
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private companion object {
        const val UTTERANCE_ID = "sable_focus"
    }
}
