package org.sableos.titan2.displaycompat.android

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.sableos.titan2.displaycompat.core.AppCornerStyle
import org.sableos.titan2.displaycompat.core.AppStyleStore

/**
 * The Sable appearance authority hosted by Settings (patch 0103): one corner style for every
 * Sable app. Settings lets this package write only the corner_style column, and only as the copy
 * on the system image. A build without the authority reads null, so the section is read-only.
 */
class AppearanceStyleStore(private val context: Context) : AppStyleStore {
    override fun read(): AppCornerStyle? =
        runCatching {
            context.contentResolver.query(URI, arrayOf(COLUMN_CORNER_STYLE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val i = c.getColumnIndex(COLUMN_CORNER_STYLE)
                AppCornerStyle.fromStableValue(if (i >= 0) c.getString(i) else null)
            }
        }.getOrNull()

    override fun write(style: AppCornerStyle): Boolean =
        runCatching {
            val values = ContentValues().apply { put(COLUMN_CORNER_STYLE, style.stableValue) }
            context.contentResolver.update(URI, values, null, null) == 1
        }.getOrDefault(false)

    /** Calls [onChange] on the main thread when the style changes elsewhere; returns the unregister action. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onChange()
            }
        val registered =
            runCatching { context.contentResolver.registerContentObserver(URI, false, observer) }.isSuccess
        return { if (registered) runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    companion object {
        const val AUTHORITY = "org.sableos.appearance"
        const val COLUMN_CORNER_STYLE = "corner_style"
        val URI: Uri = Uri.parse("content://$AUTHORITY/appearance")
    }
}
