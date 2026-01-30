package com.thaiprompt.smschecker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.thaiprompt.smschecker.service.SmsProcessingService

/**
 * Receives incoming SMS broadcasts and forwards bank transaction SMS
 * to the SmsProcessingService for parsing and syncing.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

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

            SmsProcessingService.enqueueWork(context, serviceIntent)
        }
    }
}
