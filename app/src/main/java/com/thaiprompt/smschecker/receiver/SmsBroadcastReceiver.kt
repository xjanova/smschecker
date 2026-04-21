package com.thaiprompt.smschecker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.thaiprompt.smschecker.data.license.LicenseManager
import com.thaiprompt.smschecker.service.SmsProcessingService

/**
 * Receives incoming SMS broadcasts and forwards bank transaction SMS
 * to the SmsProcessingService for parsing and syncing.
 *
 * CRITICAL: acquires a short wake lock before dispatching so that CPU stays
 * awake long enough for the SmsProcessingService to call startForeground().
 * Without this, delivery during Doze/screen-off is unreliable.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        private const val WAKELOCK_TAG = "SmsChecker:SmsBroadcastWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 30_000L // 30 seconds — enough to parse + save + sync
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Acquire wake lock FIRST — before any potentially slow operation.
        // This guarantees CPU stays awake long enough to start the foreground service.
        val wakeLock = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock — SMS may be missed", e)
            null
        }

        try {
            // License check — don't process SMS if license expired
            if (!LicenseManager.isLicenseValid()) {
                Log.w(TAG, "License not valid — ignoring SMS")
                return
            }

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // Group message parts by sender (multi-part SMS)
            val grouped = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "" }

            for ((sender, parts) in grouped) {
                if (sender.isBlank()) continue

                val fullMessage = parts.joinToString("") { it.displayMessageBody ?: "" }
                val timestamp = parts.first().timestampMillis

                Log.d(TAG, "SMS received from: $sender")

                // Forward to service for processing
                val serviceIntent = Intent(context, SmsProcessingService::class.java).apply {
                    action = SmsProcessingService.ACTION_PROCESS_SMS
                    putExtra(SmsProcessingService.EXTRA_SENDER, sender)
                    putExtra(SmsProcessingService.EXTRA_MESSAGE, fullMessage)
                    putExtra(SmsProcessingService.EXTRA_TIMESTAMP, timestamp)
                }

                try {
                    SmsProcessingService.enqueueWork(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to forward SMS to SmsProcessingService (sender=$sender)", e)
                }
            }
        } finally {
            // Release wake lock. The service now holds its own foreground guarantee.
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release wake lock", e)
            }
        }
    }
}
