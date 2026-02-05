package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.OrphanStatus
import com.thaiprompt.smschecker.data.model.OrphanTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OrphanTransactionDao {

    // =====================================================================
    // Insert / Update
    // =====================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(orphan: OrphanTransaction): Long

    @Update
    suspend fun update(orphan: OrphanTransaction)

    @Delete
    suspend fun delete(orphan: OrphanTransaction)

    @Query("DELETE FROM orphan_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    // =====================================================================
    // Query - Single
    // =====================================================================

    @Query("SELECT * FROM orphan_transactions WHERE id = :id")
    suspend fun getById(id: Long): OrphanTransaction?

    @Query("SELECT * FROM orphan_transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: Long): OrphanTransaction?

    // =====================================================================
    // Query - Lists
    // =====================================================================

    @Query("SELECT * FROM orphan_transactions ORDER BY createdAt DESC")
    fun getAllOrphans(): Flow<List<OrphanTransaction>>

    @Query("SELECT * FROM orphan_transactions WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: OrphanStatus): Flow<List<OrphanTransaction>>

    @Query("SELECT * FROM orphan_transactions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingOrphans(): Flow<List<OrphanTransaction>>

    @Query("SELECT * FROM orphan_transactions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingOrphansList(): List<OrphanTransaction>

    @Query("SELECT COUNT(*) FROM orphan_transactions WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    // =====================================================================
    // Matching - Find orphans that could match an order amount
    // =====================================================================

    /**
     * Find pending orphans with matching amount.
     * Used when a new order comes in to check if we already have the payment.
     */
    @Query("""
        SELECT * FROM orphan_transactions
        WHERE status = 'PENDING'
        AND amount = :amount
        ORDER BY transactionTimestamp DESC
        LIMIT 10
    """)
    suspend fun findByAmount(amount: Double): List<OrphanTransaction>

    /**
     * Find pending orphans with matching amount and bank.
     * More precise matching when bank is known.
     */
    @Query("""
        SELECT * FROM orphan_transactions
        WHERE status = 'PENDING'
        AND amount = :amount
        AND bank = :bank
        ORDER BY transactionTimestamp DESC
        LIMIT 10
    """)
    suspend fun findByAmountAndBank(amount: Double, bank: String): List<OrphanTransaction>

    /**
     * Find pending orphans within an amount range.
     * Useful for fuzzy matching (e.g., 1234.50 - 1234.59)
     */
    @Query("""
        SELECT * FROM orphan_transactions
        WHERE status = 'PENDING'
        AND amount >= :minAmount
        AND amount <= :maxAmount
        ORDER BY transactionTimestamp DESC
        LIMIT 20
    """)
    suspend fun findByAmountRange(minAmount: Double, maxAmount: Double): List<OrphanTransaction>

    /**
     * Find orphans created within a time window.
     * Useful for matching orders that came slightly before/after.
     */
    @Query("""
        SELECT * FROM orphan_transactions
        WHERE status = 'PENDING'
        AND amount = :amount
        AND transactionTimestamp >= :startTime
        AND transactionTimestamp <= :endTime
        ORDER BY transactionTimestamp DESC
    """)
    suspend fun findByAmountAndTimeWindow(
        amount: Double,
        startTime: Long,
        endTime: Long
    ): List<OrphanTransaction>

    // =====================================================================
    // Status Updates
    // =====================================================================

    @Query("""
        UPDATE orphan_transactions
        SET status = 'MATCHED',
            matchedOrderId = :orderId,
            matchedServerId = :serverId,
            matchedAt = :matchedAt,
            updatedAt = :matchedAt
        WHERE id = :orphanId
    """)
    suspend fun markAsMatched(
        orphanId: Long,
        orderId: Long,
        serverId: Long,
        matchedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE orphan_transactions
        SET status = 'MANUALLY_RESOLVED',
            notes = :notes,
            updatedAt = :timestamp
        WHERE id = :orphanId
    """)
    suspend fun markAsManuallyResolved(
        orphanId: Long,
        notes: String? = null,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE orphan_transactions
        SET status = 'IGNORED',
            notes = :reason,
            updatedAt = :timestamp
        WHERE id = :orphanId
    """)
    suspend fun markAsIgnored(
        orphanId: Long,
        reason: String? = null,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE orphan_transactions
        SET matchAttempts = matchAttempts + 1,
            lastMatchAttemptAt = :timestamp,
            updatedAt = :timestamp
        WHERE id = :orphanId
    """)
    suspend fun incrementMatchAttempt(orphanId: Long, timestamp: Long = System.currentTimeMillis())

    // =====================================================================
    // Cleanup
    // =====================================================================

    /**
     * Mark old pending orphans as expired.
     * Default: 7 days (604800000 ms)
     */
    @Query("""
        UPDATE orphan_transactions
        SET status = 'EXPIRED',
            updatedAt = :now
        WHERE status = 'PENDING'
        AND createdAt < :expiryTime
    """)
    suspend fun expireOldOrphans(expiryTime: Long, now: Long = System.currentTimeMillis()): Int

    /**
     * Delete very old orphans (already expired/matched).
     * Default: 30 days
     */
    @Query("""
        DELETE FROM orphan_transactions
        WHERE (status = 'EXPIRED' OR status = 'MATCHED' OR status = 'IGNORED')
        AND createdAt < :deleteBeforeTime
    """)
    suspend fun deleteOldOrphans(deleteBeforeTime: Long): Int

    /**
     * Get statistics for dashboard.
     */
    @Query("""
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN status = 'MATCHED' THEN 1 ELSE 0 END) as matched,
            SUM(CASE WHEN status = 'EXPIRED' THEN 1 ELSE 0 END) as expired,
            SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END) as pendingAmount
        FROM orphan_transactions
    """)
    suspend fun getStatistics(): OrphanStatistics

    // =====================================================================
    // Duplicate check
    // =====================================================================

    /**
     * Check if a transaction is already tracked as orphan.
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM orphan_transactions
        WHERE transactionId = :transactionId
    """)
    suspend fun existsByTransactionId(transactionId: Long): Boolean
}

data class OrphanStatistics(
    val total: Int,
    val pending: Int,
    val matched: Int,
    val expired: Int,
    val pendingAmount: Double
)
