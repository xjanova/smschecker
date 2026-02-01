package com.thaiprompt.smschecker.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thaiprompt.smschecker.data.repository.OrderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class OrderSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val orderRepository: OrderRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 0. Sync FCM token if needed
            syncFcmTokenIfNeeded()

            // 1. Push offline queued actions first
            orderRepository.syncOfflineQueue()

            // 2. Pull server changes (version-based sync)
            orderRepository.pullServerChanges()

            // 3. Fetch latest orders list
            orderRepository.fetchOrders()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * ส่ง FCM token ไปเซิร์ฟเวอร์ถ้ายังไม่ได้ส่ง
     */
    private suspend fun syncFcmTokenIfNeeded() {
        val prefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val needsSync = prefs.getBoolean("fcm_token_needs_sync", false)
        val fcmToken = prefs.getString(FcmService.FCM_TOKEN_KEY, null)

        if (needsSync && fcmToken != null) {
            try {
                orderRepository.registerFcmToken(fcmToken)
                prefs.edit().putBoolean("fcm_token_needs_sync", false).apply()
            } catch (_: Exception) {
                // Will retry next sync
            }
        }
    }

    companion object {
        private const val WORK_NAME_PERIODIC = "order_sync_periodic"
        private const val WORK_NAME_ONE_TIME = "order_sync_one_time"

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<OrderSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OrderSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
