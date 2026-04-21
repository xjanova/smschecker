package com.thaiprompt.smschecker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

        // Delay to give the system time to finish booting before we start foreground services.
        // Too short → ForegroundServiceStartNotAllowedException on some devices.
        private const val STARTUP_DELAY_MS = 8_000L
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

        Log.i(TAG, "Received $action — scheduling service restart in ${STARTUP_DELAY_MS}ms")

        // Use goAsync so the receiver stays alive during the delayed start
        val pending = goAsync()
        val appContext = context.applicationContext

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // 1. Foreground sync service (WakeLock + WebSocket + periodic polling)
                try {
                    RealtimeSyncService.start(appContext, fromBoot = true)
                    Log.i(TAG, "RealtimeSyncService started from boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start RealtimeSyncService from boot", e)
                }

                // 2. SMS processing service (monitor flag)
                try {
                    val smsIntent = Intent(appContext, SmsProcessingService::class.java).apply {
                        this.action = SmsProcessingService.ACTION_START_MONITORING
                    }
                    SmsProcessingService.enqueueWork(appContext, smsIntent)
                    Log.i(TAG, "SmsProcessingService started from boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start SmsProcessingService from boot", e)
                }

                // 3. WorkManager: watchdog + periodic sync
                try {
                    ServiceWatchdogWorker.enqueuePeriodic(appContext)
                    OrderSyncWorker.enqueuePeriodicSync(appContext)
                    Log.i(TAG, "Watchdog + periodic sync re-enqueued")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enqueue WorkManager jobs from boot", e)
                }
            } finally {
                pending.finish()
            }
        }, STARTUP_DELAY_MS)
    }
}
