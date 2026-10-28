package org.sableos.titan2.keyboard.android

import android.content.Context
import java.text.DateFormat
import java.util.Date

/** Fills AutoText's variables: %D the full date, %T the time in the phone's 12/24-hour style, %V the build number. */
internal fun autoTextVar(context: Context): (Char) -> String? = { name ->
    when (name) {
        'D' -> DateFormat.getDateInstance(DateFormat.FULL).format(Date())
        'T' -> android.text.format.DateFormat.getTimeFormat(context).format(Date())
        'V' -> SysProps.get("ro.build.display.id").ifBlank { null }
        else -> null
    }
}
