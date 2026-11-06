package org.sableos.phone.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.sableos.phone.core.SpeedDial

/**
 * Which letters have a speed dial, for Sable Start (letters only: never a name or number). Read access needs
 * org.sableos.permission.SPEED_DIAL. Read-only.
 */
class SpeedDialProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(SpeedDial.COLUMN_LETTER))
        val ctx = context ?: return cursor
        SpeedDialStore(ctx).all().keys.forEach { cursor.addRow(arrayOf<Any>(it.toString())) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
