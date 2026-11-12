package io.github.aimindseye.tenseven.platform

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.util.Log

/**
 * SableOS: Active Frames' live pictures straight from the system task snapshot, with no screen-capture
 * consent. The home is platform-signed on SableOS and holds READ_FRAME_BUFFER and MANAGE_ACTIVITY_TASKS.
 * Android 17 moved snapshots from IActivityTaskManager into android.window.TaskSnapshotManager
 * (getTaskSnapshot(taskId, resolution); RESOLUTION_HIGH 1, RESOLUTION_LOW 2); TenSeven's own bridge
 * still tries the older IActivityTaskManager call after this one. Each failure is logged once under
 * the tag SableFrames, so `adb logcat -s SableFrames` says why a frame has no picture. Added by the
 * SableOS flavor (sable-src/apps/r8/tenseven).
 */
internal object SableTaskSnapshots {
    private const val TAG = "SableFrames"
    private const val RESOLUTION_HIGH = 1
    private const val RESOLUTION_LOW = 2
    private const val MAX_EDGE = 512
    private val logged = mutableSetOf<String>()

    private val managerClass: Class<*>? by lazy {
        runCatching { Class.forName("android.window.TaskSnapshotManager") }
            .onFailure { log("no TaskSnapshotManager", it) }
            .getOrNull()
    }

    private fun manager(): Any? {
        val type = managerClass ?: return null
        return runCatching { type.getMethod("getInstance").invoke(null) }
            .onFailure { log("TaskSnapshotManager.getInstance", it) }
            .getOrNull()
    }

    /** True when this Android has the TaskSnapshotManager call. */
    fun available(): Boolean =
        runCatching {
            managerClass?.getMethod("getTaskSnapshot", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) != null
        }.getOrDefault(false)

    fun snapshot(
        taskId: Int,
        lowResolution: Boolean,
    ): Bitmap? =
        runCatching {
            val type = managerClass ?: return@runCatching null
            val manager = manager() ?: return@runCatching null
            val method = type.getMethod("getTaskSnapshot", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val snapshot =
                method.invoke(manager, taskId, if (lowResolution) RESOLUTION_LOW else RESOLUTION_HIGH)
                    ?: return@runCatching null
            bitmapOf(snapshot)
        }.onFailure { log("TaskSnapshotManager.getTaskSnapshot", it) }
            .getOrNull()

    private fun bitmapOf(snapshot: Any): Bitmap? {
        val buffer = snapshot.javaClass.getMethod("getHardwareBuffer").invoke(snapshot) as? HardwareBuffer ?: return null
        val colorSpace =
            runCatching { snapshot.javaClass.getMethod("getColorSpace").invoke(snapshot) as? ColorSpace }.getOrNull()
                ?: ColorSpace.get(ColorSpace.Named.SRGB)
        return buffer.use {
            val hardware = Bitmap.wrapHardwareBuffer(it, colorSpace) ?: return null
            val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            if (software.width <= MAX_EDGE && software.height <= MAX_EDGE) {
                software
            } else {
                val scale = MAX_EDGE.toFloat() / maxOf(software.width, software.height)
                Bitmap
                    .createScaledBitmap(
                        software,
                        (software.width * scale).toInt().coerceAtLeast(1),
                        (software.height * scale).toInt().coerceAtLeast(1),
                        true,
                    ).also { software.recycle() }
            }
        }
    }

    private fun log(
        what: String,
        error: Throwable,
    ) {
        val cause = (error.cause ?: error).toString()
        if (synchronized(logged) { logged.add(what + cause) }) Log.w(TAG, "$what failed: $cause")
    }
}
