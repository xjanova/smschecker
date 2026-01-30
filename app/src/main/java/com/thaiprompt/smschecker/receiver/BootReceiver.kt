package com.thaiprompt.smschecker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.thaiprompt.smschecker.service.SmsProcessingService

/**
 * Restarts the SMS monitoring service after device boot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, starting SMS monitoring service")

            val serviceIntent = Intent(context, SmsProcessingService::class.java).apply {
                action = SmsProcessingService.ACTION_START_MONITORING
            }
            SmsProcessingService.enqueueWork(context, serviceIntent)
        }
    }
}
