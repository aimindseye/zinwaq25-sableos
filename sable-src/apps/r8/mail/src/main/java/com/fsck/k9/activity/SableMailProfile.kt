// SableOS: Sable Mail follows the whole-OS profile (Settings.Secure sable_profile, "bb10" default, "bb7").
package com.fsck.k9.activity

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
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
}
