package org.sableos.start.platform

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Bundle
import org.sableos.start.model.ActiveFrame
import org.sableos.start.model.ActiveFrames

/**
 * Active Frames from Android's recent tasks.
 *
 * SableLauncher is platform-signed, so it holds what Launcher3 Quickstep uses
 * for Recents: REAL_GET_TASKS (every app's recent tasks through the public
 * [ActivityManager.getRecentTasks]), REORDER_TASKS ([ActivityManager.moveTaskToFront]),
 * REMOVE_TASKS and START_TASKS_FROM_RECENTS (IActivityTaskManager
 * removeTask / startActivityFromRecents) and READ_FRAME_BUFFER
 * (IActivityTaskManager getTaskSnapshot, the frame's live picture).
 * Platform-signed apps may call those hidden methods; they are reached by
 * reflection so this file builds against the public SDK, and every call
 * fails soft: no snapshot shows the app's summary instead, a close that does
 * not work leaves the task in Recents.
 *
 * Every method does binder calls: call it off the main thread.
 */
class RecentTasksRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)

    /** Most recent first, without Start, Recents and System UI, at most [ActiveFrames.MAX]. */
    fun loadFrames(): List<ActiveFrame> {
        val tasks =
            runCatching {
                @Suppress("DEPRECATION")
                activityManager?.getRecentTasks(QUERY_LIMIT, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            }.getOrNull().orEmpty()
        val frames =
            tasks.mapNotNull { info ->
                val component = info.topActivity ?: info.baseActivity ?: info.baseIntent.component
                val packageName = component?.packageName ?: return@mapNotNull null
                ActiveFrame(
                    taskId = info.taskId,
                    packageName = packageName,
                    className = component.className,
                    userId = userIdOf(info),
                    running = info.isRunning,
                )
            }
        return ActiveFrames.select(frames, excludedPackages + appContext.packageName)
    }

    /** The task's last picture (low resolution first), or null when Android has none for it. */
    fun snapshot(taskId: Int): Bitmap? = snapshot(taskId, lowResolution = true) ?: snapshot(taskId, lowResolution = false)

    /**
     * Brings the task back as it was; false when Android refused (the caller then launches the app).
     * SableLauncher's manifest declares REORDER_TASKS; the presentation check module has no manifest for it.
     */
    @SuppressLint("MissingPermission")
    fun resume(taskId: Int): Boolean {
        val fromRecents =
            runCatching {
                val service = taskService() ?: return@runCatching false
                val method =
                    service.javaClass.methods.firstOrNull {
                        it.name == "startActivityFromRecents" && it.parameterTypes.size == 2
                    } ?: return@runCatching false
                val result = method.invoke(service, taskId, null as Bundle?) as? Int ?: return@runCatching false
                result >= 0
            }.getOrDefault(false)
        if (fromRecents) return true
        return runCatching {
            activityManager?.moveTaskToFront(taskId, 0) ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** Removes the task from Recents and stops it, like swiping it away in Recents. */
    fun close(taskId: Int): Boolean =
        runCatching {
            val service = taskService() ?: return@runCatching false
            val method =
                service.javaClass.methods.firstOrNull {
                    it.name == "removeTask" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                } ?: return@runCatching false
            method.invoke(service, taskId) as? Boolean ?: true
        }.getOrDefault(false)

    private fun snapshot(
        taskId: Int,
        lowResolution: Boolean,
    ): Bitmap? =
        runCatching {
            val service = taskService() ?: return@runCatching null
            val method =
                service.javaClass.methods.firstOrNull {
                    it.name == "getTaskSnapshot" &&
                        it.parameterTypes.isNotEmpty() &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        it.parameterTypes.drop(1).all { type -> type == Boolean::class.javaPrimitiveType }
                } ?: return@runCatching null
            // (taskId, isLowResolution) on Android 14+; older releases add takeSnapshotIfNeeded (false).
            val flags: List<Any> = method.parameterTypes.drop(1).mapIndexed { index, _ -> index == 0 && lowResolution }
            val args: Array<Any> = (listOf<Any>(taskId) + flags).toTypedArray()
            val snapshot = method.invoke(service, *args) ?: return@runCatching null
            val buffer =
                snapshot.javaClass.getMethod("getHardwareBuffer").invoke(snapshot) as? HardwareBuffer
                    ?: return@runCatching null
            val colorSpace =
                runCatching { snapshot.javaClass.getMethod("getColorSpace").invoke(snapshot) as? ColorSpace }.getOrNull()
                    ?: ColorSpace.get(ColorSpace.Named.SRGB)
            Bitmap.wrapHardwareBuffer(buffer, colorSpace)
        }.getOrNull()

    private fun taskService(): Any? =
        runCatching {
            Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        }.getOrNull()

    /** TaskInfo.userId is hidden; without it the frame belongs to Start's own user. */
    private fun userIdOf(info: ActivityManager.RecentTaskInfo): Int =
        runCatching { info.javaClass.getField("userId").getInt(info) }
            .getOrElse { android.os.Process.myUserHandle().hashCode() }

    /** The app a frame stands for, when the task cannot be resumed any more. */
    fun launchIntent(frame: ActiveFrame): Intent? =
        appContext.packageManager.getLaunchIntentForPackage(frame.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        /** More than [ActiveFrames.MAX], since some recent tasks are filtered out. */
        const val QUERY_LIMIT = 16

        val excludedPackages: Set<String> =
            setOf(
                // Launcher3QuickStep: Android's Recents for Sable Start.
                "com.android.launcher3",
                "com.android.systemui",
            )
    }
}
