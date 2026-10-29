package org.sableos.tools.android

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.sableos.tools.core.Perm

/** Schedules the hourly step sample once the pedometer has been opened. */
object StepSampling {
    private const val JOB_ID = 3101

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, StepSampleJob::class.java))
            .setPeriodic(TimeUnit.HOURS.toMillis(1))
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }
}

/**
 * Reads the step counter once and adds the gain to today's total (StepLedger), so days fill while the pedometer is
 * closed. It does nothing without the activity-recognition permission or a step counter, and gives up after a few
 * seconds.
 */
class StepSampleJob : JobService(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private var running: JobParameters? = null
    private val giveUp = Runnable { finish(reschedule = true) }

    override fun onStartJob(params: JobParameters): Boolean {
        val sm = getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sm == null || sensor == null ||
            checkSelfPermission(Perm.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        running = params
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed(giveUp, GIVE_UP_MS)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        cleanUp()
        return true
    }

    override fun onSensorChanged(event: SensorEvent) {
        val store = StepStore(this)
        val ledger = store.ledger()
        ledger.sample(event.values[0].toLong(), LocalDate.now().toEpochDay())
        store.save(ledger)
        finish(reschedule = false)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // step counts do not depend on accuracy
    }

    private fun finish(reschedule: Boolean) {
        val params = running ?: return
        cleanUp()
        jobFinished(params, reschedule)
    }

    private fun cleanUp() {
        handler.removeCallbacks(giveUp)
        getSystemService(SensorManager::class.java)?.unregisterListener(this)
        running = null
    }

    private companion object {
        const val GIVE_UP_MS = 10_000L
    }
}
