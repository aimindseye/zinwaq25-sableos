package org.sableos.hub

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.provider.Telephony
import org.sableos.hub.notifications.ConnectedNotificationActivityRegistry

class HubSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val appContext = checkNotNull(context)
        enforceLauncherCaller(appContext)
        val canReadSms =
            appContext.checkSelfPermission(Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

        val unread =
            if (canReadSms) {
                runCatching {
                    appContext
                        .contentResolver
                        .query(
                            Telephony.Sms.Inbox.CONTENT_URI,
                            arrayOf(Telephony.Sms._ID),
                            Telephony.Sms.READ + "=0",
                            null,
                            null,
                        )?.use { it.count } ?: 0
                }.getOrDefault(0)
            } else {
                null
            }

        val connectedActive =
            ConnectedNotificationActivityRegistry.activeConversationCount()
        val attentionCount =
            (unread ?: 0) + connectedActive
        val availability =
            if (attentionCount == 0) {
                "EMPTY"
            } else {
                "LIVE"
            }
        val detail =
            when (attentionCount) {
                0 -> "0 active"
                1 -> "1 active"
                else -> attentionCount.toString() + " active"
            }

        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    "Hub",
                    detail,
                    availability,
                    System.currentTimeMillis(),
                ),
            )
            setNotificationUri(appContext.contentResolver, SNAPSHOT_URI)
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.org.sableos.hub.snapshot"

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

    private fun enforceLauncherCaller(context: android.content.Context) {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        val packages =
            context
                .packageManager
                .getPackagesForUid(callingUid)
                ?.toSet()
                .orEmpty()
        if ("org.sableos.launcher" !in packages) {
            throw SecurityException(
                "Sable live snapshot is restricted to SableLauncher.",
            )
        }
    }

    companion object {
        val SNAPSHOT_URI: Uri =
            Uri.parse("content://org.sableos.hub.snapshot/current")
        private val COLUMNS =
            arrayOf("title", "detail", "availability", "observed_at")
    }
}
