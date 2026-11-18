// SableOS: the home is part of the system, so it gets what it needs without a setup page (owner,
// 2026-11-12). Once, on its first start, it grants itself Contacts, Calendar, Photos, Call log and
// Notifications, notification access (Hub, media and message frames, Peek), usage access (Active Frames)
// and display over other apps, with the platform certificate's own permissions. Anything the person
// turns off later stays off: the grant runs again only for what was never granted before.
// Part of the SableOS home flavor.
package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationListenerService

object SableHomeAccess {
    private const val PREFS = "sable_home_access"
    private const val OP_USAGE = "android:get_usage_stats"
    private const val OP_OVERLAY = "android:system_alert_window"

    /** Runtime permissions the home grants itself once. Location stays the person's choice. */
    fun runtimePermissions(sdk: Int): List<String> =
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.READ_CALL_LOG)
            if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    /** Every item, with a key that marks it done. */
    fun items(sdk: Int): List<String> = runtimePermissions(sdk) + listOf("listener", "usage", "overlay")

    /** The items still to try: never done before, so a later "off" from the person is kept. */
    fun pending(
        all: List<String>,
        done: Set<String>,
    ): List<String> = all.filter { it !in done }

    fun ensure(context: Context) {
        val app = context.applicationContext
        Thread {
            runCatching { grantPending(app) }
        }.apply { name = "sable-home-access" }.start()
    }

    private fun grantPending(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val done = prefs.getStringSet("done", emptySet()).orEmpty()
        val now = done.toMutableSet()
        pending(items(Build.VERSION.SDK_INT), done).forEach { item ->
            val ok =
                when (item) {
                    "listener" -> grantListener(context)
                    "usage" -> grantOp(context, OP_USAGE)
                    "overlay" -> grantOp(context, OP_OVERLAY) || Settings.canDrawOverlays(context)
                    else -> grantRuntime(context, item)
                }
            if (ok) now += item
        }
        if (now != done) prefs.edit().putStringSet("done", now).apply()
        if ("listener" in now && "listener" !in done) ConversationAccess.requestReconnect(context)
    }

    private fun grantRuntime(
        context: Context,
        permission: String,
    ): Boolean {
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true
        runCatching {
            PackageManager::class.java
                .getMethod("grantRuntimePermission", String::class.java, String::class.java, UserHandle::class.java)
                .invoke(context.packageManager, context.packageName, permission, Process.myUserHandle())
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun grantOp(
        context: Context,
        op: String,
    ): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        runCatching {
            AppOpsManager::class.java
                .getMethod("setMode", String::class.java, Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType)
                .invoke(appOps, op, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        }
        return appOps.checkOpNoThrow(op, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun grantListener(context: Context): Boolean {
        if (ConversationAccess.isGranted(context)) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val component = ComponentName(context, ConversationListenerService::class.java)
        runCatching {
            NotificationManager::class.java
                .getMethod(
                    "setNotificationListenerAccessGranted",
                    ComponentName::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).invoke(manager, component, true, false)
        }.recoverCatching {
            NotificationManager::class.java
                .getMethod("setNotificationListenerAccessGranted", ComponentName::class.java, Boolean::class.javaPrimitiveType)
                .invoke(manager, component, true)
        }
        return ConversationAccess.isGranted(context)
    }
}
