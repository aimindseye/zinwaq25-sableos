package org.sableos.start.model

import android.content.ComponentName
import android.os.UserHandle

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val privacySummary: String = "",
)


fun AppEntry.stableKey(): String =
    "${user.hashCode()}:${component.flattenToString()}"
