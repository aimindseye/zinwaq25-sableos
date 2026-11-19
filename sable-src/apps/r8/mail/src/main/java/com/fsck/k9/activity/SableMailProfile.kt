// SableOS: Sable Mail follows the whole-OS profile (Settings.Secure sable_profile, "bb10" default, "bb7").
package com.fsck.k9.activity

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fsck.k9.ui.R

object SableMailProfile {
    private const val SETTING_KEY = "sable_profile"
    private const val BB7 = "bb7"

    /** True only for the stored value "bb7"; a missing or unknown value is the default profile, BlackBerry 10. */
    fun isBb7(stored: String?): Boolean = stored == BB7

    fun isBb7(context: Context): Boolean =
        isBb7(runCatching { Settings.Secure.getString(context.contentResolver, SETTING_KEY) }.getOrNull())

    /**
     * A message row's focus look. BlackBerry 10 keeps the layout's 2 dp accent ring; BlackBerry 7 swaps it
     * for a flat filled bar (no outline).
     */
    fun applyRowFocus(row: View) {
        if (isBb7(row.context)) {
            row.foreground = ContextCompat.getDrawable(row.context, R.drawable.sable_mail_list_item_foreground_bb7)
        }
    }

    /** How long a message row takes to leave or move: BlackBerry 7 is a quick 150 ms, BlackBerry 10 a 250 ms slide. */
    fun rowMotionMillis(bb7: Boolean): Long = if (bb7) BB7_MOTION_MILLIS else BB10_MOTION_MILLIS

    /** Delete and archive take a row out of the list with the profile's motion set. */
    fun applyRowMotion(animator: RecyclerView.ItemAnimator, context: Context) {
        val millis = rowMotionMillis(isBb7(context))
        animator.removeDuration = millis
        animator.moveDuration = millis
        animator.addDuration = millis
    }

    private const val BB7_MOTION_MILLIS = 150L
    private const val BB10_MOTION_MILLIS = 250L
}
