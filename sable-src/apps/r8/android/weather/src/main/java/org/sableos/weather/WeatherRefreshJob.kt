package org.sableos.weather

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Binder

/**
 * Fetches the active city's forecast in the background, so Start and Live show the weather without
 * Sable Weather being opened first. A periodic job keeps the cache fresh; a one-off job fills an empty
 * or stale cache as soon as a network is available. A successful refresh notifies the snapshot URI.
 */
class WeatherRefreshJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread({
            val repository = WeatherRepository(applicationContext)
            val result = runCatching { repository.refresh(repository.loadLocation()) }.getOrNull()
            val fetched = result?.availability == WeatherAvailability.Live
            // A failed one-off fetch is retried with back-off; the periodic job just waits for its next run.
            jobFinished(params, !fetched && params.jobId == ONE_OFF_JOB_ID)
        }, "sable-weather-refresh").start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val PERIODIC_JOB_ID = 1
        private const val ONE_OFF_JOB_ID = 2
        private const val PERIOD_MS = 3L * 60 * 60 * 1000

        /** Keeps the periodic refresh scheduled, and asks for a fetch now when nothing current is cached. */
        fun ensureScheduled(context: Context, needsFetch: Boolean) {
            val token = Binder.clearCallingIdentity()
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                val component = ComponentName(context, WeatherRefreshJob::class.java)
                if (scheduler.getPendingJob(PERIODIC_JOB_ID) == null) {
                    scheduler.schedule(
                        JobInfo.Builder(PERIODIC_JOB_ID, component)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setPeriodic(PERIOD_MS)
                            .setPersisted(true)
                            .build(),
                    )
                }
                if (needsFetch && scheduler.getPendingJob(ONE_OFF_JOB_ID) == null) {
                    scheduler.schedule(
                        JobInfo.Builder(ONE_OFF_JOB_ID, component)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .build(),
                    )
                }
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }
    }
}
