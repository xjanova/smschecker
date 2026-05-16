package com.thaiprompt.smschecker.service

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
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.domain.scanner.SmsInboxScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic SMS sweep — กู้ข้อความธนาคารที่ realtime receiver อาจพลาด
 *
 * WHY:
 *  - SmsBroadcastReceiver อาจไม่ trigger ใน Doze mode, app process killed,
 *    หรือ low-memory pressure → SMS ที่ลูกค้าโอนแล้ว backend ไม่เห็น
 *  - บิล UPA หมดอายุใน 30 นาที — ถ้า sweep รอบหน้า (15 นาที) ยัง catch ทัน
 *  - Backend มี nonce dedup + TransactionRepository.insertIfNotDuplicate มี mutex
 *    → ส่งซ้ำได้ปลอดภัย, จะ skip รายการที่ process แล้วเสมอ
 *
 * HOW:
 *  1. Scan SMS inbox ล่าสุด 100 ข้อความ (SmsInboxScanner ใช้ content://sms/inbox)
 *  2. กรองเฉพาะ bank SMS ที่ parse ได้ + timestamp ใน window 2 ชั่วโมง
 *  3. For each → insertIfNotDuplicate (dedup window 2 นาที) → syncTransaction
 *  4. Skip ที่มีอยู่แล้วใน Room (เคย process จาก receiver) → ไม่ส่งซ้ำ
 *
 * Periodic: 15 นาที (Android WorkManager minimum)
 * Backoff: LINEAR 30s ถ้า network fail
 */
@HiltWorker
class SmsSweepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanner: SmsInboxScanner,
    private val transactionRepository: TransactionRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!LicenseManager.isLicenseValid()) {
            Log.d(TAG, "License invalid — skip sweep")
            return Result.success()
        }

        val startTime = System.currentTimeMillis()
        val cutoff = startTime - TimeUnit.HOURS.toMillis(SWEEP_WINDOW_HOURS)

        return try {
            // Scan inbox — limit 100 ล่าสุด พอครอบคลุม 2 ชม. ปกติ
            val scanned = scanner.scanInbox(maxMessages = MAX_SCAN_MESSAGES)
            val candidates = scanned.filter {
                it.parsedTransaction != null && it.timestamp >= cutoff
            }

            if (candidates.isEmpty()) {
                Log.d(TAG, "Sweep: no bank SMS in last ${SWEEP_WINDOW_HOURS}h window")
                return Result.success()
            }

            var inserted = 0
            var skippedDup = 0
            var syncedOk = 0
            var syncedFail = 0

            for (sms in candidates) {
                val tx = sms.parsedTransaction ?: continue
                try {
                    // Atomic check-then-insert (mutex-protected) — กัน race กับ realtime receiver
                    // dedup window 2 นาที (ใหญ่กว่า default 60s) — เผื่อ timestamp drift จาก SMS provider
                    val insertedId = transactionRepository.insertIfNotDuplicate(
                        transaction = tx,
                        dedupWindowMs = DEDUP_WINDOW_MS
                    )

                    if (insertedId == null) {
                        skippedDup++
                        continue
                    }

                    inserted++
                    // Sync ไป active servers (parallel internally)
                    val ok = transactionRepository.syncTransaction(tx.copy(id = insertedId))
                    if (ok) syncedOk++ else syncedFail++
                } catch (e: Exception) {
                    Log.w(TAG, "Sweep: failed processing SMS from ${sms.sender}", e)
                }
            }

            val duration = System.currentTimeMillis() - startTime
            Log.i(
                TAG,
                "Sweep done in ${duration}ms — scanned=${candidates.size} " +
                    "inserted=$inserted skipped_dup=$skippedDup " +
                    "synced_ok=$syncedOk synced_fail=$syncedFail"
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sweep failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SmsSweep"
        private const val WORK_NAME = "sms_sweep_periodic"

        // Window 2 ชั่วโมง — ครอบคลุม UPA expiry (30 นาที) + เผื่อ Doze ขังนาน
        private const val SWEEP_WINDOW_HOURS = 2L

        // Scan สูงสุด 100 ข้อความ — เพียงพอสำหรับ 2 ชม. ปกติ
        // (ปริมาณ SMS ธนาคาร normal = 5-20 ข้อความ/ชม.)
        private const val MAX_SCAN_MESSAGES = 100

        // Dedup window 2 นาที — ใหญ่กว่า realtime path (60s) เพราะ SMS provider timestamp อาจ drift
        private const val DEDUP_WINDOW_MS = 120_000L

        /**
         * Schedule periodic sweep — idempotent, ปลอดภัยที่จะ call ทุกครั้งที่ app start
         */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                // ไม่ require network — scan inbox + insert local DB ทำงานได้
                // sync server step จะ fail gracefully ถ้า offline
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<SmsSweepWorker>(
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
