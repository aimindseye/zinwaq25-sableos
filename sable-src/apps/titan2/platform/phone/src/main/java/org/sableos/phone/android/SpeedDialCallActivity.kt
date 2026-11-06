package org.sableos.phone.android

import android.app.Activity
import android.os.Bundle
import org.sableos.phone.core.SpeedDial

/**
 * Sable Start's speed dial: calls the number on the letter in EXTRA_LETTER, or opens the speed dial list when the
 * letter has none. Callers need org.sableos.permission.SPEED_DIAL, so Start needs no call permission itself.
 */
class SpeedDialCallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val letter = intent.getStringExtra(SpeedDial.EXTRA_LETTER)?.singleOrNull()
        val entry = letter?.let { SpeedDialStore(this).get(it) }
        if (entry != null) {
            Dialing.call(this, entry.number)
        } else if (letter != null) {
            startActivity(SpeedDialActivity.assign(this, letter))
        }
        finish()
    }
}
