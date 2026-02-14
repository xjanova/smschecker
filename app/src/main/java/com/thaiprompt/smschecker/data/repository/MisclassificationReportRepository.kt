package com.thaiprompt.smschecker.data.repository

import android.os.Build
import android.util.Log
import com.thaiprompt.smschecker.BuildConfig
import com.thaiprompt.smschecker.data.api.BugReportApiService
import com.thaiprompt.smschecker.data.api.BugReportRequest
import com.thaiprompt.smschecker.data.api.BatchBugReportRequest
import com.thaiprompt.smschecker.data.db.MisclassificationReportDao
import com.thaiprompt.smschecker.data.model.MisclassificationIssueType
import com.thaiprompt.smschecker.data.model.MisclassificationReport
import com.thaiprompt.smschecker.security.SecureStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MisclassificationReportRepository @Inject constructor(
    private val reportDao: MisclassificationReportDao,
    private val bugReportApi: BugReportApiService,
    private val secureStorage: SecureStorage
) {

    fun getAllReports(): Flow<List<MisclassificationReport>> = reportDao.getAllReports()

    fun getUnanalyzedReports(): Flow<List<MisclassificationReport>> = reportDao.getUnanalyzedReports()

    fun getUnfixedReports(): Flow<List<MisclassificationReport>> = reportDao.getUnfixedReports()

    suspend fun insertReport(report: MisclassificationReport): Long = reportDao.insert(report)

    suspend fun updateReport(report: MisclassificationReport) = reportDao.update(report)

    suspend fun deleteReport(report: MisclassificationReport) = reportDao.delete(report)

    suspend fun getReportById(id: Long): MisclassificationReport? = reportDao.getReportById(id)

    suspend fun getReportByTransactionId(transactionId: Long): MisclassificationReport? =
        reportDao.getReportByTransactionId(transactionId)

    suspend fun getReportCount(): Int = reportDao.getReportCount()

    suspend fun getUnanalyzedCount(): Int = reportDao.getUnanalyzedCount()

    fun getReportsByIssueType(issueType: MisclassificationIssueType): Flow<List<MisclassificationReport>> =
        reportDao.getReportsByIssueType(issueType.name)

    fun getReportsByBank(bank: String): Flow<List<MisclassificationReport>> =
        reportDao.getReportsByBank(bank)

    fun getReportsByDateRange(startTime: Long, endTime: Long): Flow<List<MisclassificationReport>> =
        reportDao.getReportsByDateRange(startTime, endTime)

    suspend fun deleteAll() = reportDao.deleteAll()

    suspend fun getUnsyncedCount(): Int = reportDao.getUnsyncedCount()

    /**
     * Sync unsynced misclassification reports to backend (xman4289.com)
     *
     * @return Pair<Int, Int> (successCount, failedCount)
     */
    suspend fun syncReportsToBackend(): Pair<Int, Int> {
        val unsyncedReports = reportDao.getUnsyncedReports()

        if (unsyncedReports.isEmpty()) {
            Log.d(TAG, "No unsynced reports to submit")
            return Pair(0, 0)
        }

        Log.d(TAG, "Syncing ${unsyncedReports.size} misclassification reports to backend...")

        val requests = unsyncedReports.map { report ->
            BugReportRequest(
                productName = "smschecker",
                productVersion = BuildConfig.VERSION_NAME,
                reportType = "misclassification",
                title = generateTitle(report),
                description = generateDescription(report),
                metadata = mapOf(
                    "transaction_id" to report.transactionId,
                    "bank" to report.bank,
                    "amount" to report.detectedAmount,
                    "detected_type" to report.detectedType.name,
                    "correct_type" to (report.correctType?.name ?: "NOT_SPECIFIED"),
                    "issue_type" to report.issueType.name,
                    "original_message" to report.rawMessage,
                    "sender_address" to report.senderAddress,
                    "timestamp" to report.timestamp
                ),
                deviceId = secureStorage.getDeviceId(),
                priority = when (report.issueType) {
                    MisclassificationIssueType.WRONG_TRANSACTION_TYPE -> "high"
                    MisclassificationIssueType.WRONG_AMOUNT -> "medium"
                    else -> "low"
                },
                severity = when (report.issueType) {
                    MisclassificationIssueType.WRONG_TRANSACTION_TYPE -> "major"
                    MisclassificationIssueType.WRONG_AMOUNT -> "moderate"
                    else -> "minor"
                },
                osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                appVersion = BuildConfig.VERSION_NAME,
                additionalInfo = mapOf(
                    "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "device_brand" to Build.BRAND,
                    "reported_at" to report.reportedAt
                )
            )
        }

        return try {
            val response = bugReportApi.submitBatchReports(
                BatchBugReportRequest(reports = requests)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                val successCount = data?.createdCount ?: 0
                val failedCount = data?.failedCount ?: 0

                Log.d(TAG, "Backend sync successful: $successCount created, $failedCount failed")

                // Mark successfully synced reports
                if (successCount > 0) {
                    val now = System.currentTimeMillis()
                    val syncedReports = unsyncedReports.take(successCount).map { report ->
                        report.copy(
                            isSynced = true,
                            syncedAt = now
                        )
                    }
                    reportDao.updateAll(syncedReports)
                }

                Pair(successCount, failedCount)
            } else {
                Log.e(TAG, "Backend sync failed: ${response.code()} ${response.message()}")
                Pair(0, unsyncedReports.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during backend sync", e)
            Pair(0, unsyncedReports.size)
        }
    }

    private fun generateTitle(report: MisclassificationReport): String {
        return when (report.issueType) {
            MisclassificationIssueType.WRONG_TRANSACTION_TYPE -> {
                "${report.bank} SMS misclassified as ${report.detectedType.name}"
            }
            MisclassificationIssueType.WRONG_AMOUNT -> {
                "${report.bank} amount parsed incorrectly"
            }
            MisclassificationIssueType.NOT_A_TRANSACTION -> {
                "${report.bank} non-transaction SMS detected as transaction"
            }
            MisclassificationIssueType.CANNOT_PARSE -> {
                "${report.bank} transaction SMS not detected"
            }
            else -> {
                "${report.bank} SMS parsing issue"
            }
        }
    }

    private fun generateDescription(report: MisclassificationReport): String {
        return buildString {
            appendLine("**Issue:** ${report.issueType.name}")
            appendLine()
            appendLine("**Bank:** ${report.bank}")
            appendLine("**Detected Type:** ${report.detectedType.name}")

            if (report.correctType != null) {
                appendLine("**Correct Type:** ${report.correctType.name}")
            }

            appendLine("**Detected Amount:** ${report.detectedAmount}")

            if (report.correctAmount != null) {
                appendLine("**Correct Amount:** ${report.correctAmount}")
            }

            appendLine()
            appendLine("**Original SMS:**")
            appendLine("```")
            appendLine(report.rawMessage)
            appendLine("```")

            appendLine()
            appendLine("**Sender:** ${report.senderAddress}")
        }
    }

    companion object {
        private const val TAG = "MisclassificationRepo"
    }
}
