package org.sableos.titan2.keyboard.android

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.sableos.titan2.keyboard.core.KeyProbeFormat

/**
 * Diagnostic only: shows every key event that reaches an ordinary app (key code, scan code, meta state, produced
 * character, device). Keys the vendor framework intercepts (Func1/Func2, the red keys) will simply not appear,
 * which is itself the finding.
 * No permissions, no network, no storage; lines also go to logcat under SableKeyProbe.
 */
class KeyProbeActivity : Activity() {
    private lateinit var view: TextView
    private val lines = ArrayDeque<String>()

    private companion object {
        const val TEXT_SIZE_SP = 12f
        const val PAD_SIDE_PX = 16
        const val PAD_TOP_PX = 48
        const val MAX_LINES = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = TEXT_SIZE_SP
            setPadding(PAD_SIDE_PX, PAD_TOP_PX, PAD_SIDE_PX, PAD_SIDE_PX)
            text = "Key probe: press keys. Hold nothing else. Back key leaves after two presses.\n"
        }
        setContentView(
            ScrollView(this).apply {
                addView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )
    }

    private var backPresses = 0

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val action = if (e.action ==
            KeyEvent.ACTION_DOWN
        ) {
            "DOWN"
        } else if (e.action ==
            KeyEvent.ACTION_UP
        ) {
            "UP  "
        } else {
            "ACT${e.action}"
        }
        val dev = e.device?.name ?: "dev${e.deviceId}"
        val l = KeyProbeFormat.line(
            action,
            KeyProbeFormat.ProbeKey(KeyEvent.keyCodeToString(e.keyCode), e.keyCode, e.scanCode),
            KeyProbeFormat.ProbeChar(e.metaState, e.unicodeChar),
            dev,
            e.repeatCount
        )
        Log.i("SableKeyProbe", l)
        lines.addLast(l)
        while (lines.size > MAX_LINES) lines.removeFirst()
        view.text = lines.joinToString("\n")
        if (e.keyCode ==
            KeyEvent.KEYCODE_BACK
        ) {
            if (e.action == KeyEvent.ACTION_UP &&
                ++backPresses >= 2
            ) {
                finish()
            }
            return true
        }
        backPresses = 0
        return true // consume so nothing else reacts while probing
    }
}
