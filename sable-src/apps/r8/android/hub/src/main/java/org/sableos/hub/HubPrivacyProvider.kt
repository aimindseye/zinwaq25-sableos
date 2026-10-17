package org.sableos.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import org.sableos.hub.policy.HubPrivacyCallers

/**
 * Read-only Sable private mode for Phone and Contacts (patches/framework/packages/apps/Dialer
 * 0706 and Contacts 0754): one row, one column, `private_mode` 0 or 1. Hub owns the switch
 * (Connected apps > Private mode); Phone hides names in Recents and Contacts hides numbers while
 * it is on. Only the callers in [HubPrivacyCallers.ALLOWED_PACKAGES] may read it; nothing is
 * writable.
 */
class HubPrivacyProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val appContext = checkNotNull(context)
        enforceCaller(appContext)
        val privateMode = HubPreferences(appContext).privateMode()
        return MatrixCursor(COLUMNS).apply {
            addRow(arrayOf<Any?>(if (privateMode) 1 else 0))
            setNotificationUri(appContext.contentResolver, STATE_URI)
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.org.sableos.hub.privacy"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Read-only provider")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    private fun enforceCaller(context: Context) {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        val packages =
            context.packageManager
                .getPackagesForUid(callingUid)
                ?.toList()
                .orEmpty()
        if (!HubPrivacyCallers.allowed(packages)) {
            throw SecurityException("Sable private mode is readable by Phone, Contacts and Start only.")
        }
    }

    companion object {
        const val AUTHORITY = "org.sableos.hub.privacy"
        const val COLUMN_PRIVATE_MODE = "private_mode"
        val STATE_URI: Uri = Uri.parse("content://$AUTHORITY/state")
        private val COLUMNS = arrayOf(COLUMN_PRIVATE_MODE)
    }
}
