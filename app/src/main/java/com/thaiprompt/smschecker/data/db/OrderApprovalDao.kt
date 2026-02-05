package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.PendingAction
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderApprovalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderApproval): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<OrderApproval>)

    @Update
    suspend fun update(order: OrderApproval)

    @Query("SELECT * FROM order_approvals WHERE approvalStatus != 'DELETED' ORDER BY createdAt DESC")
    fun getAllOrders(): Flow<List<OrderApproval>>

    @Query("SELECT * FROM order_approvals WHERE approvalStatus = :status ORDER BY createdAt DESC")
    fun getOrdersByStatus(status: ApprovalStatus): Flow<List<OrderApproval>>

    @Query("SELECT * FROM order_approvals WHERE serverId = :serverId ORDER BY createdAt DESC")
    fun getOrdersByServer(serverId: Long): Flow<List<OrderApproval>>

    @Query("SELECT * FROM order_approvals WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun getOrdersByDateRange(startTime: Long, endTime: Long): Flow<List<OrderApproval>>

    @Query("""
        SELECT * FROM order_approvals
        WHERE approvalStatus != 'DELETED'
        AND (:status IS NULL OR approvalStatus = :status)
        AND (:serverId IS NULL OR serverId = :serverId)
        AND (:startTime IS NULL OR createdAt >= :startTime)
        AND (:endTime IS NULL OR createdAt <= :endTime)
        ORDER BY createdAt DESC
    """)
    fun getFilteredOrders(
        status: ApprovalStatus?,
        serverId: Long?,
        startTime: Long?,
        endTime: Long?
    ): Flow<List<OrderApproval>>

    @Query("SELECT * FROM order_approvals WHERE id = :id")
    suspend fun getById(id: Long): OrderApproval?

    @Query("SELECT * FROM order_approvals WHERE id = :id")
    suspend fun getOrderById(id: Long): OrderApproval?

    @Query("SELECT * FROM order_approvals WHERE remoteApprovalId = :remoteId AND serverId = :serverId")
    suspend fun getByRemoteId(remoteId: Long, serverId: Long): OrderApproval?

    @Query("SELECT * FROM order_approvals WHERE pendingAction IS NOT NULL")
    suspend fun getPendingActions(): List<OrderApproval>

    @Query("SELECT COUNT(*) FROM order_approvals WHERE approvalStatus = 'PENDING_REVIEW'")
    fun getPendingReviewCount(): Flow<Int>

    @Query("SELECT * FROM order_approvals WHERE approvalStatus = 'PENDING_REVIEW' ORDER BY createdAt DESC")
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

    @Query("DELETE FROM order_approvals WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM order_approvals WHERE id = :id")
    suspend fun deleteById(id: Long)
}
