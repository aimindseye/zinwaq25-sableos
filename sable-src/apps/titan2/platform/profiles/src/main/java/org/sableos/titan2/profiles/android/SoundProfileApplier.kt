package org.sableos.titan2.profiles.android

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import org.sableos.titan2.profiles.core.Dnd
import org.sableos.titan2.profiles.core.ProfilePlan
import org.sableos.titan2.profiles.core.ProfileSettings
import org.sableos.titan2.profiles.core.Ringer
import org.sableos.titan2.profiles.core.SoundProfile
import org.sableos.titan2.profiles.core.SoundProfiles

/** Stores the chosen sound profile and each profile's own settings. */
class SoundProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("sound_profiles", Context.MODE_PRIVATE)

    var current: SoundProfile?
        get() = SoundProfile.fromName(prefs.getString(CURRENT, null))
        set(v) {
            prefs.edit().putString(CURRENT, v?.name).apply()
        }

    fun settings(profile: SoundProfile): ProfileSettings =
        SoundProfiles.parse(prefs.getString(profile.name, null))

    fun save(profile: SoundProfile, settings: ProfileSettings) {
        prefs.edit().putString(profile.name, SoundProfiles.format(settings)).apply()
    }

    /** The brightness from before Bedside ("mode,value"), kept until another profile puts it back. */
    var brightnessBeforeBedside: String?
        get() = prefs.getString(BRIGHTNESS_BEFORE_BEDSIDE, null)
        set(v) {
            prefs.edit().putString(BRIGHTNESS_BEFORE_BEDSIDE, v).apply()
        }

    private companion object {
        const val CURRENT = "current"
        const val BRIGHTNESS_BEFORE_BEDSIDE = "brightness_before_bedside"
    }
}

/** Sets a sound profile on the phone. */
class SoundProfileApplier(private val context: Context) {
    enum class Result {
        APPLIED,

        /** Do Not Disturb access is missing: nothing was changed. */
        NEEDS_DND_ACCESS,

        /** Sound was set, but tones, vibration or the light could not be (no settings access). */
        PARTIAL
    }

    fun apply(profile: SoundProfile): Result {
        val store = SoundProfileStore(context)
        val plan = SoundProfiles.plan(profile, store.settings(profile))
        val nm = context.getSystemService(NotificationManager::class.java)
        val dndChanges =
            plan.dnd != Dnd.OFF ||
                nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if ((dndChanges || plan.ringer != Ringer.NORMAL) && !nm.isNotificationPolicyAccessGranted) {
            return Result.NEEDS_DND_ACCESS
        }
        // Do Not Disturb off first, so the ringer mode is not overridden; on again last.
        if (plan.dnd == Dnd.OFF &&
            dndChanges
        ) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        setSound(plan)
        setDnd(nm, plan.dnd)
        store.current = profile
        val screen = setScreen(store, plan)
        return if (setExtras(plan) && screen) Result.APPLIED else Result.PARTIAL
    }

    private fun setSound(plan: ProfilePlan) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.ringerMode = when (plan.ringer) {
            Ringer.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            Ringer.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            Ringer.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
        val pct = plan.volumePercent ?: return
        for (stream in intArrayOf(AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION)) {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, (max * pct + PERCENT / 2) / PERCENT, 0)
        }
    }

    private fun setDnd(nm: NotificationManager, dnd: Dnd) {
        when (dnd) {
            Dnd.CALLS_ONLY -> {
                nm.notificationPolicy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                        NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
                        NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY
                )
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }

            Dnd.ALARMS_ONLY -> nm.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_ALARMS
            )

            Dnd.OFF -> Unit
        }
    }

    /** Vibration, the light and the tones; false when the system settings could not be written. */
    private fun setExtras(plan: ProfilePlan): Boolean =
        Settings.System.canWrite(context) && runCatching {
            val cr = context.contentResolver
            Settings.System.putInt(
                cr,
                Settings.System.VIBRATE_WHEN_RINGING,
                if (plan.vibrateWhenRinging) 1 else 0
            )
            Settings.System.putInt(cr, NOTIFICATION_LIGHT_PULSE, if (plan.light) 1 else 0)
            plan.ringtone?.let { setTone(RingtoneManager.TYPE_RINGTONE, it) }
            plan.notificationTone?.let { setTone(RingtoneManager.TYPE_NOTIFICATION, it) }
        }.isSuccess

    /**
     * Bedside dims the screen and remembers the brightness it found; any other profile puts that
     * back. False when system settings cannot be written.
     */
    private fun setScreen(store: SoundProfileStore, plan: ProfilePlan): Boolean {
        val saved = store.brightnessBeforeBedside
        if (!plan.dimScreen && saved == null) return true
        if (!Settings.System.canWrite(context)) return false
        val cr = context.contentResolver
        return runCatching {
            if (plan.dimScreen) {
                if (saved == null) {
                    store.brightnessBeforeBedside =
                        SoundProfiles.formatBrightness(
                            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE),
                            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
                        )
                }
                Settings.System.putInt(
                    cr,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, SoundProfiles.BEDSIDE_BRIGHTNESS)
            } else {
                SoundProfiles.restoreBrightness(saved)?.let { (mode, value) ->
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, value)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
                }
                store.brightnessBeforeBedside = null
            }
        }.isSuccess
    }

    private fun setTone(type: Int, uri: String) {
        RingtoneManager.setActualDefaultRingtoneUri(context, type, Uri.parse(uri))
    }

    private companion object {
        const val PERCENT = 100

        // Settings.System.NOTIFICATION_LIGHT_PULSE (hidden in the SDK, public in the settings provider).
        const val NOTIFICATION_LIGHT_PULSE = "notification_light_pulse"
    }
}
