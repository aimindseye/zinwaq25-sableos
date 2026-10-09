package org.sableos.titan2.displaycompat.android

import android.content.Context
import org.sableos.titan2.displaycompat.core.SizePx

/**
 * Mini mode resolution is device evidence, not a constant. The Titan manuals only say the resolution becomes
 * "another normal size".
 * Capture it from the stock device (`wm size` before/after toggling Mini mode) and enter it as `WxH` on this
 * app's validation screen (stored in this app's prefs). The product lane no longer assigns a system property
 * for it. Absent => Mini mode is a no-op.
 * Measured Titan 2 evidence is recorded in docs/C3B_PRODUCT_INTERFACE_EVIDENCE.md.
 * Mini Mode is non-blocking and deferred.
 */
object MiniSizeEvidence {
    fun read(context: Context): SizePx? {
        val raw =
            context.getSharedPreferences(
                "evidence",
                Context.MODE_PRIVATE
            ).getString("mini_size", "")
                ?: ""
        val parts = raw.split('x')
        val w = parts.getOrNull(0)?.toIntOrNull()
        val h = parts.getOrNull(1)?.toIntOrNull()
        return if (parts.size == 2 && isPositive(w) && isPositive(h)) {
            SizePx(requireNotNull(w), requireNotNull(h))
        } else {
            null
        }
    }

    private fun isPositive(v: Int?): Boolean = v != null && v > 0
}
