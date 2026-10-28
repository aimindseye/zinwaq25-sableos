package org.sableos.tools.android

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.sableos.tools.core.ReportFile

/**
 * Saves a report the user asked to save as a text file in Download/SableTools, through MediaStore (no storage
 * permission). Files, a USB cable or any share target can take it from there. Returns the path shown to the
 * user, or null when the file could not be written.
 */
object ReportFiles {
    fun save(context: Context, title: String, text: String): String? {
        val name = ReportFile.name(title, System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, ReportFile.MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, ReportFile.FOLDER + "/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        val written = uri != null && runCatching {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } !=
                null
        }.getOrDefault(false)
        if (uri != null && !written) resolver.delete(uri, null, null)
        if (uri != null && written) {
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                },
                null,
                null
            )
        }
        return if (written) ReportFile.FOLDER + "/" + name else null
    }
}
