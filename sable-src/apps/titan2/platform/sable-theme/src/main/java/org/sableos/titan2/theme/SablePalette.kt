package org.sableos.titan2.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.view.WindowInsetsController

/**
 * The colours of the Sable theme for a View-based platform app: dark or light from the system
 * Dark theme, the accent from the Sable appearance authority (Settings > Display > Sable appearance),
 * the same as every Compose Sable app. A build without the authority reads Sable Blue.
 *
 * One copy for every platform app: each module adds `../sable-theme/src/main/java` as a source dir.
 */
data class SablePalette(
    val dark: Boolean,
    val window: Int,
    val surface: Int,
    val focus: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
    val warn: Int,
    val ok: Int,
    val danger: Int
) {
    /** The window behind the views, and status/navigation bar icons that stay readable on it. */
    fun applyTo(activity: Activity) {
        activity.window.decorView.setBackgroundColor(window)
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        activity.window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
    }

    companion object {
        private const val AUTHORITY_URI = "content://org.sableos.appearance/appearance"
        private const val COLUMN_ACCENT = "accent"

        /**
         * Accent tones by the stable value Settings stores: (dark accent, text on it, light accent, text on it),
         * the same as SableAccentTokens in sabledesign so platform apps match Start, Settings and Quick Settings.
         */
        private val ACCENTS =
            mapOf(
                "blue" to listOf(0xFF4D9CFF, 0xFF06111F, 0xFF1F6FD1, 0xFFFFFFFF),
                "green" to listOf(0xFF35C66B, 0xFF04140A, 0xFF1A7F45, 0xFFFFFFFF),
                "purple" to listOf(0xFFA47CFF, 0xFF0F0526, 0xFF7A42E8, 0xFFFFFFFF),
                "orange" to listOf(0xFFF28C45, 0xFF1D0C02, 0xFFB35410, 0xFFFFFFFF),
                "slate" to listOf(0xFF94A3B8, 0xFF0B1220, 0xFF55657A, 0xFFFFFFFF)
            )

        /** Surfaces and text are SableSystemTokens (DESIGN-KF-B), the neutral palette every Sable surface uses. */
        fun of(dark: Boolean, accent: String?): SablePalette {
            val tones = (ACCENTS[accent] ?: ACCENTS.getValue("blue")).map { it.toInt() }
            return if (dark) {
                SablePalette(
                    dark = true,
                    window = 0xFF05070A.toInt(),
                    surface = 0xFF0D1117.toInt(),
                    focus = 0xFF161C24.toInt(),
                    text = 0xFFF7F9FC.toInt(),
                    muted = 0xFFAAB4C0.toInt(),
                    accent = tones[0],
                    onAccent = tones[1],
                    warn = 0xFFFBBF24.toInt(),
                    ok = 0xFF4ADE80.toInt(),
                    danger = 0xFFFF8A80.toInt()
                )
            } else {
                SablePalette(
                    dark = false,
                    window = 0xFFF5F7F9.toInt(),
                    surface = 0xFFFFFFFF.toInt(),
                    focus = 0xFFE9EDF2.toInt(),
                    text = 0xFF12171C.toInt(),
                    muted = 0xFF515B66.toInt(),
                    accent = tones[2],
                    onAccent = tones[3],
                    warn = 0xFF8A5A00.toInt(),
                    ok = 0xFF1A7F45.toInt(),
                    danger = 0xFFBA1A1A.toInt()
                )
            }
        }

        fun read(context: Context): SablePalette = of(isNight(context), readAccent(context))

        private fun isNight(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        private fun readAccent(context: Context): String? = runCatching {
            context.contentResolver.query(
                Uri.parse(AUTHORITY_URI),
                arrayOf(COLUMN_ACCENT),
                null,
                null,
                null
            )?.use { c ->
                val i = c.getColumnIndex(COLUMN_ACCENT)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            }
        }.getOrNull()
    }
}
