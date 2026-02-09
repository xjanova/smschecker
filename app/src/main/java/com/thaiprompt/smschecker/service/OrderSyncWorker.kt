package com.thaiprompt.smschecker.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.OrphanTransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class OrderSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val orderRepository: OrderRepository,
    private val orphanRepository: OrphanTransactionRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()

        return try {
            // 0. Sync FCM token if needed
            syncFcmTokenIfNeeded()

            // 1. Push offline queued actions first (PARALLEL)
            orderRepository.syncOfflineQueue()

            // 2. Pull server changes (PARALLEL - version-based sync)
            orderRepository.pullServerChanges()

            // 3. Fetch latest orders list (PARALLEL)
            orderRepository.fetchOrders()

            // 4. Check orphan transactions for matching with new orders
            checkOrphansForNewOrders()

            // 5. Cleanup old orphan transactions
            runOrphanCleanup()

            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Sync completed in ${duration}ms")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * Check if any orphan transactions match newly received orders.
     */
    private suspend fun checkOrphansForNewOrders() {
        try {
            val pendingOrders = orderRepository.getPendingOrdersList()
            if (pendingOrders.isEmpty()) return

            val matches = orphanRepository.findMatchesForOrders(pendingOrders)

            for ((orderId, orphan) in matches) {
                val order = pendingOrders.find { it.id == orderId } ?: continue

                Log.i(TAG, "Found orphan match: order=${order.id}, orphan=${orphan.id}, amount=${orphan.amount}")

                // Auto-approve the order
                val approved = orderRepository.approveOrder(orderId)
                if (approved) {
                    // Mark orphan as matched
                    orphanRepository.markAsMatched(orphan.id, orderId, order.serverId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check orphans for new orders", e)
        }
    }

    /**
     * Cleanup old orphan transactions.
     */
    private suspend fun runOrphanCleanup() {
        try {
            val result = orphanRepository.runCleanup()
            if (result.expiredCount > 0 || result.deletedCount > 0) {
                Log.d(TAG, "Orphan cleanup: expired=${result.expiredCount}, deleted=${result.deletedCount}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Orphan cleanup failed", e)
        }
    }

    /**
     * ส่ง FCM token ไปเซิร์ฟเวอร์ถ้ายังไม่ได้ส่ง
     */
    private suspend fun syncFcmTokenIfNeeded() {
        val prefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val needsSync = prefs.getBoolean("fcm_token_needs_sync", false)
        val fcmToken = prefs.getString(FcmService.FCM_TOKEN_KEY, null)

        Log.d(TAG, "syncFcmTokenIfNeeded: needsSync=$needsSync, hasToken=${fcmToken != null}, tokenLength=${fcmToken?.length ?: 0}")

        if (needsSync && fcmToken != null) {
            try {
                Log.i(TAG, "syncFcmTokenIfNeeded: Sending FCM token to servers...")
                orderRepository.registerFcmToken(fcmToken)
                prefs.edit().putBoolean("fcm_token_needs_sync", false).apply()
                Log.i(TAG, "syncFcmTokenIfNeeded: FCM token sent successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "syncFcmTokenIfNeeded: FAILED to send FCM token: ${e.javaClass.simpleName}: ${e.message}", e)
                // Will retry next sync
            }
        }
    }

    companion object {
        private const val TAG = "OrderSyncWorker"
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
