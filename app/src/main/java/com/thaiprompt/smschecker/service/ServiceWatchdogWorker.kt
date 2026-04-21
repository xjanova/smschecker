package com.thaiprompt.smschecker.service

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thaiprompt.smschecker.data.license.LicenseManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic watchdog that ensures RealtimeSyncService is alive.
 *
 * WHY:
 *  - Android may kill foreground services under memory pressure or battery optimizer.
 *  - START_STICKY restart is not guaranteed in Doze mode.
 *  - FCM high-priority push is reliable but won't revive a dead WakeLock / polling loop.
 *
 * Runs every ~15 minutes (WorkManager minimum). If the service is missing, start it.
 * Also re-enqueues the OrderSyncWorker periodic job in case the user cleared app data.
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // No point reviving service if license is invalid — user must re-activate first
        if (!LicenseManager.isLicenseValid()) {
            Log.d(TAG, "License not valid, skipping watchdog tick")
            return Result.success()
        }

        val alive = isServiceRunning(applicationContext, RealtimeSyncService::class.java.name)
        return try {
            if (!alive) {
                Log.w(TAG, "⚠️ RealtimeSyncService NOT running — restarting")
                RealtimeSyncService.start(applicationContext, fromBoot = false)
            } else {
                Log.d(TAG, "✅ RealtimeSyncService alive")
            }
            // Also make sure OrderSyncWorker periodic is scheduled (idempotent)
            OrderSyncWorker.enqueuePeriodicSync(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog failed", e)
            // Retry with exponential backoff
            Result.retry()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, className: String): Boolean {
        // getRunningServices is deprecated for OTHER apps' services but still returns YOUR OWN.
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getRunningServices(Int.MAX_VALUE).any { it.service.className == className }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query running services", e)
            false
        }
    }

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val WORK_NAME = "service_watchdog_periodic"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                // Watchdog must run even on metered networks / battery saver —
                // it's a tiny task and needed specifically when system is under pressure.
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
