package com.thaiprompt.smschecker.data.repository

import android.util.Log
import com.thaiprompt.smschecker.data.db.OrphanTransactionDao
import com.thaiprompt.smschecker.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing orphan transactions.
 *
 * Orphan Transaction Flow:
 * 1. SMS/Notification comes in → Parse transaction
 * 2. Try to match with pending orders
 * 3. If no match → Save to orphan_transactions
 * 4. When new order arrives → Check orphans first
 * 5. If orphan matches → Auto-approve + mark orphan as MATCHED
 *
 * Cleanup:
 * - Expire pending orphans after 7 days
 * - Delete expired/matched orphans after 30 days
 */
@Singleton
class OrphanTransactionRepository @Inject constructor(
    private val orphanDao: OrphanTransactionDao
) {

    companion object {
        private const val TAG = "OrphanTransactionRepo"

        // Expiry settings
        const val ORPHAN_EXPIRY_DAYS = 7
        const val ORPHAN_DELETE_DAYS = 30

        // Time window for matching (±30 minutes)
        const val MATCH_TIME_WINDOW_MS = 30 * 60 * 1000L

        // กรอบเวลาสำหรับ auto-match แบบ batch reconciliation (±24 ชม.) — กว้างกว่า realtime
        // เพราะ orphan อาจรอบิล sync นานข้ามชั่วโมง แต่ยังกันเคส "ยอดบังเอิญตรงคนละวัน"
        const val AUTO_MATCH_TIME_WINDOW_MS = 24 * 60 * 60 * 1000L

        // Amount tolerance for fuzzy matching (0.01 = 1 satang)
        const val AMOUNT_TOLERANCE = 0.01
    }

    // =====================================================================
    // Observe
    // =====================================================================

    fun getAllOrphans(): Flow<List<OrphanTransaction>> = orphanDao.getAllOrphans()

    fun getPendingOrphans(): Flow<List<OrphanTransaction>> = orphanDao.getPendingOrphans()

    fun getPendingCount(): Flow<Int> = orphanDao.getPendingCount()

    // =====================================================================
    // Create Orphan
    // =====================================================================

    /**
     * Save a transaction as orphan when no matching order found.
     */
    suspend fun saveAsOrphan(
        transaction: BankTransaction,
        source: TransactionSource = TransactionSource.SMS
    ): Long {
        // Check if already exists
        if (orphanDao.existsByTransactionId(transaction.id)) {
            Log.d(TAG, "Transaction ${transaction.id} already tracked as orphan")
            return -1
        }

        val orphan = OrphanTransaction(
            transactionId = transaction.id,
            amount = transaction.amount.toDoubleOrNull() ?: 0.0,
            amountString = transaction.amount,
            bank = transaction.bank,
            accountNumber = transaction.accountNumber,
            senderOrReceiver = transaction.senderOrReceiver,
            referenceNumber = transaction.referenceNumber,
            rawMessage = transaction.rawMessage,
            transactionTimestamp = transaction.timestamp,
            source = source
        )

        val id = orphanDao.insert(orphan)
        Log.i(TAG, "Saved orphan transaction: amount=${orphan.amount}, bank=${orphan.bank}, id=$id")
        return id
    }

    // =====================================================================
    // Find Match
    // =====================================================================

    /**
     * Find orphan transactions that match an order's amount.
     * Called when a new order arrives to check if payment already exists.
     *
     * @param orderAmount The order amount to match
     * @param bank Optional bank filter
     * @param orderCreatedAt Optional timestamp for time-window matching
     * @return Best matching orphan, or null if no match
     */
    suspend fun findMatchingOrphan(
        orderAmount: Double,
        bank: String? = null,
        orderCreatedAt: Long? = null
    ): OrphanTransaction? {
        Log.d(TAG, "Looking for orphan matching amount=$orderAmount, bank=$bank")

        // Strategy 1: Exact amount + bank match
        if (bank != null) {
            val bankMatches = orphanDao.findByAmountAndBank(orderAmount, bank)
            if (bankMatches.isNotEmpty()) {
                Log.d(TAG, "Found ${bankMatches.size} orphans matching amount and bank")
                // Prefer the most recent one
                return bankMatches.maxByOrNull { it.transactionTimestamp }
            }
        }

        // Strategy 2: Exact amount match (any bank)
        val amountMatches = orphanDao.findByAmount(orderAmount)
        if (amountMatches.isNotEmpty()) {
            Log.d(TAG, "Found ${amountMatches.size} orphans matching amount")

            // If we have order timestamp, prefer orphans close to that time
            if (orderCreatedAt != null) {
                val timeWindowStart = orderCreatedAt - MATCH_TIME_WINDOW_MS
                val timeWindowEnd = orderCreatedAt + MATCH_TIME_WINDOW_MS

                val timeMatches = amountMatches.filter {
                    it.transactionTimestamp in timeWindowStart..timeWindowEnd
                }

                if (timeMatches.isNotEmpty()) {
                    return timeMatches.minByOrNull {
                        kotlin.math.abs(it.transactionTimestamp - orderCreatedAt)
                    }
                }
            }

            // Return most recent match
            return amountMatches.maxByOrNull { it.transactionTimestamp }
        }

        // Strategy 3: Fuzzy amount match (for rounding issues)
        val minAmount = orderAmount - AMOUNT_TOLERANCE
        val maxAmount = orderAmount + AMOUNT_TOLERANCE
        val fuzzyMatches = orphanDao.findByAmountRange(minAmount, maxAmount)

        if (fuzzyMatches.isNotEmpty()) {
            Log.d(TAG, "Found ${fuzzyMatches.size} orphans in fuzzy range")
            // Prefer exact match, then closest amount
            return fuzzyMatches.minByOrNull {
                kotlin.math.abs(it.amount - orderAmount)
            }
        }

        Log.d(TAG, "No matching orphan found for amount=$orderAmount")
        return null
    }

    /**
     * Find all orphans that could match a list of pending orders.
     * Useful for batch reconciliation.
     */
    suspend fun findMatchesForOrders(
        orders: List<OrderApproval>
    ): Map<Long, OrphanTransaction> {
        val result = mutableMapOf<Long, OrphanTransaction>()
        val pendingOrphans = orphanDao.getPendingOrphansList()

        if (pendingOrphans.isEmpty()) {
            return result
        }

        // 🛡️ (2026-06-03) กันอนุมัติ "ผิดบิล": auto-match เฉพาะเมื่อยอดนั้น unique ทั้งสองฝั่ง
        //   บั๊กเดิม: จับคู่ด้วย amount อย่างเดียว แล้วเอา orphan ใบแรก (comment เดิมยอมรับเอง
        //   ว่า "could be refined with bank/time matching") → ถ้ามี 2 บิล/2 ลูกค้ายอดเท่ากัน
        //   (เช่น ฿500.00) จ่ายมาแค่คนเดียว ระบบอาจ auto-approve บิลของอีกคนที่ยังไม่จ่าย
        //   เกณฑ์ปลอดภัยใหม่: ยอดนั้นต้องมี pending order "ใบเดียว" AND orphan "ใบเดียว"
        //   + orphan SMS ต้องอยู่ในกรอบเวลาใกล้บิล ถ้ากำกวมเมื่อไร → ไม่ auto, ให้ admin จับคู่เองที่ Orphans tab
        // 🐞 (2026-06-04) key ด้วย "สตางค์ (Long)" แทน Double ดิบ — กัน float ULP mismatch ระหว่าง
        //   amount ที่ parse คนละแหล่ง (orphan = จาก string SMS, order = จาก JSON double ของ server)
        //   ถ้า bit pattern ต่างกันแม้ ULP เดียว groupBy/lookup จะไม่ตรงกันแล้วบิลไม่ถูกจับคู่เงียบๆ
        fun satang(a: Double): Long = Math.round(a * 100.0)
        val pendingOrders = orders.filter { it.approvalStatus == ApprovalStatus.PENDING_REVIEW }
        val ordersByAmount = pendingOrders.groupBy { satang(it.amount) }
        val orphansByAmount = pendingOrphans.groupBy { satang(it.amount) }

        for ((amountKey, ordersForAmount) in ordersByAmount) {
            val amount = ordersForAmount.first().amount  // for logging (baht)
            // ฝั่ง order: ยอดนี้ต้องมีบิลเดียว — ถ้าหลายบิลยอดเท่ากัน บอกไม่ได้ว่าจ่ายให้ใบไหน
            if (ordersForAmount.size != 1) {
                Log.w(TAG, "findMatchesForOrders: amount=$amount มี ${ordersForAmount.size} pending orders — ambiguous, skip auto")
                continue
            }
            val orphansForAmount = orphansByAmount[amountKey] ?: continue
            // ฝั่ง orphan: ยอดนี้ต้องมียอดโอนเดียว — ถ้าหลายยอดเท่ากัน บอกไม่ได้ว่าใบไหนคือของบิลนี้
            if (orphansForAmount.size != 1) {
                Log.w(TAG, "findMatchesForOrders: amount=$amount มี ${orphansForAmount.size} orphans — ambiguous, skip auto")
                continue
            }

            val order = ordersForAmount.first()
            val orphan = orphansForAmount.first()

            // 🕐 Time sanity check: orphan SMS ต้องอยู่ใกล้ช่วงเวลาบิล (ใช้ paymentTimestamp = server created_at
            //   ถ้ามี ไม่งั้น createdAt local) — กันเคสยอดบังเอิญตรงแต่คนละวัน/คนละรายการ
            val orderTime = order.paymentTimestamp ?: order.createdAt
            val delta = kotlin.math.abs(orphan.transactionTimestamp - orderTime)
            if (delta > AUTO_MATCH_TIME_WINDOW_MS) {
                Log.w(TAG, "findMatchesForOrders: amount=$amount ห่างกัน ${delta / 60000}min เกิน window — skip auto (manual review)")
                continue
            }

            result[order.id] = orphan
        }

        Log.d(TAG, "Found ${result.size} unambiguous orphan matches for ${pendingOrders.size} pending orders")
        return result
    }

    // =====================================================================
    // Mark as Matched
    // =====================================================================

    /**
     * Mark an orphan as matched to an order.
     */
    suspend fun markAsMatched(
        orphanId: Long,
        orderId: Long,
        serverId: Long
    ) {
        orphanDao.markAsMatched(orphanId, orderId, serverId)
        Log.i(TAG, "Marked orphan $orphanId as matched to order $orderId")
    }

    /**
     * Mark as manually resolved by user.
     */
    suspend fun markAsManuallyResolved(orphanId: Long, notes: String? = null) {
        orphanDao.markAsManuallyResolved(orphanId, notes)
    }

    /**
     * Mark as ignored (user doesn't want to track).
     */
    suspend fun markAsIgnored(orphanId: Long, reason: String? = null) {
        orphanDao.markAsIgnored(orphanId, reason)
    }

    // =====================================================================
    // Cleanup
    // =====================================================================

    /**
     * Run cleanup: expire old pending orphans, delete very old ones.
     * Should be called periodically (e.g., in OrderSyncWorker).
     */
    suspend fun runCleanup(): CleanupResult {
        val now = System.currentTimeMillis()
        val expiryTime = now - (ORPHAN_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)
        val deleteTime = now - (ORPHAN_DELETE_DAYS * 24 * 60 * 60 * 1000L)

        val expired = orphanDao.expireOldOrphans(expiryTime)
        val deleted = orphanDao.deleteOldOrphans(deleteTime)

        if (expired > 0 || deleted > 0) {
            Log.i(TAG, "Cleanup: expired=$expired, deleted=$deleted orphans")
        }

        return CleanupResult(expired, deleted)
    }

    data class CleanupResult(
        val expiredCount: Int,
        val deletedCount: Int
    )

    // =====================================================================
    // Statistics
    // =====================================================================

    suspend fun getStatistics() = orphanDao.getStatistics()

    // =====================================================================
    // Utils
    // =====================================================================

    suspend fun incrementMatchAttempt(orphanId: Long) {
        orphanDao.incrementMatchAttempt(orphanId)
    }

    suspend fun getById(id: Long) = orphanDao.getById(id)

    suspend fun delete(orphan: OrphanTransaction) = orphanDao.delete(orphan)
}
