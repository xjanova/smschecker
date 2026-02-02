package com.thaiprompt.smschecker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.SmsCheckerApp
import com.thaiprompt.smschecker.data.db.SmsSenderRuleDao
import com.thaiprompt.smschecker.data.model.TransactionSource
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.domain.parser.BankSmsParser
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SmsProcessingService : Service() {

    companion object {
        const val ACTION_PROCESS_SMS = "com.thaiprompt.smschecker.PROCESS_SMS"
        const val ACTION_PROCESS_NOTIFICATION = "com.thaiprompt.smschecker.PROCESS_NOTIFICATION"
        const val ACTION_START_MONITORING = "com.thaiprompt.smschecker.START_MONITORING"
        const val ACTION_SYNC_ALL = "com.thaiprompt.smschecker.SYNC_ALL"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_SOURCE_PACKAGE = "source_package"

        private const val TAG = "SmsProcessingService"
        private const val NOTIFICATION_ID = 1001
        private const val DEDUP_WINDOW_MS = 60_000L // 60 seconds dedup window

        fun enqueueWork(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    @Inject lateinit var repository: TransactionRepository
    @Inject lateinit var secureStorage: SecureStorage
    @Inject lateinit var smsSenderRuleDao: SmsSenderRuleDao
    @Inject lateinit var parser: BankSmsParser
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var orderRepository: OrderRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Session counters for notification display
    private var sessionDetectedCount = 0
    private var sessionMatchedCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification("SMS Payment Checker กำลังทำงาน"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PROCESS_SMS -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return START_STICKY
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
                val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())

                processSms(sender, message, timestamp)
            }
            ACTION_PROCESS_NOTIFICATION -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return START_STICKY
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
                val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
                val sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE) ?: ""

                processNotification(sender, message, timestamp, sourcePackage)
            }
            ACTION_SYNC_ALL -> {
                syncAllUnsynced()
            }
            ACTION_START_MONITORING -> {
                Log.d(TAG, "Monitoring service started")
            }
        }

        return START_STICKY
    }

    private fun processSms(sender: String, message: String, timestamp: Long) {
        if (!secureStorage.isMonitoringEnabled()) return

        serviceScope.launch {
            try {
                // Load custom rules
                val rules = smsSenderRuleDao.getActiveRules()
                parser.setCustomRules(rules)

                // Check if this is a bank transaction SMS
                if (!parser.isBankTransactionSms(sender, message)) {
                    Log.d(TAG, "Not a bank SMS from: $sender")
                    return@launch
                }

                // Parse the SMS
                val transaction = parser.parse(sender, message, timestamp)
                if (transaction == null) {
                    Log.w(TAG, "Failed to parse bank SMS from: $sender")
                    return@launch
                }

                Log.d(TAG, "Parsed transaction: ${transaction.bank} ${transaction.type} ${transaction.amount}")

                // Deduplication: check if same transaction was already saved (e.g. from notification)
                val isDuplicate = repository.findDuplicate(
                    bank = transaction.bank,
                    amount = transaction.amount,
                    type = transaction.type,
                    timestamp = timestamp,
                    windowMs = DEDUP_WINDOW_MS
                )
                if (isDuplicate) {
                    Log.d(TAG, "Duplicate SMS transaction detected, skipping: ${transaction.bank} ${transaction.amount}")
                    return@launch
                }

                // Save to local database
                val savedId = repository.saveTransaction(transaction)
                val savedTransaction = transaction.copy(id = savedId)
                sessionDetectedCount++

                // Update foreground notification with counters
                updateNotification("กำลังทำงาน | ตรวจจับ $sessionDetectedCount | แมท $sessionMatchedCount")

                // Try to match with orders (by amount for credit transactions)
                var matchedOrderNumber: String? = null
                if (transaction.type == TransactionType.CREDIT) {
                    try {
                        val amountDouble = transaction.amount.toDoubleOrNull()
                        if (amountDouble != null) {
                            val ordersList = orderRepository.getAllOrders().first()
                            val match = ordersList.find { order ->
                                kotlin.math.abs(order.amount - amountDouble) < 0.01
                            }
                            matchedOrderNumber = match?.orderNumber
                            if (match != null) {
                                sessionMatchedCount++
                                Log.d(TAG, "Matched transaction with order: ${match.orderNumber} (ID: ${match.id})")
                                updateNotification("กำลังทำงาน | ตรวจจับ $sessionDetectedCount | แมท $sessionMatchedCount")

                                // Auto-approve matched order
                                try {
                                    val approvalSuccess = orderRepository.approveOrder(match.id)
                                    if (approvalSuccess) {
                                        Log.d(TAG, "Successfully auto-approved order: ${match.orderNumber}")
                                        // Fetch updated orders to reflect approval status
                                        try {
                                            orderRepository.fetchOrders()
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Failed to fetch updated orders after approval", e)
                                        }
                                    } else {
                                        Log.w(TAG, "Failed to auto-approve order: ${match.orderNumber}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error auto-approving order: ${match.orderNumber}", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Order matching failed", e)
                    }
                }

                // Sync to servers
                val synced = repository.syncTransaction(savedTransaction)
                if (synced) {
                    Log.d(TAG, "Transaction synced successfully")
                    showTransactionNotification(savedTransaction)
                } else {
                    Log.w(TAG, "Transaction saved locally but sync failed")
                }

                // TTS announcement
                try {
                    ttsManager.speakTransaction(
                        bankName = transaction.bank,
                        amount = transaction.amount,
                        isCredit = transaction.type == TransactionType.CREDIT,
                        orderNumber = matchedOrderNumber
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "TTS announcement failed", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            }
        }
    }

    private fun processNotification(sender: String, message: String, timestamp: Long, sourcePackage: String) {
        if (!secureStorage.isNotificationListeningEnabled()) return

        serviceScope.launch {
            try {
                // Load custom rules
                val rules = smsSenderRuleDao.getActiveRules()
                parser.setCustomRules(rules)

                // Check if this is a bank transaction notification
                if (!parser.isBankTransactionSms(sender, message)) {
                    Log.d(TAG, "Not a bank notification from: $sender ($sourcePackage)")
                    return@launch
                }

                // Parse the notification
                val transaction = parser.parse(sender, message, timestamp)
                if (transaction == null) {
                    Log.w(TAG, "Failed to parse bank notification from: $sender")
                    return@launch
                }

                // Mark as notification source and include package info
                val notifTransaction = transaction.copy(
                    sourceType = TransactionSource.NOTIFICATION,
                    senderAddress = "$sender ($sourcePackage)"
                )

                Log.d(TAG, "Parsed notification: ${notifTransaction.bank} ${notifTransaction.type} ${notifTransaction.amount}")

                // Deduplication: check if same transaction was already saved (e.g. from SMS)
                val isDuplicate = repository.findDuplicate(
                    bank = notifTransaction.bank,
                    amount = notifTransaction.amount,
                    type = notifTransaction.type,
                    timestamp = timestamp,
                    windowMs = DEDUP_WINDOW_MS
                )
                if (isDuplicate) {
                    Log.d(TAG, "Duplicate notification transaction detected, skipping: ${notifTransaction.bank} ${notifTransaction.amount}")
                    return@launch
                }

                // Save to local database
                val savedId = repository.saveTransaction(notifTransaction)
                val savedTransaction = notifTransaction.copy(id = savedId)
                sessionDetectedCount++

                // Update foreground notification with counters
                updateNotification("กำลังทำงาน | ตรวจจับ $sessionDetectedCount | แมท $sessionMatchedCount")

                // Try to match with orders (by amount for credit transactions)
                var matchedOrderNumber: String? = null
                if (notifTransaction.type == TransactionType.CREDIT) {
                    try {
                        val amountDouble = notifTransaction.amount.toDoubleOrNull()
                        if (amountDouble != null) {
                            val ordersList = orderRepository.getAllOrders().first()
                            val match = ordersList.find { order ->
                                kotlin.math.abs(order.amount - amountDouble) < 0.01
                            }
                            matchedOrderNumber = match?.orderNumber
                            if (match != null) {
                                sessionMatchedCount++
                                Log.d(TAG, "Matched notification with order: ${match.orderNumber} (ID: ${match.id})")
                                updateNotification("กำลังทำงาน | ตรวจจับ $sessionDetectedCount | แมท $sessionMatchedCount")

                                // Auto-approve matched order
                                try {
                                    val approvalSuccess = orderRepository.approveOrder(match.id)
                                    if (approvalSuccess) {
                                        Log.d(TAG, "Successfully auto-approved order from notification: ${match.orderNumber}")
                                        // Fetch updated orders to reflect approval status
                                        try {
                                            orderRepository.fetchOrders()
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Failed to fetch updated orders after notification approval", e)
                                        }
                                    } else {
                                        Log.w(TAG, "Failed to auto-approve order from notification: ${match.orderNumber}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error auto-approving order from notification: ${match.orderNumber}", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Order matching failed for notification", e)
                    }
                }

                // Sync to servers
                val synced = repository.syncTransaction(savedTransaction)
                if (synced) {
                    Log.d(TAG, "Notification transaction synced successfully")
                    showTransactionNotification(savedTransaction)
                } else {
                    Log.w(TAG, "Notification transaction saved locally but sync failed")
                }

                // TTS announcement
                try {
                    ttsManager.speakTransaction(
                        bankName = notifTransaction.bank,
                        amount = notifTransaction.amount,
                        isCredit = notifTransaction.type == TransactionType.CREDIT,
                        orderNumber = matchedOrderNumber
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "TTS announcement failed for notification", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    private fun syncAllUnsynced() {
        serviceScope.launch {
            try {
                val count = repository.syncAllUnsynced()
                Log.d(TAG, "Synced $count transactions")
                if (count > 0) {
                    updateNotification("ซิงค์สำเร็จ $count รายการ")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing transactions", e)
            }
        }
    }

    private fun showTransactionNotification(transaction: com.thaiprompt.smschecker.data.model.BankTransaction) {
        val title = if (transaction.type == com.thaiprompt.smschecker.data.model.TransactionType.CREDIT) {
            "💰 เงินเข้า - ${transaction.bank}"
        } else {
            "💸 เงินออก - ${transaction.bank}"
        }

        val notification = NotificationCompat.Builder(this, SmsCheckerApp.NOTIFICATION_CHANNEL_TRANSACTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(transaction.getFormattedAmount())
            .setSubText("ตรวจจับ $sessionDetectedCount | แมท $sessionMatchedCount")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(transaction.id.toInt() + 2000, notification)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, SmsCheckerApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SMS Payment Checker")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(createPendingIntent())
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        ttsManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
