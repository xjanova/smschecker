package com.thaiprompt.smschecker.data.repository

import com.thaiprompt.smschecker.data.db.MisclassificationReportDao
import com.thaiprompt.smschecker.data.model.MisclassificationIssueType
import com.thaiprompt.smschecker.data.model.MisclassificationReport
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MisclassificationReportRepository @Inject constructor(
    private val reportDao: MisclassificationReportDao
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
}
