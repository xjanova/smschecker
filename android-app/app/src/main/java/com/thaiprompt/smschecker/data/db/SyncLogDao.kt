package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.SyncLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLog): Long

    @Update
    suspend fun update(log: SyncLog)

    @Query("SELECT * FROM sync_logs ORDER BY sentAt DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<SyncLog>>

    @Query("SELECT * FROM sync_logs WHERE transactionId = :transactionId ORDER BY sentAt DESC")
    fun getLogsForTransaction(transactionId: Long): Flow<List<SyncLog>>

    @Query("DELETE FROM sync_logs WHERE sentAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int
}
