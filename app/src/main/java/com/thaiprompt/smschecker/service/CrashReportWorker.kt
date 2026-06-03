package com.thaiprompt.smschecker.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thaiprompt.smschecker.BuildConfig
import com.thaiprompt.smschecker.data.api.BugReportApiService
import com.thaiprompt.smschecker.data.api.BugReportRequest
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 🛡️ (2026-06-04) Crash reporter
 *
 * บนเครื่อง dedicated ไม่มีใครอ่าน logcat → crash ที่ฆ่า process ตอนกลางคืน "หายไปเงียบๆ"
 * Flow:
 *   1. SmsCheckerApp uncaught handler persist stacktrace ลง prefs ด้วย commit() (ก่อน process ตาย)
 *   2. รอบเปิดแอป/process ถัดไป → enqueueIfPending() → worker นี้อ่าน prefs แล้ว POST ไป xman4289.com
 *      ผ่าน BugReportApiService (reportType="crash") แล้วเคลียร์ flag
 *
 * ส่งจาก process ใหม่ (ไม่ใช่ process ที่กำลังตาย) — network เชื่อถือได้
 */
@HiltWorker
class CrashReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bugReportApi: BugReportApiService,
    private val secureStorage: SecureStorage,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trace = prefs.getString(KEY_TRACE, null) ?: return Result.success() // ไม่มี crash ค้าง
        val message = prefs.getString(KEY_MESSAGE, null) ?: "crash"
        val threadName = prefs.getString(KEY_THREAD, "?") ?: "?"
        val crashedAt = prefs.getLong(KEY_AT, 0L)

        return try {
            val resp = bugReportApi.submitReport(
                BugReportRequest(
                    productVersion = BuildConfig.VERSION_NAME,
                    reportType = "crash",
                    title = "Crash: ${message.take(120)}",
                    description = "Uncaught exception on thread '$threadName' at ${java.util.Date(crashedAt)}",
                    metadata = mapOf(
                        "thread" to threadName,
                        "crashed_at" to crashedAt,
                        "device_model" to android.os.Build.MODEL,
                        "sdk_int" to android.os.Build.VERSION.SDK_INT
                    ),
                    deviceId = secureStorage.getDeviceId(),
                    priority = "high",
                    severity = "critical",
                    osVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                    appVersion = BuildConfig.VERSION_NAME,
                    stackTrace = trace
                )
            )
            if (resp.isSuccessful) {
                prefs.edit().clear().apply()
                Log.i(TAG, "Crash report sent + cleared")
                Result.success()
            } else {
                Log.w(TAG, "Crash report HTTP ${resp.code()} — will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Crash report failed — will retry: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CrashReportWorker"
        private const val WORK_NAME = "crash_report_oneshot"

        // shared with SmsCheckerApp.persistCrash()
        const val PREFS = "smschecker_crash"
        const val KEY_TRACE = "pending_stacktrace"
        const val KEY_MESSAGE = "pending_message"
        const val KEY_THREAD = "pending_thread"
        const val KEY_AT = "pending_at"

        /** เรียกตอน app start — ถ้ามี crash ค้างใน prefs ค่อย enqueue (ไม่งั้นข้าม) */
        fun enqueueIfPending(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_TRACE, null) == null) return

            val request = OneTimeWorkRequestBuilder<CrashReportWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
