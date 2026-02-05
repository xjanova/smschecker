package com.thaiprompt.smschecker.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.data.api.WebSocketManager
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.OrphanTransactionRepository
import com.thaiprompt.smschecker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground Service for maintaining real-time sync connections.
 *
 * Features:
 * - Keeps WebSocket connections alive even when app is in background
 * - Prevents Android from killing the sync process
 * - Shows persistent notification for user awareness
 * - Auto-restarts on crash or system kill
 * - Network-aware: reconnects when network changes
 * - Battery-optimized: uses WakeLock sparingly
 *
 * This service is CRITICAL for:
 * - Real-time order notifications
 * - Instant SMS/notification processing
 * - Orphan transaction recovery
 */
@AndroidEntryPoint
class RealtimeSyncService : Service() {

    companion object {
        private const val TAG = "RealtimeSyncService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "realtime_sync_channel"
        private const val CHANNEL_NAME = "Real-time Sync"

        private const val WAKELOCK_TAG = "SmsChecker:RealtimeSyncWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes max

        // Actions
        const val ACTION_START = "com.thaiprompt.smschecker.action.START_SYNC"
        const val ACTION_STOP = "com.thaiprompt.smschecker.action.STOP_SYNC"
        const val ACTION_RECONNECT = "com.thaiprompt.smschecker.action.RECONNECT"

        // Intent extras
        const val EXTRA_FROM_BOOT = "from_boot"

        fun start(context: Context, fromBoot: Boolean = false) {
            val intent = Intent(context, RealtimeSyncService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FROM_BOOT, fromBoot)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RealtimeSyncService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }

        fun reconnect(context: Context) {
            val intent = Intent(context, RealtimeSyncService::class.java).apply {
                action = ACTION_RECONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    @Inject
    lateinit var webSocketManager: WebSocketManager

    @Inject
    lateinit var orderRepository: OrderRepository

    @Inject
    lateinit var orphanRepository: OrphanTransactionRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false

    // Periodic sync job
    private var periodicSyncJob: Job? = null
    private val PERIODIC_SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes (more aggressive than WorkManager)

    // Orphan cleanup job
    private var orphanCleanupJob: Job? = null
    private val ORPHAN_CLEANUP_INTERVAL_MS = 60 * 60 * 1000L // 1 hour

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForegroundWithNotification()
                    startRealTimeSync()
                    isRunning = true
                }
            }
            ACTION_STOP -> {
                stopRealTimeSync()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
            }
            ACTION_RECONNECT -> {
                if (isRunning) {
                    reconnectWebSockets()
                }
            }
        }

        // Restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopRealTimeSync()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed, restarting service")
        // Schedule restart when user swipes away app
        val restartIntent = Intent(this, RealtimeSyncService::class.java).apply {
            action = ACTION_START
        }
        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000,
            pendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    // =====================================================================
    // Real-time Sync Management
    // =====================================================================

    private fun startRealTimeSync() {
        Log.i(TAG, "Starting real-time sync")

        // Acquire partial wake lock (allows CPU to run)
        acquireWakeLock()

        // Initialize and connect WebSocket
        webSocketManager.initialize()
        webSocketManager.connectAll()

        // Listen for WebSocket messages
        webSocketManager.addMessageListener { serverId, message ->
            handleWebSocketMessage(serverId, message)
        }

        // Start periodic sync (backup for WebSocket)
        startPeriodicSync()

        // Start orphan cleanup
        startOrphanCleanup()

        // Initial sync
        serviceScope.launch {
            try {
                orderRepository.fetchOrders()
                orderRepository.pullServerChanges()
            } catch (e: Exception) {
                Log.e(TAG, "Initial sync failed", e)
            }
        }

        updateNotification("Connected", "Listening for new orders")
    }

    private fun stopRealTimeSync() {
        Log.i(TAG, "Stopping real-time sync")

        periodicSyncJob?.cancel()
        orphanCleanupJob?.cancel()
        webSocketManager.disconnectAll()
        serviceScope.cancel()
        releaseWakeLock()
    }

    private fun reconnectWebSockets() {
        Log.d(TAG, "Reconnecting WebSockets")
        webSocketManager.connectAll()
    }

    private fun handleWebSocketMessage(serverId: Long, message: WebSocketManager.WebSocketMessage) {
        Log.d(TAG, "WebSocket message from server $serverId: ${message.type}")

        serviceScope.launch {
            when (message.type) {
                "new_order" -> {
                    // New order received - trigger sync
                    orderRepository.fetchOrders()
                    checkOrphansForNewOrders()
                    showNewOrderNotification()
                }
                "order_update" -> {
                    // Order status changed
                    orderRepository.pullServerChanges()
                }
                "sync_request" -> {
                    // Server requested a sync
                    orderRepository.fetchOrders()
                    orderRepository.pullServerChanges()
                }
                "ping" -> {
                    // Heartbeat - connection is alive
                    updateNotification("Connected", "Last ping: ${System.currentTimeMillis()}")
                }
            }
        }
    }

    // =====================================================================
    // Periodic Sync (Backup for WebSocket failures)
    // =====================================================================

    private fun startPeriodicSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = serviceScope.launch {
            while (isActive) {
                delay(PERIODIC_SYNC_INTERVAL_MS)
                try {
                    Log.d(TAG, "Running periodic sync")
                    orderRepository.syncOfflineQueue()
                    orderRepository.pullServerChanges()
                    orderRepository.fetchOrders()
                    checkOrphansForNewOrders()
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic sync failed", e)
                }
            }
        }
    }

    // =====================================================================
    // Orphan Transaction Recovery
    // =====================================================================

    private fun startOrphanCleanup() {
        orphanCleanupJob?.cancel()
        orphanCleanupJob = serviceScope.launch {
            while (isActive) {
                delay(ORPHAN_CLEANUP_INTERVAL_MS)
                try {
                    val result = orphanRepository.runCleanup()
                    if (result.expiredCount > 0 || result.deletedCount > 0) {
                        Log.d(TAG, "Orphan cleanup: expired=${result.expiredCount}, deleted=${result.deletedCount}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Orphan cleanup failed", e)
                }
            }
        }
    }

    /**
     * Check if any orphan transactions match newly received orders.
     * This is the KEY function for recovering "lost" decimal payments.
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
                    showOrphanMatchNotification(orphan.amount, order.orderNumber)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check orphans for new orders", e)
        }
    }

    // =====================================================================
    // Notifications
    // =====================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time sync status"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Starting", "Connecting to servers...")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showNewOrderNotification() {
        // This would show a separate notification for new orders
        // Implementation depends on your notification preferences
    }

    private fun showOrphanMatchNotification(amount: Double, orderNumber: String?) {
        val content = "ยอด %.2f บาท จับคู่กับออเดอร์ ${orderNumber ?: "ใหม่"} แล้ว".format(amount)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("💰 พบยอดโอนที่รอจับคู่")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_check)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // =====================================================================
    // Wake Lock Management
    // =====================================================================

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                acquire(WAKELOCK_TIMEOUT_MS)
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }
}
