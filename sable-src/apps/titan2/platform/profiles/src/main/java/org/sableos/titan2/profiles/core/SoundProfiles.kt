package org.sableos.titan2.profiles.core

/**
 * BlackBerry sound profiles. Picking one sets the ringer, Do Not Disturb, the ring and notification
 * volumes, vibration, the notification light and, when the profile has them, its own ringtone and
 * notification tone. Pure: [plan] says what to set and the Android side sets it.
 */
enum class SoundProfile(val title: String, val summary: String) {
    NORMAL("Normal", "Rings and alerts at your volume"),
    LOUD("Loud", "Rings and alerts at full volume"),
    VIBRATE_ONLY("Vibrate only", "Vibrates for calls and alerts, no sound"),
    PHONE_CALLS_ONLY("Phone calls only", "Only calls ring; messages and alerts are silent"),
    ALL_ALERTS_OFF("All alerts off", "Nothing rings or vibrates; alarms still sound");

    companion object {
        fun fromName(name: String?): SoundProfile? = entries.firstOrNull { it.name == name }
    }
}

enum class Ringer { NORMAL, VIBRATE, SILENT }

/** Do Not Disturb as Android's interruption filters: off, priority only (calls), alarms only. */
enum class Dnd { OFF, CALLS_ONLY, ALARMS_ONLY }

/** What the user changed for one profile; null keeps the profile's default or the phone's current tone. */
data class ProfileSettings(
    /** Ring and notification volume, 0 to 100 percent of the stream's maximum. */
    val volumePercent: Int? = null,
    val vibrate: Boolean? = null,
    val light: Boolean? = null,
    val ringtone: String? = null,
    val notificationTone: String? = null
)

/** Everything one profile sets. A null volume or tone leaves it as it is. */
data class ProfilePlan(
    val ringer: Ringer,
    val dnd: Dnd,
    val volumePercent: Int?,
    val vibrateWhenRinging: Boolean,
    val light: Boolean,
    val ringtone: String?,
    val notificationTone: String?
)

object SoundProfiles {
    const val NORMAL_VOLUME = 70
    const val FULL_VOLUME = 100

    fun plan(profile: SoundProfile, settings: ProfileSettings = ProfileSettings()): ProfilePlan {
        val base = when (profile) {
            SoundProfile.NORMAL -> ProfilePlan(
                Ringer.NORMAL,
                Dnd.OFF,
                NORMAL_VOLUME,
                true,
                true,
                null,
                null
            )

            SoundProfile.LOUD -> ProfilePlan(
                Ringer.NORMAL,
                Dnd.OFF,
                FULL_VOLUME,
                true,
                true,
                null,
                null
            )

            SoundProfile.VIBRATE_ONLY -> ProfilePlan(
                Ringer.VIBRATE,
                Dnd.OFF,
                null,
                true,
                true,
                null,
                null
            )

            SoundProfile.PHONE_CALLS_ONLY ->
                ProfilePlan(Ringer.NORMAL, Dnd.CALLS_ONLY, NORMAL_VOLUME, true, false, null, null)

            SoundProfile.ALL_ALERTS_OFF -> ProfilePlan(
                Ringer.SILENT,
                Dnd.ALARMS_ONLY,
                null,
                false,
                false,
                null,
                null
            )
        }
        // A silent or vibrating profile has no volume to set; the user's volume applies only where it rings.
        val volume = settings.volumePercent?.coerceIn(0, FULL_VOLUME)?.takeIf {
            base.volumePercent !=
                null
        }
        return base.copy(
            volumePercent = volume ?: base.volumePercent,
            vibrateWhenRinging = settings.vibrate?.takeIf { profile != SoundProfile.ALL_ALERTS_OFF }
                ?: base.vibrateWhenRinging,
            light = settings.light ?: base.light,
            ringtone = settings.ringtone,
            notificationTone = settings.notificationTone
        )
    }

    /** The next profile, for a key or tile that steps through them. */
    fun next(current: SoundProfile?): SoundProfile {
        val all = SoundProfile.entries
        return if (current == null) all.first() else all[(current.ordinal + 1) % all.size]
    }

    /** Reads one profile's stored settings: `key=value` lines (volume, vibrate, light, ringtone, notification). */
    fun parse(text: String?): ProfileSettings {
        val map = text.orEmpty().lineSequence()
            .mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else null
            }
            .toMap()
        return ProfileSettings(
            volumePercent = map["volume"]?.toIntOrNull()?.coerceIn(0, FULL_VOLUME),
            vibrate = map["vibrate"]?.toBooleanStrictOrNull(),
            light = map["light"]?.toBooleanStrictOrNull(),
            ringtone = map["ringtone"]?.takeIf { it.isNotBlank() },
            notificationTone = map["notification"]?.takeIf { it.isNotBlank() }
        )
    }

    fun format(s: ProfileSettings): String = listOfNotNull(
        s.volumePercent?.let { "volume=$it" },
        s.vibrate?.let { "vibrate=$it" },
        s.light?.let { "light=$it" },
        s.ringtone?.let { "ringtone=${oneLine(it)}" },
        s.notificationTone?.let { "notification=${oneLine(it)}" }
    ).joinToString("\n")

    private fun oneLine(s: String) = s.replace('\n', ' ')
}
