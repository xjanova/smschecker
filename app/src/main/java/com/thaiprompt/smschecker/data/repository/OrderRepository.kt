package com.thaiprompt.smschecker.data.repository

import com.thaiprompt.smschecker.data.api.*
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.model.*
import com.thaiprompt.smschecker.security.SecureStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val orderApprovalDao: OrderApprovalDao,
    private val serverConfigDao: ServerConfigDao,
    private val apiClientFactory: ApiClientFactory,
    private val secureStorage: SecureStorage
) {

    fun getAllOrders(): Flow<List<OrderApproval>> = orderApprovalDao.getAllOrders()

    fun getOrdersByStatus(status: ApprovalStatus): Flow<List<OrderApproval>> =
        orderApprovalDao.getOrdersByStatus(status)

    fun getFilteredOrders(
        status: ApprovalStatus?,
        serverId: Long?,
        startTime: Long?,
        endTime: Long?
    ): Flow<List<OrderApproval>> = orderApprovalDao.getFilteredOrders(status, serverId, startTime, endTime)

    fun getPendingReviewCount(): Flow<Int> = orderApprovalDao.getPendingReviewCount()

    fun getOfflineQueueCount(): Flow<Int> = orderApprovalDao.getOfflineQueueCount()

    suspend fun fetchOrders() {
        val activeServers = serverConfigDao.getActiveConfigs()
        val deviceId = secureStorage.getDeviceId() ?: return

        for (server in activeServers) {
            val apiKey = secureStorage.getApiKey(server.id) ?: continue
            try {
                val client = apiClientFactory.getClient(server.baseUrl)
                val response = client.getOrders(apiKey, deviceId)
                if (response.isSuccessful) {
                    val orders = response.body()?.data?.data ?: continue
                    val localOrders = orders.map { it.toLocalEntity(server.id) }
                    orderApprovalDao.insertAll(localOrders)
                }
            } catch (_: Exception) {
                // Silently skip failed servers
            }
        }
    }

    suspend fun approveOrder(order: OrderApproval) {
        val server = serverConfigDao.getById(order.serverId) ?: return
        val apiKey = secureStorage.getApiKey(server.id) ?: return
        val deviceId = secureStorage.getDeviceId() ?: return

        try {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.approveOrder(apiKey, deviceId, order.remoteApprovalId)
            if (response.isSuccessful) {
                orderApprovalDao.updateStatus(order.id, ApprovalStatus.MANUALLY_APPROVED, null)
            } else {
                // Queue for offline sync
                orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            }
        } catch (_: Exception) {
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
        }
    }

    suspend fun rejectOrder(order: OrderApproval, reason: String = "") {
        val server = serverConfigDao.getById(order.serverId) ?: return
        val apiKey = secureStorage.getApiKey(server.id) ?: return
        val deviceId = secureStorage.getDeviceId() ?: return

        try {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.rejectOrder(apiKey, deviceId, order.remoteApprovalId, RejectBody(reason))
            if (response.isSuccessful) {
                orderApprovalDao.updateStatus(order.id, ApprovalStatus.REJECTED, null)
            } else {
                orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
            }
        } catch (_: Exception) {
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
        }
    }

    suspend fun syncOfflineQueue() {
        val pending = orderApprovalDao.getPendingActions()
        val deviceId = secureStorage.getDeviceId() ?: return

        for (order in pending) {
            val server = serverConfigDao.getById(order.serverId) ?: continue
            val apiKey = secureStorage.getApiKey(server.id) ?: continue

            try {
                val client = apiClientFactory.getClient(server.baseUrl)
                val success = when (order.pendingAction) {
                    PendingAction.APPROVE -> {
                        val resp = client.approveOrder(apiKey, deviceId, order.remoteApprovalId)
                        resp.isSuccessful
                    }
                    PendingAction.REJECT -> {
                        val resp = client.rejectOrder(
                            apiKey, deviceId, order.remoteApprovalId,
                            RejectBody(order.rejectionReason ?: "")
                        )
                        resp.isSuccessful
                    }
                    null -> true
                }

                if (success) {
                    val newStatus = when (order.pendingAction) {
                        PendingAction.APPROVE -> ApprovalStatus.MANUALLY_APPROVED
                        PendingAction.REJECT -> ApprovalStatus.REJECTED
                        null -> order.approvalStatus
                    }
                    orderApprovalDao.updateStatus(order.id, newStatus, null)
                }
            } catch (_: Exception) {
                // Keep pending, retry next sync
            }
        }
    }

    suspend fun pullServerChanges() {
        val activeServers = serverConfigDao.getActiveConfigs()
        val deviceId = secureStorage.getDeviceId() ?: return

        for (server in activeServers) {
            val apiKey = secureStorage.getApiKey(server.id) ?: continue
            try {
                val sinceVersion = orderApprovalDao.getLatestSyncVersion(server.id)
                val client = apiClientFactory.getClient(server.baseUrl)
                val response = client.syncOrders(apiKey, deviceId, sinceVersion)
                if (response.isSuccessful) {
                    val orders = response.body()?.data?.orders ?: continue
                    for (remote in orders) {
                        val existing = orderApprovalDao.getByRemoteId(remote.id, server.id)
                        // Local pending action wins — don't overwrite
                        if (existing != null && existing.pendingAction != null) {
                            continue
                        }
                        val local = remote.toLocalEntity(server.id)
                        if (existing != null) {
                            orderApprovalDao.update(local.copy(id = existing.id))
                        } else {
                            orderApprovalDao.insert(local)
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip failed server
            }
        }
    }

    suspend fun fetchDashboardStats(days: Int = 7): DashboardStats {
        val activeServers = serverConfigDao.getActiveConfigs()
        val deviceId = secureStorage.getDeviceId() ?: return DashboardStats()

        var combined = DashboardStats()

        for (server in activeServers) {
            val apiKey = secureStorage.getApiKey(server.id) ?: continue
            try {
                val client = apiClientFactory.getClient(server.baseUrl)
                val response = client.getDashboardStats(apiKey, deviceId, days)
                if (response.isSuccessful) {
                    val stats = response.body()?.data ?: continue
                    combined = combined.copy(
                        totalOrders = combined.totalOrders + stats.total_orders,
                        autoApproved = combined.autoApproved + stats.auto_approved,
                        manuallyApproved = combined.manuallyApproved + stats.manually_approved,
                        pendingReview = combined.pendingReview + stats.pending_review,
                        rejected = combined.rejected + stats.rejected,
                        totalAmount = combined.totalAmount + stats.total_amount,
                        dailyBreakdown = stats.daily_breakdown.map {
                            DailyStats(
                                date = it.date,
                                count = it.count,
                                approved = it.approved,
                                rejected = it.rejected,
                                amount = it.amount
                            )
                        }
                    )
                }
            } catch (_: Exception) {
                // Skip failed server
            }
        }

        return combined
    }

    suspend fun updateApprovalMode(mode: ApprovalMode) {
        val activeServers = serverConfigDao.getActiveConfigs()
        val deviceId = secureStorage.getDeviceId() ?: return

        secureStorage.setApprovalMode(mode.apiValue)

        for (server in activeServers) {
            val apiKey = secureStorage.getApiKey(server.id) ?: continue
            try {
                val client = apiClientFactory.getClient(server.baseUrl)
                client.updateDeviceSettings(apiKey, deviceId, UpdateSettingsBody(mode.apiValue))
            } catch (_: Exception) {
                // Best effort
            }
        }
    }
}

fun RemoteOrderApproval.toLocalEntity(serverId: Long): OrderApproval {
    val details = order_details_json
    return OrderApproval(
        serverId = serverId,
        remoteApprovalId = id,
        notificationId = notification_id,
        matchedTransactionId = matched_transaction_id,
        approvalStatus = ApprovalStatus.fromApiValue(approval_status),
        confidence = MatchConfidence.fromApiValue(confidence),
        orderNumber = details?.get("order_number")?.toString(),
        productName = details?.get("product_name")?.toString(),
        productDetails = details?.get("product_details")?.toString(),
        quantity = (details?.get("quantity") as? Number)?.toInt(),
        websiteName = details?.get("website_name")?.toString(),
        customerName = details?.get("customer_name")?.toString(),
        amount = notification?.amount?.toDoubleOrNull() ?: 0.0,
        bank = notification?.bank,
        paymentTimestamp = null, // Parsed from notification.sms_timestamp if needed
        approvedBy = approved_by,
        syncedVersion = synced_version,
        lastSyncedAt = System.currentTimeMillis()
    )
}
