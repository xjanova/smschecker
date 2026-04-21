package com.thaiprompt.smschecker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.thaiprompt.smschecker.service.OrderSyncWorker
import com.thaiprompt.smschecker.service.RealtimeSyncService
import com.thaiprompt.smschecker.service.ServiceWatchdogWorker
import com.thaiprompt.smschecker.service.SmsProcessingService

/**
 * Restart all background components after:
 *  - BOOT_COMPLETED               (normal boot)
 *  - QUICKBOOT_POWERON            (HTC/Samsung quick boot — some ROMs don't send BOOT_COMPLETED)
 *  - MY_PACKAGE_REPLACED          (after app update — components are killed by package manager)
 *
 * This is CRITICAL: RealtimeSyncService holds the WakeLock + periodic sync loop.
 * Without it restarted on boot, the app "dies" until the user opens it manually.
 *
 * IMPORTANT: We start services IMMEDIATELY in onReceive. We do NOT use goAsync+postDelayed
 * because that would hold the receiver for 8+ seconds, risking the 10-second broadcast
 * timeout on slow devices. Hilt's Application.onCreate runs BEFORE this receiver, so all
 * DI graph is ready. If foreground-service start fails due to transient boot state,
 * ServiceWatchdogWorker will restart it within 15 minutes.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val shouldRestart = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> true
            else -> false
        }
        if (!shouldRestart) return

        Log.i(TAG, "Received $action — starting services immediately")
        val appContext = context.applicationContext

        // 1. Foreground sync service (WakeLock + WebSocket + periodic polling).
        //    START FIRST — this is the most important service for overnight operation.
        try {
            RealtimeSyncService.start(appContext, fromBoot = true)
            Log.i(TAG, "RealtimeSyncService started from boot")
        } catch (e: Exception) {
            // May fail with ForegroundServiceStartNotAllowedException on some devices
            // — ServiceWatchdogWorker will revive within 15 min.
            Log.e(TAG, "Failed to start RealtimeSyncService from boot", e)
        }

        // 2. SMS processing service (monitor flag).
        try {
            val smsIntent = Intent(appContext, SmsProcessingService::class.java).apply {
                this.action = SmsProcessingService.ACTION_START_MONITORING
            }
            SmsProcessingService.enqueueWork(appContext, smsIntent)
            Log.i(TAG, "SmsProcessingService started from boot")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SmsProcessingService from boot", e)
        }

        // 3. WorkManager jobs — idempotent, safe to enqueue on every boot.
        //    These are resilient and run on a separate process-lifecycle.
        try {
            ServiceWatchdogWorker.enqueuePeriodic(appContext)
            OrderSyncWorker.enqueuePeriodicSync(appContext)
            Log.i(TAG, "Watchdog + periodic sync re-enqueued")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue WorkManager jobs from boot", e)
        }
    }
}
