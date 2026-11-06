package org.sableos.assistant.platform

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Cosgrove's name for Sable Start's Command (/cosgrove follows a rename): one row, the name and
 * its command word. Nothing else is readable; no key, address or conversation.
 */
class PersonaProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val settings = AssistantStore(requireNotNull(context)).load()
        return MatrixCursor(arrayOf(COLUMN_NAME, COLUMN_WORD)).apply {
            addRow(arrayOf(settings.name, settings.commandWord))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.org.sableos.assistant.persona"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "org.sableos.assistant.persona"
        val URI: Uri = Uri.parse("content://$AUTHORITY")
        const val COLUMN_NAME = "name"
        const val COLUMN_WORD = "word"
    }
}
