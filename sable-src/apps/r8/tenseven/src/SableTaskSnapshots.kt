package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.util.Log

/**
 * SableOS: Active Frames' live pictures straight from the system task snapshot, with no screen-capture
 * consent. The home is platform-signed on SableOS and holds READ_FRAME_BUFFER and MANAGE_ACTIVITY_TASKS.
 * Android 17 moved snapshots from IActivityTaskManager into android.window.TaskSnapshotManager
 * (getTaskSnapshot(taskId, resolution); RESOLUTION_HIGH 1, RESOLUTION_LOW 2, RESOLUTION_ANY 3); TenSeven's own bridge
 * still tries the older IActivityTaskManager call after this one. Each failure is logged once under
 * the tag SableFrames, so `adb logcat -s SableFrames` says why a frame has no picture. Added by the
 * SableOS flavor (sable-src/apps/r8/tenseven).
 */
internal object SableTaskSnapshots {
    private const val TAG = "SableFrames"
    private const val RESOLUTION_HIGH = 1
    private const val RESOLUTION_LOW = 2
    private const val RESOLUTION_ANY = 3
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

    /**
     * The TaskSnapshotManager call: getTaskSnapshot(taskId, resolution) on Android 17, found by name so
     * an added flag (a boolean or another int) does not hide it.
     */
    private val method: java.lang.reflect.Method? by lazy {
        val type = managerClass ?: return@lazy null
        val found =
            type.methods.filter {
                it.name == "getTaskSnapshot" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType &&
                    it.parameterTypes.drop(1).all { p -> p == Int::class.javaPrimitiveType || p == Boolean::class.javaPrimitiveType }
            }.minByOrNull { it.parameterCount }
        if (found == null) {
            val names = type.methods.filter { it.name.contains("Snapshot") }.joinToString { it.toGenericString() }
            Log.w(TAG, "no usable TaskSnapshotManager.getTaskSnapshot; has: $names")
        }
        found
    }

    /** True when this Android has the TaskSnapshotManager call. */
    fun available(): Boolean = method != null

    /** Logs [message] once per [key]. */
    fun note(
        key: String,
        message: String,
    ) {
        if (synchronized(logged) { logged.add(key) }) Log.i(TAG, message)
    }

    /** Once: why the home's frames have no live picture (`adb logcat -s SableFrames`). */
    fun explain(context: Context) {
        if (!synchronized(logged) { logged.add("explain") }) return
        val held =
            listOf("READ_FRAME_BUFFER", "REAL_GET_TASKS", "MANAGE_ACTIVITY_TASKS").joinToString { name ->
                val ok = context.checkSelfPermission("android.permission.$name") == PackageManager.PERMISSION_GRANTED
                "$name=" + if (ok) "granted" else "MISSING"
            }
        Log.i(TAG, "snapshots: $held, TaskSnapshotManager=${if (available()) "ok" else "missing"}, sdk=${Build.VERSION.SDK_INT}")
    }

    fun snapshot(
        taskId: Int,
        lowResolution: Boolean,
    ): Bitmap? {
        val wanted = if (lowResolution) RESOLUTION_LOW else RESOLUTION_HIGH
        // Android 17 may keep only one resolution of a task's picture: ask for the wanted one, then any.
        return pictureAt(taskId, wanted) ?: pictureAt(taskId, RESOLUTION_ANY)
    }

    private fun pictureAt(
        taskId: Int,
        resolution: Int,
    ): Bitmap? =
        runCatching {
            val call = method ?: return@runCatching null
            val manager = manager() ?: return@runCatching null
            val args =
                listOf<Any>(taskId) +
                    call.parameterTypes.drop(1).mapIndexed { i, type ->
                        if (type == Boolean::class.javaPrimitiveType) false else if (i == 0) resolution else 0
                    }
            val snapshot = call.invoke(manager, *args.toTypedArray())
            if (snapshot == null) {
                note("null:$resolution", "${call.name}(task $taskId, resolution $resolution) returned no snapshot")
                return@runCatching null
            }
            bitmapOf(snapshot) ?: null.also {
                note("nobuffer:$resolution", "snapshot of task $taskId (resolution $resolution) has no picture: ${describe(snapshot)}")
            }
        }.onFailure { log("TaskSnapshotManager.getTaskSnapshot", it) }
            .getOrNull()

    /** What a snapshot without a usable picture holds, for the log. */
    private fun describe(snapshot: Any): String =
        listOf("isBufferValid", "isRealSnapshot", "isLowResolution", "getHardwareBuffer").joinToString { name ->
            "$name=" + runCatching { snapshot.javaClass.getMethod(name).invoke(snapshot) }.getOrElse { "?" }
        }

    /**
     * A software copy of the snapshot's picture. Android 17's TaskSnapshot wraps its own buffer
     * (wrapToBitmap) and shares it with TaskSnapshotManager's cache, so the buffer is never closed here:
     * closing it left the cached snapshot empty for every later frame. The copy is released with
     * closeBuffer(), which lets the cache drop it properly.
     */
    private fun bitmapOf(snapshot: Any): Bitmap? {
        val type = snapshot.javaClass
        val valid = runCatching { type.getMethod("isBufferValid").invoke(snapshot) as? Boolean }.getOrNull()
        if (valid == false) return null
        val colorSpace =
            runCatching { type.getMethod("getColorSpace").invoke(snapshot) as? ColorSpace }.getOrNull()
                ?: ColorSpace.get(ColorSpace.Named.SRGB)
        val hardware =
            runCatching { type.getMethod("wrapToBitmap", ColorSpace::class.java).invoke(snapshot, colorSpace) as? Bitmap }
                .getOrNull()
                ?: runCatching {
                    val buffer = type.getMethod("getHardwareBuffer").invoke(snapshot) as? HardwareBuffer
                    buffer?.takeUnless { it.isClosed }?.let { Bitmap.wrapHardwareBuffer(it, colorSpace) }
                }.getOrNull()
                ?: return null
        val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
        hardware.recycle()
        runCatching { type.getMethod("closeBuffer").invoke(snapshot) }
        if (software == null) return null
        return if (software.width <= MAX_EDGE && software.height <= MAX_EDGE) {
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

    private fun log(
        what: String,
        error: Throwable,
    ) {
        val cause = (error.cause ?: error).toString()
        if (synchronized(logged) { logged.add(what + cause) }) Log.w(TAG, "$what failed: $cause")
    }
}
