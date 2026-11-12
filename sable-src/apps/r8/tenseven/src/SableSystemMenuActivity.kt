package io.github.aimindseye.tenseven

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import io.github.aimindseye.tenseven.platform.RecentTasksRepository

/**
 * SableOS: the BlackBerry 7 style system menu, opened by Alt+BlackBerry key from any app
 * (frameworks/base 0324; owner decision 2026-11-12). Its rows: Hub over the app, the app's own menu, Close the app,
 * Switch application (Active Frames), Notifications and Quick Settings. The trackpad moves
 * through them and Enter or a click chooses; Back closes it. On the home the app rows are left
 * out. Added by the SableOS flavor (sable-src/apps/r8/tenseven).
 */
class SableSystemMenuActivity : Activity() {
    private enum class Row { Hub, AppMenu, Close, Switch, Notifications, QuickSettings }

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val front = frontTask()
        val appName = front?.let { appLabel(it) }
        val rows =
            if (front == null) {
                listOf(Row.Hub, Row.Switch, Row.Notifications, Row.QuickSettings)
            } else {
                Row.entries.toList()
            }
        val labels =
            rows.map { row ->
                when (row) {
                    Row.Hub -> "Hub"
                    Row.AppMenu -> "$appName menu"
                    Row.Close -> "Close $appName"
                    Row.Switch -> "Switch Application"
                    Row.Notifications -> "Notifications"
                    Row.QuickSettings -> "Quick Settings"
                }
            }
        dialog =
            AlertDialog
                .Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setItems(labels.toTypedArray()) { _, which -> choose(rows[which], front) }
                .setOnCancelListener { finish() }
                .create()
                .also { it.show() }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    /** The app in front when the menu opened: the task under this one, unless it is the home. */
    private fun frontTask(): ActivityManager.RunningTaskInfo? =
        runCatching {
            @Suppress("DEPRECATION")
            getSystemService(ActivityManager::class.java)
                .getRunningTasks(TASKS)
                .firstOrNull { it.taskId != taskId }
                ?.takeIf { info ->
                    val pkg = info.topActivity?.packageName
                    pkg != null && pkg != packageName && pkg != SYSTEM_UI
                }
        }.getOrNull()

    private fun appLabel(task: ActivityManager.RunningTaskInfo): String {
        val pkg = task.topActivity?.packageName.orEmpty()
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun choose(
        row: Row,
        front: ActivityManager.RunningTaskInfo?,
    ) {
        finish()
        when (row) {
            Row.Hub -> {
                runCatching {
                    startActivity(
                        Intent(this, SableHubPeekActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }

            Row.AppMenu -> {
                sendMenuKeyAfterClosing()
            }

            Row.Close -> {
                front?.let { RecentTasksRepository(applicationContext).close(it.taskId) }
            }

            Row.Switch -> {
                runCatching {
                    startActivity(
                        Intent(ACTION_ACTIVE_FRAMES).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }

            Row.Notifications -> {
                statusBar("expandNotificationsPanel")
            }

            Row.QuickSettings -> {
                statusBar("expandSettingsPanel")
            }
        }
    }

    /** Menu for the app, once it is in front again (INJECT_EVENTS; the home is platform-signed). */
    private fun sendMenuKeyAfterClosing() {
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                runCatching { Instrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU) }
                    .onFailure { Log.w(TAG, "menu key failed: $it") }
            }.start()
        }, MENU_DELAY_MS)
    }

    /** The shade: StatusBarManager's expand calls (EXPAND_STATUS_BAR). */
    private fun statusBar(method: String) {
        runCatching {
            val manager = getSystemService("statusbar") ?: return@runCatching
            manager.javaClass.getMethod(method).invoke(manager)
        }.onFailure { Log.w(TAG, "$method failed: $it") }
    }

    private companion object {
        const val TAG = "SableSystemMenu"
        const val TASKS = 3
        const val MENU_DELAY_MS = 300L
        const val SYSTEM_UI = "com.android.systemui"
        const val ACTION_ACTIVE_FRAMES = "org.sableos.start.action.SHOW_ACTIVE_FRAMES"
    }
}
