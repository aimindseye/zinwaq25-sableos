package org.sableos.phone.android

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log

/**
 * Rings for an incoming call: the phone's ringtone looped on the ring stream, and the vibration pattern, as the
 * ringer mode and Do Not Disturb allow. Sable Phone declares that it rings (IN_CALL_SERVICE_RINGING), so Telecom
 * leaves this to the Phone app; without it nothing rang on the Q25. Also lights the screen for the call.
 */
class Ringer(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private var silenced = false

    val ringing: Boolean get() = ringtone != null || vibrator != null

    @Suppress("TooGenericExceptionCaught")
    fun start() {
        if (ringing || silenced) return
        try {
            lightScreen()
            val audio = context.getSystemService(AudioManager::class.java)
            val mode = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            val quiet = dndSilencesCalls()
            if (mode == AudioManager.RINGER_MODE_NORMAL && !quiet) playRingtone()
            if (mode != AudioManager.RINGER_MODE_SILENT && !quiet) vibrate()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Cannot ring", e)
        }
    }

    /** The user silenced this call: it keeps ringing on screen but makes no more sound. */
    fun silence() {
        silenced = true
        stop()
    }

    /** No call is ringing any more: the next one may ring again. */
    fun reset() {
        silenced = false
        stop()
    }

    private fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    fun release() {
        reset()
        wake?.takeIf { it.isHeld }?.release()
        wake = null
    }

    private fun playRingtone() {
        val uri =
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI
        val tone = RingtoneManager.getRingtone(context, uri) ?: return
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        tone.isLooping = true
        tone.play()
        ringtone = tone
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v =
            context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() } ?: return
        v.vibrate(
            VibrationEffect.createWaveform(PATTERN, 0),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
        )
        vibrator = v
    }

    /** Do Not Disturb that lets no calls through (Total silence or Alarms only). */
    private fun dndSilencesCalls(): Boolean {
        val filter = context.getSystemService(
            NotificationManager::class.java
        )?.currentInterruptionFilter
        return filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            filter == NotificationManager.INTERRUPTION_FILTER_ALARMS
    }

    // Wakes a sleeping screen for the call; the call screen shows over the lock screen.
    @Suppress("DEPRECATION")
    private fun lightScreen() {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        val lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SablePhone:ring"
        )
        lock.acquire(WAKE_MS)
        wake = lock
    }

    private companion object {
        const val TAG = "SablePhoneRinger"
        const val WAKE_MS = 15_000L
        val PATTERN = longArrayOf(0, 800, 800)
    }
}
