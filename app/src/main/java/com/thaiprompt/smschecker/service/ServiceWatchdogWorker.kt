package com.thaiprompt.smschecker.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.SmsCheckerApp
import com.thaiprompt.smschecker.ui.MainActivity
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

        // 🔁 (2026-06-03) Re-arm WorkManager safety nets FIRST (idempotent) — เหล่านี้รันได้จริงใน
        //   background แม้บน Android 14. ทำก่อนพยายาม restart FGS เพื่อว่าถ้า startForegroundService()
        //   ถูกปฏิเสธ (ForegroundServiceStartNotAllowedException บน A14 เมื่อ start จาก background worker)
        //   ตัว SmsSweepWorker (กู้ SMS ที่พลาด) ก็ยังถูก schedule อยู่ดี
        //   หมายเหตุสำคัญ: การ "รับ SMS" ไม่ได้ขึ้นกับ RealtimeSyncService — SmsBroadcastReceiver
        //   (manifest receiver) + SmsProcessingService (start ภายใต้ FGS exemption ของ SMS_RECEIVED)
        //   จัดการเอง. RealtimeSyncService มีไว้สำหรับ order sync / WebSocket / orphan reconciliation
        try { OrderSyncWorker.enqueuePeriodicSync(applicationContext) } catch (e: Exception) { Log.w(TAG, "re-enqueue OrderSyncWorker failed", e) }
        try { SmsSweepWorker.enqueuePeriodic(applicationContext) } catch (e: Exception) { Log.w(TAG, "re-enqueue SmsSweepWorker failed", e) }

        // 🛡️ (2026-06-04) liveness จาก heartbeat (RealtimeSyncService เขียน last_alive_at ทุก 5 นาที)
        //   เชื่อถือได้กว่า getRunningServices (deprecated + ไม่แม่นบน OEM ใหม่ๆ → อาจรายงาน alive ทั้งที่ตาย)
        val hb = applicationContext.getSharedPreferences("smschecker_heartbeat", Context.MODE_PRIVATE)
        val lastAlive = hb.getLong("last_alive_at", 0L)
        val ageMs = System.currentTimeMillis() - lastAlive
        val heartbeatStale = lastAlive <= 0L || ageMs > HEARTBEAT_STALE_MS
        val runningByQuery = isServiceRunning(applicationContext, RealtimeSyncService::class.java.name)

        if (heartbeatStale || !runningByQuery) {
            Log.w(TAG, "⚠️ RealtimeSyncService looks dead (hbStale=$heartbeatStale age=${ageMs / 60000}min running=$runningByQuery) — attempting restart")
            try {
                RealtimeSyncService.start(applicationContext, fromBoot = false)
            } catch (e: Exception) {
                // A14: background FGS start อาจถูกปฏิเสธ — ไม่ fatal. FCM push + WorkManager ยังขับ
                // order sync ต่อได้; service ฟื้นเองตอนผู้ใช้เปิดแอป. ไม่ retry วนเปล่า.
                Log.w(TAG, "RealtimeSyncService restart from background rejected (A14): ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ RealtimeSyncService alive (heartbeat ${ageMs / 60000}min ago)")
        }

        // 🛡️ (2026-06-04) Tripwire: ถ้าเคยมีชีวิต (lastAlive>0) แต่เงียบนานเกิน ALERT_STALE_MS
        //   = ตายและ revive จาก background ไม่ได้ (A14) → เด้ง noti ดังให้ผู้ใช้รู้ ("ตายแล้วไม่มีใครรู้")
        //   แตะ noti = เปิด MainActivity = activity start ซึ่ง A14 อนุญาตให้ start FGS ได้ → กู้ระบบ
        //   พอ heartbeat กลับมาสด ก็ cancel noti อัตโนมัติ
        if (lastAlive > 0L && ageMs > ALERT_STALE_MS) {
            postServiceDownAlert(applicationContext, ageMs / 60000)
        } else {
            cancelServiceDownAlert(applicationContext)
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

    private fun postServiceDownAlert(context: Context, staleMinutes: Long) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val noti = NotificationCompat.Builder(context, SmsCheckerApp.NOTIFICATION_CHANNEL_TRANSACTION)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚠️ ระบบตรวจสอบ SMS อาจหยุดทำงาน")
                .setContentText("ไม่พบสัญญาณ ~${staleMinutes} นาที — แตะเพื่อเปิดแอปและกู้ระบบ")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ALERT_NOTIF_ID, noti)
        } catch (e: Exception) {
            Log.w(TAG, "postServiceDownAlert failed", e)
        }
    }

    private fun cancelServiceDownAlert(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ALERT_NOTIF_ID)
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val WORK_NAME = "service_watchdog_periodic"
        private const val HEARTBEAT_STALE_MS = 30 * 60 * 1000L   // heartbeat ทุก 5 นาที → >30 นาที = ตาย
        private const val ALERT_STALE_MS = 60 * 60 * 1000L       // เงียบ >1 ชม → เตือนผู้ใช้ (tripwire)
        private const val ALERT_NOTIF_ID = 9911

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
