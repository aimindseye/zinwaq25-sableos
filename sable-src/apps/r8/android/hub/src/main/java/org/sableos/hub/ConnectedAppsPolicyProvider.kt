package org.sableos.hub

import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import org.sableos.hub.notifications.SableNotificationListenerService
import org.sableos.hub.policy.ConnectedAppsParity

class ConnectedAppsPolicyProvider : ContentProvider() {
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
        require(uri == ConnectedAppsRepository.HIDDEN_URI) {
            "Unsupported connected-app policy query: $uri"
        }

        // HUB_CONNECTED_APPS_PARITY: without Android notification access Hub cannot show these
        // apps, so Sable Start must not hide them either.
        val hidden =
            ConnectedAppsParity.effectiveHiddenKeys(
                policies = ConnectedAppsRepository(appContext).loadPolicies(),
                notificationAccessGranted = notificationAccessGranted(appContext),
            )
        return MatrixCursor(COLUMNS).apply {
            hidden
                .sortedWith(
                    compareBy<ConnectedAppKey> { it.userSerial }
                        .thenBy { it.packageName },
                ).forEach { key ->
                    addRow(
                        arrayOf<Any>(
                            key.packageName,
                            key.userSerial,
                        ),
                    )
                }
            setNotificationUri(
                appContext.contentResolver,
                ConnectedAppsRepository.HIDDEN_URI,
            )
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.org.sableos.hub.hidden-app"

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

    private fun notificationAccessGranted(context: android.content.Context): Boolean =
        runCatching {
            checkNotNull(context.getSystemService(NotificationManager::class.java))
                .isNotificationListenerAccessGranted(
                    ComponentName(context, SableNotificationListenerService::class.java),
                )
        }.getOrDefault(false)

    private fun enforceLauncherCaller(context: android.content.Context) {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) {
            return
        }

        val packages =
            context.packageManager
                .getPackagesForUid(callingUid)
                ?.toSet()
                .orEmpty()
        if (SABLE_LAUNCHER_PACKAGE !in packages) {
            throw SecurityException(
                "Connected-app hiding policy is restricted to SableLauncher.",
            )
        }
    }

    private companion object {
        const val SABLE_LAUNCHER_PACKAGE = "org.sableos.launcher"
        val COLUMNS =
            arrayOf(
                ConnectedAppsRepository.COLUMN_PACKAGE_NAME,
                ConnectedAppsRepository.COLUMN_USER_SERIAL,
            )
    }
}
