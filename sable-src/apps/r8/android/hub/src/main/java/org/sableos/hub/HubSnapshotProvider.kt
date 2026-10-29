package org.sableos.hub

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.os.UserManager
import android.provider.Telephony
import org.sableos.hub.HubText.t
import org.sableos.hub.notifications.ActiveNotificationCountRegistry
import org.sableos.hub.notifications.ConnectedNotificationActivityRegistry
import org.sableos.hub.notifications.SableNotificationListenerService
import org.sableos.hub.platform.PrivacyReader
import org.sableos.hub.policy.ConnectedAppsParity
import org.sableos.hub.policy.HubAppCounts
import org.sableos.hub.policy.HubPrivacyState

/**
 * Sable Start's live Hub data, readable by Start only:
 * - `current`: one row, the Messages card's "N active" line.
 * - `apps`: Start's unread bar, one row per app (`package`, `count`), most unread first; no
 *   notification text. Hidden apps are left out, and while the device is locked or Sable private
 *   mode is on there is one row, package "*", with the total ([HubAppCounts]).
 */
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
        if (uri.pathSegments.firstOrNull() == PATH_APPS) return appCounts(appContext)
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
                0 -> t("n0_active")
                1 -> t("n1_active")
                else -> attentionCount.toString() + " active"
            }

        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    t("hub"),
                    detail,
                    availability,
                    System.currentTimeMillis(),
                ),
            )
            setNotificationUri(appContext.contentResolver, SNAPSHOT_URI)
        }
    }

    private fun appCounts(appContext: android.content.Context): Cursor {
        val userManager = appContext.getSystemService(UserManager::class.java)
        val personalSerial = userManager?.getSerialNumberForUser(Process.myUserHandle()) ?: -1L
        val accessGranted = notificationAccessGranted(appContext)
        val hidden =
            ConnectedAppsParity.effectiveHiddenKeys(
                policies = ConnectedAppsRepository(appContext).loadPolicies(),
                notificationAccessGranted = accessGranted,
            )
        val privacy =
            HubPrivacyState(
                deviceLocked = PrivacyReader(appContext).deviceLocked(),
                privateMode = HubPreferences(appContext).privateMode(),
            )
        val rows =
            if (personalSerial < 0L || !accessGranted) {
                emptyList()
            } else {
                HubAppCounts.rows(
                    active = ActiveNotificationCountRegistry.snapshot(),
                    hidden = hidden,
                    personalSerial = personalSerial,
                    privacy = privacy,
                )
            }
        return MatrixCursor(APP_COLUMNS).apply {
            rows.forEach { row -> addRow(arrayOf<Any>(row.packageName, row.count)) }
            setNotificationUri(appContext.contentResolver, SNAPSHOT_URI)
        }
    }

    private fun notificationAccessGranted(context: android.content.Context): Boolean =
        runCatching {
            checkNotNull(context.getSystemService(NotificationManager::class.java))
                .isNotificationListenerAccessGranted(
                    ComponentName(context, SableNotificationListenerService::class.java),
                )
        }.getOrDefault(false)

    override fun getType(uri: Uri): String =
        if (uri.pathSegments.firstOrNull() == PATH_APPS) {
            "vnd.android.cursor.dir/vnd.org.sableos.hub.app-count"
        } else {
            "vnd.android.cursor.item/vnd.org.sableos.hub.snapshot"
        }

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
        const val PATH_APPS = "apps"
        private val COLUMNS =
            arrayOf("title", "detail", "availability", "observed_at")
        private val APP_COLUMNS = arrayOf("package", "count")
    }
}
