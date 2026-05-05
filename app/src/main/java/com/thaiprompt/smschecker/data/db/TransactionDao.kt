package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * รวมยอดรายวันแยกตาม CREDIT (เงินเข้า) / DEBIT (เงินออก) — ใช้สำหรับกราฟรายได้
 */
data class DailyIncomeExpense(
    val date: String,   // YYYY-MM-DD (local timezone)
    val credit: Double,
    val debit: Double
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: BankTransaction): Long

    @Update
    suspend fun update(transaction: BankTransaction)

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC LIMIT 500")
    fun getAllTransactions(): Flow<List<BankTransaction>>

    @Query("SELECT * FROM bank_transactions WHERE type = :type ORDER BY timestamp DESC LIMIT 500")
    fun getTransactionsByType(type: TransactionType): Flow<List<BankTransaction>>

    @Query("SELECT * FROM bank_transactions WHERE bank = :bank ORDER BY timestamp DESC LIMIT 500")
    fun getTransactionsByBank(bank: String): Flow<List<BankTransaction>>

    /**
     * คืนรายการที่ยังไม่ได้ถูก sync ไป "ทุก" active server (ใช้ sync_logs เป็นฐาน)
     * รองรับ multi-server retry: ถ้ามี server ใดยังไม่ได้รับ transaction นี้ → คืนกลับมาให้ retry
     * รวมถึงรายการที่ไม่เคยถูกส่งไปไหนเลย (ไม่มี sync_log)
     */
    @Query("""
        SELECT bt.* FROM bank_transactions bt
        WHERE EXISTS (
            SELECT 1 FROM server_configs sc
            WHERE sc.isActive = 1
            AND NOT EXISTS (
                SELECT 1 FROM sync_logs sl
                WHERE sl.transactionId = bt.id
                AND sl.serverId = sc.id
                AND sl.status = 'SUCCESS'
            )
        )
        ORDER BY bt.timestamp ASC
        LIMIT 200
    """)
    suspend fun getUnsyncedTransactions(): List<BankTransaction>

    @Query("SELECT * FROM bank_transactions WHERE id = :id")
    suspend fun getById(id: Long): BankTransaction?

    @Query("""
        SELECT * FROM bank_transactions
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
        LIMIT 500
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

    @Query("""
        SELECT COUNT(*) FROM bank_transactions bt
        WHERE EXISTS (
            SELECT 1 FROM server_configs sc
            WHERE sc.isActive = 1
            AND NOT EXISTS (
                SELECT 1 FROM sync_logs sl
                WHERE sl.transactionId = bt.id
                AND sl.serverId = sc.id
                AND sl.status = 'SUCCESS'
            )
        )
    """)
    fun getUnsyncedCount(): Flow<Int>

    /**
     * รวมยอดรายวันแยกตามประเภท (CREDIT/DEBIT) — ใช้สำหรับกราฟรายได้
     * คืนค่าเป็น YYYY-MM-DD (local time) → credit, debit
     */
    @Query("""
        SELECT
            strftime('%Y-%m-%d', timestamp/1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as credit,
            COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as debit
        FROM bank_transactions
        WHERE timestamp >= :since
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyIncomeExpense(since: Long): List<DailyIncomeExpense>

    /**
     * รวมยอดรายเดือน — สำหรับกราฟรายได้รายปี
     * คืนค่าเป็น YYYY-MM (local time) → credit, debit
     */
    @Query("""
        SELECT
            strftime('%Y-%m', timestamp/1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as credit,
            COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as debit
        FROM bank_transactions
        WHERE timestamp >= :since
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getMonthlyIncomeExpense(since: Long): List<DailyIncomeExpense>

    /**
     * รายวันแบบช่วง [start, end) — รองรับ custom range
     */
    @Query("""
        SELECT
            strftime('%Y-%m-%d', timestamp/1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as credit,
            COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as debit
        FROM bank_transactions
        WHERE timestamp >= :startTime AND timestamp < :endTime
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyIncomeExpenseRange(startTime: Long, endTime: Long): List<DailyIncomeExpense>

    /**
     * รายเดือนแบบช่วง [start, end) — สำหรับ range ยาว
     */
    @Query("""
        SELECT
            strftime('%Y-%m', timestamp/1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as credit,
            COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN CAST(amount AS REAL) ELSE 0 END), 0) as debit
        FROM bank_transactions
        WHERE timestamp >= :startTime AND timestamp < :endTime
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getMonthlyIncomeExpenseRange(startTime: Long, endTime: Long): List<DailyIncomeExpense>

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

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPaged(limit: Int, offset: Int): List<BankTransaction>

    @Query("SELECT COUNT(*) FROM bank_transactions")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM bank_transactions")
    suspend fun getTotalCountOnce(): Int

    @Query("""
        SELECT COUNT(*) FROM bank_transactions bt
        WHERE NOT EXISTS (
            SELECT 1 FROM server_configs sc
            WHERE sc.isActive = 1
            AND NOT EXISTS (
                SELECT 1 FROM sync_logs sl
                WHERE sl.transactionId = bt.id
                AND sl.serverId = sc.id
                AND sl.status = 'SUCCESS'
            )
        )
        AND EXISTS (
            SELECT 1 FROM sync_logs sl2
            WHERE sl2.transactionId = bt.id
            AND sl2.status = 'SUCCESS'
        )
    """)
    fun getSyncedCount(): Flow<Int>

    @Query("UPDATE bank_transactions SET bank = :bank, type = :type, amount = :amount WHERE id = :id")
    suspend fun updateTransaction(id: Long, bank: String, type: TransactionType, amount: String)
}
