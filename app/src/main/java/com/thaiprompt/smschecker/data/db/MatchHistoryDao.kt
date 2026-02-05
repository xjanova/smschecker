package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.MatchHistory
import com.thaiprompt.smschecker.data.model.MatchResult
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: MatchHistory): Long

    @Query("SELECT * FROM match_history ORDER BY matchedAt DESC")
    fun getAllHistory(): Flow<List<MatchHistory>>

    @Query("SELECT * FROM match_history ORDER BY matchedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<MatchHistory>>

    @Query("SELECT * FROM match_history WHERE matchResult = :result ORDER BY matchedAt DESC")
    fun getHistoryByResult(result: MatchResult): Flow<List<MatchHistory>>

    @Query("SELECT * FROM match_history WHERE serverId = :serverId ORDER BY matchedAt DESC")
    fun getHistoryByServer(serverId: Long): Flow<List<MatchHistory>>

    @Query("SELECT * FROM match_history WHERE matchedAt BETWEEN :startTime AND :endTime ORDER BY matchedAt DESC")
    fun getHistoryInRange(startTime: Long, endTime: Long): Flow<List<MatchHistory>>

    // Statistics
    @Query("SELECT COUNT(*) FROM match_history WHERE matchResult = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT AVG(serverQueriesCount) FROM match_history WHERE matchResult = 'SUCCESS'")
    suspend fun getAverageQueriesCount(): Double?

    @Query("SELECT AVG(matchDurationMs) FROM match_history WHERE matchResult = 'SUCCESS'")
    suspend fun getAverageMatchDuration(): Double?

    @Query("SELECT COUNT(*) FROM match_history WHERE matchedAt >= :since")
    suspend fun getCountSince(since: Long): Int

    // Summary for dashboard
    @Query("""
        SELECT
            COUNT(*) as totalMatches,
            SUM(CASE WHEN matchResult = 'SUCCESS' THEN 1 ELSE 0 END) as successCount,
            AVG(serverQueriesCount) as avgQueries,
            AVG(matchDurationMs) as avgDuration,
            SUM(amount) as totalAmount
        FROM match_history
        WHERE matchedAt >= :since
    """)
    suspend fun getSummary(since: Long): MatchSummary?

    // Cleanup old records
    @Query("DELETE FROM match_history WHERE matchedAt < :before")
    suspend fun deleteOldRecords(before: Long): Int

    @Delete
    suspend fun delete(history: MatchHistory)
}

data class MatchSummary(
    val totalMatches: Int,
    val successCount: Int,
    val avgQueries: Double?,
    val avgDuration: Double?,
    val totalAmount: Double?
)
