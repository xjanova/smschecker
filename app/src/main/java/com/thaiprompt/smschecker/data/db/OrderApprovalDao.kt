package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.PendingAction
import kotlinx.coroutines.flow.Flow

/**
 * รวมยอดบิลที่อนุมัติแล้วต่อวัน/เดือน — ใช้สำหรับกราฟรายรับ (เส้นเขียว)
 */
data class DailyApprovedAmount(
    val date: String,   // YYYY-MM-DD หรือ YYYY-MM (local timezone)
    val amount: Double
)

@Dao
interface OrderApprovalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderApproval): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<OrderApproval>)

    @Update
    suspend fun update(order: OrderApproval)

    @Query("SELECT * FROM order_approvals WHERE approvalStatus != 'DELETED' ORDER BY COALESCE(paymentTimestamp, createdAt) DESC LIMIT 500")
    fun getAllOrders(): Flow<List<OrderApproval>>

    @Query("SELECT * FROM order_approvals WHERE approvalStatus = :status ORDER BY COALESCE(paymentTimestamp, createdAt) DESC LIMIT 500")
    fun getOrdersByStatus(status: ApprovalStatus): Flow<List<OrderApproval>>

    @Query("SELECT * FROM order_approvals WHERE serverId = :serverId ORDER BY COALESCE(paymentTimestamp, createdAt) DESC LIMIT 500")
    fun getOrdersByServer(serverId: Long): Flow<List<OrderApproval>>

    @Query("SELECT * FROM order_approvals WHERE COALESCE(paymentTimestamp, createdAt) BETWEEN :startTime AND :endTime ORDER BY COALESCE(paymentTimestamp, createdAt) DESC LIMIT 500")
    fun getOrdersByDateRange(startTime: Long, endTime: Long): Flow<List<OrderApproval>>

    @Query("""
        SELECT * FROM order_approvals
        WHERE approvalStatus != 'DELETED'
        AND (:status IS NULL OR approvalStatus = :status)
        AND (:serverId IS NULL OR serverId = :serverId)
        AND (:startTime IS NULL OR COALESCE(paymentTimestamp, createdAt) >= :startTime)
        AND (:endTime IS NULL OR COALESCE(paymentTimestamp, createdAt) <= :endTime)
        ORDER BY COALESCE(paymentTimestamp, createdAt) DESC
        LIMIT 500
    """)
    fun getFilteredOrders(
        status: ApprovalStatus?,
        serverId: Long?,
        startTime: Long?,
        endTime: Long?
    ): Flow<List<OrderApproval>>

    @Query("""
        SELECT * FROM order_approvals
        WHERE approvalStatus != 'DELETED'
        AND (:status IS NULL OR approvalStatus = :status)
        AND (:serverId IS NULL OR serverId = :serverId)
        AND (:startTime IS NULL OR COALESCE(paymentTimestamp, createdAt) >= :startTime)
        AND (:endTime IS NULL OR COALESCE(paymentTimestamp, createdAt) <= :endTime)
        AND (:search IS NULL OR :search = ''
            OR orderNumber LIKE '%' || :search || '%'
            OR productName LIKE '%' || :search || '%'
            OR customerName LIKE '%' || :search || '%'
            OR websiteName LIKE '%' || :search || '%'
            OR serverName LIKE '%' || :search || '%'
            OR bank LIKE '%' || :search || '%'
        )
        ORDER BY COALESCE(paymentTimestamp, createdAt) DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilteredOrdersPaged(
        status: String?,
        serverId: Long?,
        startTime: Long?,
        endTime: Long?,
        search: String?,
        limit: Int,
        offset: Int
    ): List<OrderApproval>

    @Query("""
        SELECT COUNT(*) FROM order_approvals
        WHERE approvalStatus != 'DELETED'
        AND (:status IS NULL OR approvalStatus = :status)
        AND (:serverId IS NULL OR serverId = :serverId)
        AND (:startTime IS NULL OR COALESCE(paymentTimestamp, createdAt) >= :startTime)
        AND (:endTime IS NULL OR COALESCE(paymentTimestamp, createdAt) <= :endTime)
        AND (:search IS NULL OR :search = ''
            OR orderNumber LIKE '%' || :search || '%'
            OR productName LIKE '%' || :search || '%'
            OR customerName LIKE '%' || :search || '%'
            OR websiteName LIKE '%' || :search || '%'
            OR serverName LIKE '%' || :search || '%'
            OR bank LIKE '%' || :search || '%'
        )
    """)
    suspend fun getFilteredOrdersCount(
        status: String?,
        serverId: Long?,
        startTime: Long?,
        endTime: Long?,
        search: String?
    ): Int

    @Query("SELECT * FROM order_approvals WHERE id = :id")
    suspend fun getById(id: Long): OrderApproval?

    @Query("SELECT * FROM order_approvals WHERE id = :id")
    suspend fun getOrderById(id: Long): OrderApproval?

    @Query("SELECT * FROM order_approvals WHERE remoteApprovalId = :remoteId AND serverId = :serverId")
    suspend fun getByRemoteId(remoteId: Long, serverId: Long): OrderApproval?

    @Query("SELECT * FROM order_approvals WHERE pendingAction IS NOT NULL LIMIT 100")
    suspend fun getPendingActions(): List<OrderApproval>

    @Query("SELECT COUNT(*) FROM order_approvals WHERE approvalStatus = 'PENDING_REVIEW'")
    fun getPendingReviewCount(): Flow<Int>

    @Query("SELECT * FROM order_approvals WHERE approvalStatus = 'PENDING_REVIEW' ORDER BY COALESCE(paymentTimestamp, createdAt) DESC LIMIT 200")
    suspend fun getPendingReviewOrders(): List<OrderApproval>

    @Query("SELECT COUNT(*) FROM order_approvals WHERE pendingAction IS NOT NULL")
    fun getOfflineQueueCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(syncedVersion), 0) FROM order_approvals WHERE serverId = :serverId")
    suspend fun getLatestSyncVersion(serverId: Long): Long

    @Query("UPDATE order_approvals SET approvalStatus = :status, pendingAction = :pendingAction, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ApprovalStatus, pendingAction: PendingAction?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM order_approvals")
    suspend fun getTotalOrderCount(): Int

    @Query("SELECT COUNT(*) FROM order_approvals WHERE approvalStatus IN ('AUTO_APPROVED', 'MANUALLY_APPROVED')")
    suspend fun getApprovedCount(): Int

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM order_approvals WHERE approvalStatus IN ('AUTO_APPROVED', 'MANUALLY_APPROVED') AND createdAt >= :since")
    suspend fun getApprovedAmount(since: Long): Double

    /**
     * รวมยอดบิลที่อนุมัติแล้ว (auto + manual) ตั้งแต่ :since — Flow สำหรับ dashboard "รายรับวันนี้"
     * ใช้ paymentTimestamp เป็นแกนเวลา ถ้า null fallback createdAt (ตรงกับการเรียงในหน้ารายการ)
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM order_approvals
        WHERE approvalStatus IN ('AUTO_APPROVED', 'MANUALLY_APPROVED')
        AND COALESCE(paymentTimestamp, createdAt) >= :since
    """)
    fun getApprovedAmountFlow(since: Long): Flow<Double>

    /**
     * รายรับรายวันจากบิลที่อนุมัติแล้ว — ใช้สำหรับกราฟรายได้ (เส้นเขียว)
     * คืนค่าเป็น YYYY-MM-DD (local time) → amount
     */
    @Query("""
        SELECT
            strftime('%Y-%m-%d', COALESCE(paymentTimestamp, createdAt)/1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(amount), 0.0) as amount
        FROM order_approvals
        WHERE approvalStatus IN ('AUTO_APPROVED', 'MANUALLY_APPROVED')
        AND COALESCE(paymentTimestamp, createdAt) >= :since
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyApprovedAmount(since: Long): List<DailyApprovedAmount>

    /**
     * รายรับรายเดือนจากบิลที่อนุมัติแล้ว — สำหรับกราฟรายปี
     * คืนค่าเป็น YYYY-MM (local time) → amount
     */
    @Query("""
        SELECT
            strftime('%Y-%m', COALESCE(paymentTimestamp, createdAt)/1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(amount), 0.0) as amount
        FROM order_approvals
        WHERE approvalStatus IN ('AUTO_APPROVED', 'MANUALLY_APPROVED')
        AND COALESCE(paymentTimestamp, createdAt) >= :since
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getMonthlyApprovedAmount(since: Long): List<DailyApprovedAmount>

    @Query("DELETE FROM order_approvals WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM order_approvals WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * ย้ายบิลที่หมดอายุ/ยกเลิกไปถังขยะ (soft delete → DELETED)
     * บิลที่ EXPIRED หรือ CANCELLED นานกว่า cutoff จะถูกย้าย
     */
    @Query("""
        UPDATE order_approvals
        SET approvalStatus = 'DELETED', updatedAt = :now
        WHERE approvalStatus IN ('EXPIRED', 'CANCELLED')
        AND updatedAt < :cutoff
    """)
    suspend fun softDeleteExpiredOrders(cutoff: Long, now: Long = System.currentTimeMillis()): Int

    /**
     * ลบบิลในถังขยะ (DELETED) ที่เก่ากว่า cutoff ออกจริง
     */
    @Query("DELETE FROM order_approvals WHERE approvalStatus = 'DELETED' AND updatedAt < :cutoff")
    suspend fun permanentlyDeleteOldTrash(cutoff: Long): Int
}
