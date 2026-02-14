package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.MisclassificationReport
import kotlinx.coroutines.flow.Flow

@Dao
interface MisclassificationReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: MisclassificationReport): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<MisclassificationReport>)

    @Update
    suspend fun update(report: MisclassificationReport)

    @Delete
    suspend fun delete(report: MisclassificationReport)

    @Query("SELECT * FROM misclassification_reports ORDER BY reportedAt DESC")
    fun getAllReports(): Flow<List<MisclassificationReport>>

    @Query("SELECT * FROM misclassification_reports WHERE id = :id")
    suspend fun getReportById(id: Long): MisclassificationReport?

    @Query("SELECT * FROM misclassification_reports WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getReportByTransactionId(transactionId: Long): MisclassificationReport?

    @Query("SELECT * FROM misclassification_reports WHERE isAnalyzed = 0 ORDER BY reportedAt DESC")
    fun getUnanalyzedReports(): Flow<List<MisclassificationReport>>

    @Query("SELECT * FROM misclassification_reports WHERE isFixed = 0 ORDER BY reportedAt DESC")
    fun getUnfixedReports(): Flow<List<MisclassificationReport>>

    @Query("SELECT COUNT(*) FROM misclassification_reports")
    suspend fun getReportCount(): Int

    @Query("SELECT COUNT(*) FROM misclassification_reports WHERE isAnalyzed = 0")
    suspend fun getUnanalyzedCount(): Int

    @Query("SELECT * FROM misclassification_reports WHERE issueType = :issueType ORDER BY reportedAt DESC")
    fun getReportsByIssueType(issueType: String): Flow<List<MisclassificationReport>>

    @Query("SELECT * FROM misclassification_reports WHERE bank = :bank ORDER BY reportedAt DESC")
    fun getReportsByBank(bank: String): Flow<List<MisclassificationReport>>

    @Query("DELETE FROM misclassification_reports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM misclassification_reports")
    suspend fun deleteAll()

    @Query("""
        SELECT * FROM misclassification_reports
        WHERE reportedAt >= :startTime AND reportedAt <= :endTime
        ORDER BY reportedAt DESC
    """)
    fun getReportsByDateRange(startTime: Long, endTime: Long): Flow<List<MisclassificationReport>>

    @Query("SELECT * FROM misclassification_reports WHERE isSynced = 0 ORDER BY reportedAt ASC")
    suspend fun getUnsyncedReports(): List<MisclassificationReport>

    @Query("SELECT COUNT(*) FROM misclassification_reports WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int

    @Update
    suspend fun updateAll(reports: List<MisclassificationReport>)
}
