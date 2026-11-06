package org.sableos.calendar

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/** The colours a calendar can be given on the Calendars page; the dot there steps through them. */
object CalendarPalette {
    val COLORS: List<Int> =
        listOf(
            0xFF2E86DE.toInt(),
            0xFF27AE60.toInt(),
            0xFFF39C12.toInt(),
            0xFFE74C3C.toInt(),
            0xFF9B59B6.toInt(),
            0xFF1ABC9C.toInt(),
            0xFFE84393.toInt(),
            0xFF95A5A6.toInt(),
        )

    /** The palette colour after [current]; a colour not in the palette (a provider's own) starts at the first. */
    fun next(current: Int): Int {
        val index = COLORS.indexOf(current or OPAQUE)
        return COLORS[(index + 1) % COLORS.size]
    }

    private const val OPAQUE = -0x1000000
}

/**
 * The colour the user chose for each calendar, kept in Sable Calendar (Android only lets a sync
 * adapter change a calendar's own colour). Keyed by the calendar's id.
 */
class CalendarColors(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("calendar_colors", Context.MODE_PRIVATE)

    fun load(): Map<Long, Int> =
        prefs.all
            .mapNotNull { (key, value) -> key.toLongOrNull()?.let { id -> (value as? Int)?.let { id to it } } }
            .toMap()

    fun set(
        calendarId: Long,
        color: Int,
    ) {
        prefs.edit().putInt(calendarId.toString(), color).apply()
    }
}

/** Calendar name to the colour its events show; empty until the calendars have loaded. */
val LocalEventColors = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }
