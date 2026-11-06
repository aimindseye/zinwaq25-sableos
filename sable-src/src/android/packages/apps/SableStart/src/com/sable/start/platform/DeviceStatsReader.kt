package org.sableos.start.platform

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import org.sableos.start.model.MonitorStats

/**
 * Device Monitor's readings: the battery from Android's sticky battery broadcast (no receiver is
 * kept), CPU from the status rings' [DeviceStatsSampler], memory from ActivityManager and storage
 * from the data partition. No permission needed. Keep one per shown frame and call [read] from one
 * coroutine at a time, off the main thread (the CPU load compares with the previous reading).
 */
class DeviceStatsReader {
    private val sampler = DeviceStatsSampler()

    fun read(context: Context): MonitorStats {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        val storage = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        return MonitorStats(
            batteryPercent = if (level >= 0 && scale > 0) level * PERCENT / scale else null,
            cpu = runCatching { sampler.sample(context).cpu }.getOrNull(),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            memoryUsedBytes = memory.totalMem - memory.availMem,
            memoryTotalBytes = memory.totalMem,
            storageFreeBytes = storage?.availableBytes ?: 0L,
            storageTotalBytes = storage?.totalBytes ?: 0L,
        )
    }

    private companion object {
        const val PERCENT = 100
    }
}
