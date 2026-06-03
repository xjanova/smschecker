package com.thaiprompt.smschecker.service

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thaiprompt.smschecker.data.license.LicenseManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic watchdog that ensures RealtimeSyncService is alive.
 *
 * WHY:
 *  - Android may kill foreground services under memory pressure or battery optimizer.
 *  - START_STICKY restart is not guaranteed in Doze mode.
 *  - FCM high-priority push is reliable but won't revive a dead WakeLock / polling loop.
 *
 * Runs every ~15 minutes (WorkManager minimum). If the service is missing, start it.
 * Also re-enqueues the OrderSyncWorker periodic job in case the user cleared app data.
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // No point reviving service if license is invalid — user must re-activate first
        if (!LicenseManager.isLicenseValid()) {
            Log.d(TAG, "License not valid, skipping watchdog tick")
            return Result.success()
        }

        val alive = isServiceRunning(applicationContext, RealtimeSyncService::class.java.name)

        // 🔁 (2026-06-03) Re-arm WorkManager safety nets FIRST (idempotent) — เหล่านี้รันได้จริงใน
        //   background แม้บน Android 14. ทำก่อนพยายาม restart FGS เพื่อว่าถ้า startForegroundService()
        //   ถูกปฏิเสธ (ForegroundServiceStartNotAllowedException บน A14 เมื่อ start จาก background worker)
        //   ตัว SmsSweepWorker (กู้ SMS ที่พลาด) ก็ยังถูก schedule อยู่ดี
        //   หมายเหตุสำคัญ: การ "รับ SMS" ไม่ได้ขึ้นกับ RealtimeSyncService — SmsBroadcastReceiver
        //   (manifest receiver) + SmsProcessingService (start ภายใต้ FGS exemption ของ SMS_RECEIVED)
        //   จัดการเอง. RealtimeSyncService มีไว้สำหรับ order sync / WebSocket / orphan reconciliation
        try { OrderSyncWorker.enqueuePeriodicSync(applicationContext) } catch (e: Exception) { Log.w(TAG, "re-enqueue OrderSyncWorker failed", e) }
        try { SmsSweepWorker.enqueuePeriodic(applicationContext) } catch (e: Exception) { Log.w(TAG, "re-enqueue SmsSweepWorker failed", e) }

        if (!alive) {
            Log.w(TAG, "⚠️ RealtimeSyncService NOT running — attempting restart")
            try {
                RealtimeSyncService.start(applicationContext, fromBoot = false)
            } catch (e: Exception) {
                // A14: background FGS start อาจถูกปฏิเสธ — ไม่ fatal: FCM push + WorkManager ยังขับ
                // order sync ต่อได้ และ service จะฟื้นเองตอนผู้ใช้เปิดแอป (foreground). ไม่ retry วนเปล่า.
                Log.w(TAG, "RealtimeSyncService restart from background rejected (relying on FCM/WorkManager): ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ RealtimeSyncService alive")
        }
        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, className: String): Boolean {
        // getRunningServices is deprecated for OTHER apps' services but still returns YOUR OWN.
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getRunningServices(Int.MAX_VALUE).any { it.service.className == className }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query running services", e)
            false
        }
    }

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val WORK_NAME = "service_watchdog_periodic"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                // Watchdog must run even on metered networks / battery saver —
                // it's a tiny task and needed specifically when system is under pressure.
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
