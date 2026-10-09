package org.sableos.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process

class MediaSnapshotProvider : ContentProvider() {
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
        val snapshot = MediaSnapshotStore.read(appContext)

        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    "Media",
                    snapshotDetail(snapshot),
                    if (snapshot.title.isNotBlank()) "LIVE" else "EMPTY",
                    snapshot.observedAt,
                ),
            )
            setNotificationUri(
                appContext.contentResolver,
                MediaSnapshotStore.SNAPSHOT_URI,
            )
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.org.sableos.media.snapshot"

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

    private fun snapshotDetail(snapshot: MediaSnapshot): String {
        if (snapshot.title.isBlank()) {
            return "nothing playing"
        }

        return buildString {
            append(snapshot.title)
            if (snapshot.artist.isNotBlank()) {
                append(" · ")
                append(snapshot.artist)
            }
            append(if (snapshot.playing) " · playing" else " · paused")
        }
    }

    private fun enforceLauncherCaller(context: Context) {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) {
            return
        }

        val packages =
            context.packageManager
                .getPackagesForUid(callingUid)
                ?.toSet()
                .orEmpty()

        if ("org.sableos.launcher" !in packages) {
            throw SecurityException(
                "Sable live snapshot is restricted to SableLauncher.",
            )
        }
    }

    private companion object {
        val COLUMNS =
            arrayOf(
                "title",
                "detail",
                "availability",
                "observed_at",
            )
    }
}
