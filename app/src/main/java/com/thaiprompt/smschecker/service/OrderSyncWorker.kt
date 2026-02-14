package com.thaiprompt.smschecker.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thaiprompt.smschecker.BuildConfig
import com.thaiprompt.smschecker.data.api.DebugReportBody
import com.thaiprompt.smschecker.data.repository.MisclassificationReportRepository
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.OrphanTransactionRepository
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class OrderSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val orderRepository: OrderRepository,
    private val orphanRepository: OrphanTransactionRepository,
    private val misclassificationRepository: MisclassificationReportRepository,
    private val secureStorage: SecureStorage
) : CoroutineWorker(appContext, workerParams) {

    // Track FCM sync result for debug report
    private var lastFcmSyncResult: String = "not_attempted"

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()

        return try {
            // 0. Sync FCM token if needed
            syncFcmTokenIfNeeded()

            // 0.5 Send debug report to server (uses fetchOrders-like path which we know works)
            sendDebugReport()

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

            // 6. Sync misclassification reports to backend
            syncMisclassificationReports()

            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Sync completed in ${duration}ms")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * Send diagnostic data to server via a path we know works.
     * This helps debug why registerFcmToken() fails.
     */
    private suspend fun sendDebugReport() {
        try {
            val prefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            val fcmToken = prefs.getString(FcmService.FCM_TOKEN_KEY, null)
            val needsSync = prefs.getBoolean("fcm_token_needs_sync", false)
            val deviceId = secureStorage.getDeviceId()

            // Count active servers and servers with API keys
            var activeServers = 0
            var serversWithKeys = 0
            try {
                val servers = orderRepository.getActiveServerCount()
                activeServers = servers.first
                serversWithKeys = servers.second
            } catch (e: Exception) {
                Log.e(TAG, "sendDebugReport: Failed to get server count: ${e.message}")
            }

            val report = DebugReportBody(
                device_id = deviceId,
                app_version = BuildConfig.VERSION_NAME,
                fcm_token_length = fcmToken?.length ?: 0,
                fcm_token_prefix = fcmToken?.take(30),
                fcm_needs_sync = needsSync,
                active_servers_count = activeServers,
                servers_with_api_keys = serversWithKeys,
                register_fcm_result = lastFcmSyncResult,
                build_number = BuildConfig.VERSION_CODE,
                device_model = android.os.Build.MODEL,
                timestamp = System.currentTimeMillis()
            )

            orderRepository.sendDebugReport(report)
        } catch (e: Exception) {
            Log.e(TAG, "sendDebugReport: Failed: ${e.message}")
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
        Log.i(TAG, "syncFcmTokenIfNeeded: === START ===")
        val prefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val needsSync = prefs.getBoolean("fcm_token_needs_sync", false)
        val fcmToken = prefs.getString(FcmService.FCM_TOKEN_KEY, null)

        Log.i(TAG, "syncFcmTokenIfNeeded: needsSync=$needsSync, hasToken=${fcmToken != null}, tokenLength=${fcmToken?.length ?: 0}")

        if (!needsSync) {
            Log.i(TAG, "syncFcmTokenIfNeeded: needsSync=false, skipping (flag not set)")
            lastFcmSyncResult = "skipped:needsSync=false"
            return
        }

        if (fcmToken == null) {
            Log.w(TAG, "syncFcmTokenIfNeeded: fcmToken is NULL! Firebase may not have initialized yet.")
            lastFcmSyncResult = "token_null_trying_direct"
            // ลองดึง token ใหม่จาก Firebase โดยตรง
            try {
                val tasks = com.google.android.gms.tasks.Tasks.await(
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token,
                    10, java.util.concurrent.TimeUnit.SECONDS
                )
                if (tasks != null) {
                    Log.i(TAG, "syncFcmTokenIfNeeded: Got FCM token directly! length=${tasks.length}")
                    prefs.edit().putString(FcmService.FCM_TOKEN_KEY, tasks).apply()
                    // ใช้ token นี้ต่อ
                    sendFcmToken(prefs, tasks)
                } else {
                    Log.w(TAG, "syncFcmTokenIfNeeded: Direct token fetch returned null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncFcmTokenIfNeeded: Direct token fetch failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }
            return
        }

        sendFcmToken(prefs, fcmToken)
    }

    /**
     * ส่ง FCM token ไปเซิร์ฟเวอร์จริง
     */
    private suspend fun sendFcmToken(prefs: android.content.SharedPreferences, fcmToken: String) {
        try {
            Log.i(TAG, "sendFcmToken: Calling orderRepository.registerFcmToken()...")
            val sent = orderRepository.registerFcmToken(fcmToken)
            Log.i(TAG, "sendFcmToken: registerFcmToken returned: $sent")
            if (sent) {
                prefs.edit().putBoolean("fcm_token_needs_sync", false).apply()
                Log.i(TAG, "sendFcmToken: ✅ FCM token sent successfully! Flag cleared.")
                lastFcmSyncResult = "success"
            } else {
                Log.w(TAG, "sendFcmToken: ❌ registerFcmToken returned false, will retry next sync")
                lastFcmSyncResult = "returned_false"
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendFcmToken: 💥 EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
            lastFcmSyncResult = "exception:${e.javaClass.simpleName}:${e.message}"
        }
    }

    /**
     * Sync misclassification reports to xman4289.com backend.
     * Reports are created locally when users long-press a transaction
     * and report SMS parsing issues. This syncs them in batch.
     */
    private suspend fun syncMisclassificationReports() {
        try {
            val unsyncedCount = misclassificationRepository.getUnsyncedCount()
            if (unsyncedCount == 0) return

            Log.d(TAG, "Syncing $unsyncedCount misclassification reports to backend...")
            val (success, failed) = misclassificationRepository.syncReportsToBackend()
            Log.d(TAG, "Misclassification sync: success=$success, failed=$failed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync misclassification reports", e)
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
