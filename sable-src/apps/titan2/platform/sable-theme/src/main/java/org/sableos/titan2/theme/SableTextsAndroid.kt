package org.sableos.titan2.theme

import android.annotation.SuppressLint
import android.content.Context

/**
 * Reads [SableTexts] lines from [context]'s string resources, by the same keys, so the phone's language picks the
 * translation. Call it in `onCreate`: a language change recreates the activity and binds again.
 */
@SuppressLint("DiscouragedApi")
fun SableTexts.bindTo(context: Context) {
    val res = context.resources
    val pkg = context.packageName
    lookup = { key ->
        val id = res.getIdentifier(key, "string", pkg)
        if (id == 0) null else res.getString(id)
    }
}
