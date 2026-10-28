package org.sableos.titan2.displaycompat.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.view.WindowInsetsController

/**
 * The colours of the Sable theme for a View-based platform app: dark or light from the system
 * Dark theme, the accent from the Sable appearance authority (Settings > Display > Sable appearance),
 * the same as every Compose Sable app. A build without the authority reads Sable Blue.
 */
internal data class SablePalette(
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

        /** Accent and the text drawn on it, by the stable value Settings stores (SableTheme AccentPreset). */
        private val ACCENTS =
            mapOf(
                "blue" to (0xFF4D9CFF.toInt() to 0xFF06111F.toInt()),
                "green" to (0xFF35C66B.toInt() to 0xFF04140A.toInt()),
                "purple" to (0xFF7A42E8.toInt() to 0xFFFFFFFF.toInt()),
                "orange" to (0xFFF28C45.toInt() to 0xFF1D0C02.toInt()),
                "slate" to (0xFF64748B.toInt() to 0xFFFFFFFF.toInt())
            )

        fun of(dark: Boolean, accent: String?): SablePalette {
            val (a, onA) = ACCENTS[accent] ?: ACCENTS.getValue("blue")
            return if (dark) {
                SablePalette(
                    dark = true,
                    window = 0xFF06121D.toInt(),
                    surface = 0xFF0C1E2D.toInt(),
                    focus = 0xFF1E3A52.toInt(),
                    text = 0xFFEEF8FF.toInt(),
                    muted = 0xFF8BA6B8.toInt(),
                    accent = a,
                    onAccent = onA,
                    warn = 0xFFFFB74D.toInt(),
                    ok = 0xFF4CC38A.toInt(),
                    danger = 0xFFFF6B6B.toInt()
                )
            } else {
                SablePalette(
                    dark = false,
                    window = 0xFFF2F4F7.toInt(),
                    surface = 0xFFFFFFFF.toInt(),
                    focus = 0xFFDCE4EC.toInt(),
                    text = 0xFF12171C.toInt(),
                    muted = 0xFF56636F.toInt(),
                    accent = a,
                    onAccent = onA,
                    warn = 0xFF9A5800.toInt(),
                    ok = 0xFF1E7A4C.toInt(),
                    danger = 0xFFC62828.toInt()
                )
            }
        }

        fun read(context: Context): SablePalette {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return of(night == Configuration.UI_MODE_NIGHT_YES, readAccent(context))
        }

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
