package com.thaiprompt.smschecker.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.thaiprompt.smschecker.data.license.LicenseManager

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
         *
         * Supports 15 Thai banks:
         * - Major banks: KBANK, SCB, KTB, BBL, BAY, TTB, GSB
         * - Other banks: CIMB, KKP, LH, TISCO, UOB, ICBC, BAAC, PromptPay
         */
        val BANK_APP_PACKAGES = mapOf(
            // === Major Banks ===
            "com.kasikorn.retail.mbanking.wap" to "KBANK",  // K PLUS
            "com.scb.phone" to "SCB",                        // SCB EASY
            "com.ktb.netbank" to "KTB",                      // Krungthai NEXT
            "com.bbl.mobilebanking" to "BBL",                // Bualuang mBanking
            "com.gsb.mymo" to "GSB",                         // MyMo by GSB
            "com.krungsri.kma" to "BAY",                     // KMA (Krungsri)
            "com.ttb.touchapp" to "TTB",                     // ttb touch

            // === Other Thai Banks ===
            "com.cimb.th.digi" to "CIMB",                    // CIMB THAI Digital Banking
            "com.cimbthai.paynow" to "CIMB",                 // CIMB Thai PayNow
            "th.co.kiatnakin.kiatnakinphatra" to "KKP",      // KKP Mobile
            "com.lhbank.lhmobile" to "LH",                   // LH Bank M Choice
            "com.tisco.tiscoapp" to "TISCO",                 // TISCO My Wealth
            "com.uob.mightyth" to "UOB",                     // UOB Mighty Thailand
            "com.icbc.mobilethailand" to "ICBC",             // ICBC Mobile Banking (Thailand)
            "com.baac.baacmobile" to "BAAC",                 // BAAC Mobile (ธ.ก.ส.)
            "th.or.baac.baaconnect" to "BAAC",               // BAAC Connect

            // === PromptPay / National Payment ===
            "th.or.itmx.promptpay" to "PROMPTPAY",           // PromptPay Official App

            // === Digital Wallets (KTB ecosystem) ===
            // เป๋าตัง — KTBCS's consumer wallet (G-wallet, PromptPay, vaccine pass etc.)
            "com.ktbcs.paotang" to "PAOTANG",
            "com.ktbcs.paotangpublic" to "PAOTANG",
            "th.co.ktb.paotang" to "PAOTANG",
            // ถุงเงิน — KTBCS's merchant acceptance app (shop-side)
            "com.ktbcs.thungngern" to "THUNGNGERN",
            "com.ktbcs.merchant" to "THUNGNGERN",
            "co.th.ktbcs.thungngern" to "THUNGNGERN",

            // === LINE & LINE Pay ===
            // NOTE: LINE package fires for ALL messages, not just payments.
            // Filtering to "is this actually a transaction?" is done downstream by
            // BankSmsParser.isBankTransactionSms() which checks for financial keywords.
            "jp.naver.line.android" to "LINE",
            "com.linecorp.linepay" to "LINEPAY",
            "com.linecorp.linepayth" to "LINEPAY",
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

        // License check — don't process notifications if license expired
        if (!LicenseManager.isLicenseValid()) {
            return
        }

        // Acquire a short wake lock so the CPU stays on long enough for
        // SmsProcessingService to call startForeground() and finish the DB write.
        // Critical at night when device is in Doze — otherwise transaction can be lost.
        val wakeLock: PowerManager.WakeLock? = try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsChecker:NotifListenerWakeLock").apply {
                setReferenceCounted(false)
                acquire(30_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
            null
        }

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

            // ⚠️ LINE package fires for ALL messages (chats, groups, stickers, etc.).
            // Filter: only process LINE notifications whose title explicitly mentions payment.
            // This avoids treating casual chats like "จ่ายให้หน่อย 500" as a real transaction.
            if (bankCode == "LINE") {
                val isPaymentRelated =
                    title.contains("LINE Pay", ignoreCase = true) ||
                    title.contains("ชำระเงิน", ignoreCase = true) ||
                    title.contains("การแจ้งเตือนการชำระเงิน", ignoreCase = true) ||
                    title.contains("Payment Notification", ignoreCase = true)
                if (!isPaymentRelated) {
                    return
                }
            }

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
        } finally {
            try { if (wakeLock?.isHeld == true) wakeLock.release() } catch (_: Exception) {}
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }
}
