package org.sableos.tools.android

import android.content.Context
import org.sableos.tools.core.StepLedger
import org.sableos.tools.core.StepProfile

/** The pedometer's daily totals and profile, in this app's private preferences. Never leaves the phone. */
class StepStore(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun ledger(): StepLedger = StepLedger.decode(prefs.getString(KEY_LEDGER, null))

    fun save(ledger: StepLedger) {
        prefs.edit().putString(KEY_LEDGER, ledger.encode()).apply()
    }

    fun profile(): StepProfile = StepProfile(
        prefs.getInt(KEY_HEIGHT, StepProfile.DEFAULT_HEIGHT),
        prefs.getInt(KEY_WEIGHT, StepProfile.DEFAULT_WEIGHT),
        prefs.getInt(KEY_GOAL, StepProfile.DEFAULT_GOAL)
    ).safe

    fun save(profile: StepProfile) {
        val p = profile.safe
        prefs.edit()
            .putInt(KEY_HEIGHT, p.heightCm)
            .putInt(KEY_WEIGHT, p.weightKg)
            .putInt(KEY_GOAL, p.goal)
            .apply()
    }

    private companion object {
        const val NAME = "pedometer"
        const val KEY_LEDGER = "ledger"
        const val KEY_HEIGHT = "height_cm"
        const val KEY_WEIGHT = "weight_kg"
        const val KEY_GOAL = "goal"
    }
}
