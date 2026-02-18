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

    /**
     * Get servers that successfully synced a specific transaction.
     * Used to determine which servers still need sync (avoid duplicate sends).
     */
    @Query("SELECT DISTINCT serverId FROM sync_logs WHERE transactionId = :transactionId AND status = 'SUCCESS'")
    suspend fun getSuccessfulServerIds(transactionId: Long): List<Long>

    /**
     * Check if a transaction was successfully synced to a specific server.
     */
    @Query("SELECT COUNT(*) > 0 FROM sync_logs WHERE transactionId = :transactionId AND serverId = :serverId AND status = 'SUCCESS'")
    suspend fun isTransactionSyncedToServer(transactionId: Long, serverId: Long): Boolean
}
