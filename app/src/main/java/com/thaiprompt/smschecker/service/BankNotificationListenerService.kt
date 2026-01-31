package com.thaiprompt.smschecker.service

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens for push notifications from Thai banking apps and forwards
 * detected bank notifications to SmsProcessingService for parsing,
 * saving, and syncing — using the same pipeline as SMS messages.
 *
 * Requires "Notification Access" permission to be granted by the user
 * in Android Settings.
 */
class BankNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotifListener"

        /**
         * Maps Thai banking app package names to bank codes.
         * These codes match the ones used by BankSmsParser.
         */
        val BANK_APP_PACKAGES = mapOf(
            "com.kasikorn.retail.mbanking.wap" to "KBANK",  // K PLUS
            "com.scb.phone" to "SCB",                        // SCB EASY
            "com.ktb.netbank" to "KTB",                      // Krungthai NEXT
            "com.bbl.mobilebanking" to "BBL",                // Bualuang mBanking
            "com.gsb.mymo" to "GSB",                         // MyMo
            "com.krungsri.kma" to "BAY",                     // KMA
            "com.ttb.touchapp" to "TTB",                     // ttb touch
        )

        /**
         * Creates a synthetic sender address for notification-sourced transactions.
         * The format "KBANK-NOTIF" still matches "KBANK" in BankSmsParser's
         * contains() check, so no parser changes are needed.
         */
        fun getSyntheticSender(bankCode: String): String = "${bankCode}-NOTIF"
    }

    // Simple deduplication cache: notification key -> timestamp
    private val recentNotifications = LinkedHashMap<String, Long>(50, 0.75f, true)
    private val DEDUP_WINDOW_MS = 5_000L // 5 seconds

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: return
            val bankCode = BANK_APP_PACKAGES[packageName] ?: return

            // Deduplication check
            val notifKey = "${sbn.key}_${sbn.postTime}"
            val now = System.currentTimeMillis()
            synchronized(recentNotifications) {
                // Clean old entries
                recentNotifications.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }
                if (recentNotifications.containsKey(notifKey)) {
                    Log.d(TAG, "Duplicate notification from $packageName, skipping")
                    return
                }
                recentNotifications[notifKey] = now
            }

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString()

            // Use bigText if available (more detail), otherwise text
            val messageBody = bigText ?: text
            if (messageBody.isBlank()) return

            // Combine title + text as the synthetic message
            val combinedMessage = if (title.isNotBlank()) "$title\n$messageBody" else messageBody
            val syntheticSender = getSyntheticSender(bankCode)
            val timestamp = sbn.postTime

            Log.d(TAG, "Bank notification from $packageName ($bankCode): $title")

            // Forward to SmsProcessingService
            val intent = Intent(this, SmsProcessingService::class.java).apply {
                action = SmsProcessingService.ACTION_PROCESS_NOTIFICATION
                putExtra(SmsProcessingService.EXTRA_SENDER, syntheticSender)
                putExtra(SmsProcessingService.EXTRA_MESSAGE, combinedMessage)
                putExtra(SmsProcessingService.EXTRA_TIMESTAMP, timestamp)
                putExtra(SmsProcessingService.EXTRA_SOURCE_PACKAGE, packageName)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification from ${sbn.packageName}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }
}
