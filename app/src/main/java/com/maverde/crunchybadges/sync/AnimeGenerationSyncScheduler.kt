package com.maverde.crunchybadges.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AnimeGenerationSyncScheduler {

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Enqueue a one-shot sync now (called on app launch). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AnimeGenerationSyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AnimeGenerationSyncWorker.UNIQUE_WORK_NAME + "_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Ensure the periodic (~12h) sync is scheduled. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<AnimeGenerationSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AnimeGenerationSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
