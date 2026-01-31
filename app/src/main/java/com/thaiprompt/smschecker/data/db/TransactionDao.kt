package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: BankTransaction): Long

    @Update
    suspend fun update(transaction: BankTransaction)

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<BankTransaction>>

    @Query("SELECT * FROM bank_transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<BankTransaction>>

    @Query("SELECT * FROM bank_transactions WHERE bank = :bank ORDER BY timestamp DESC")
    fun getTransactionsByBank(bank: String): Flow<List<BankTransaction>>

    @Query("SELECT * FROM bank_transactions WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedTransactions(): List<BankTransaction>

    @Query("SELECT * FROM bank_transactions WHERE id = :id")
    suspend fun getById(id: Long): BankTransaction?

    @Query("""
        SELECT * FROM bank_transactions
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<BankTransaction>>

    @Query("SELECT COUNT(*) FROM bank_transactions WHERE type = :type")
    fun getCountByType(type: TransactionType): Flow<Int>

    @Query("""
        SELECT COALESCE(SUM(CAST(amount AS REAL)), 0)
        FROM bank_transactions
        WHERE type = :type AND timestamp >= :since
    """)
    fun getTotalAmountByType(type: TransactionType, since: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM bank_transactions WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("UPDATE bank_transactions SET isSynced = 1, syncedServerId = :serverId, syncResponse = :response WHERE id = :transactionId")
    suspend fun markAsSynced(transactionId: Long, serverId: Long, response: String?)

    @Query("DELETE FROM bank_transactions WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int

    @Query("""
        SELECT * FROM bank_transactions
        WHERE bank = :bank AND amount = :amount AND type = :type
        AND ABS(timestamp - :timestamp) < :windowMs
        LIMIT 1
    """)
    suspend fun findDuplicate(bank: String, amount: String, type: TransactionType, timestamp: Long, windowMs: Long): BankTransaction?

    @Query("SELECT * FROM bank_transactions WHERE sourceType = 'NOTIFICATION' ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentNotificationTransactions(): List<BankTransaction>

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC LIMIT 200")
    fun getRecentTransactions(): Flow<List<BankTransaction>>

    @Query("SELECT COUNT(*) FROM bank_transactions")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM bank_transactions WHERE isSynced = 1")
    fun getSyncedCount(): Flow<Int>
}
