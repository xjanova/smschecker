package com.thaiprompt.smschecker.data.repository

import com.thaiprompt.smschecker.data.api.*
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.model.*
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.util.RetryHelper
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
        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            return
        }
        val deviceId = secureStorage.getDeviceId() ?: return

        for (server in activeServers) {
            val apiKey = secureStorage.getApiKey(server.id) ?: continue

            // Retry with exponential backoff for unstable network
            val success = RetryHelper.withRetryBoolean {
                val client = apiClientFactory.getClient(server.baseUrl)
                val response = client.getOrders(apiKey, deviceId)
                if (response.isSuccessful) {
                    val orders = response.body()?.data?.data ?: emptyList()
                    if (orders.isNotEmpty()) {
                        val localOrders = orders.map { it.toLocalEntity(server.id) }
                        orderApprovalDao.insertAll(localOrders)
                    }
                    true
                } else {
                    false
                }
            }

            try {
                if (success) {
                    serverConfigDao.updateSyncStatus(server.id, System.currentTimeMillis(), "success")
                } else {
                    serverConfigDao.updateSyncStatus(server.id, System.currentTimeMillis(), "failed")
                }
            } catch (e: Exception) { }
        }
    }

    suspend fun approveOrder(order: OrderApproval) {
        val server = serverConfigDao.getById(order.serverId) ?: return
        val apiKey = secureStorage.getApiKey(server.id) ?: return
        val deviceId = secureStorage.getDeviceId() ?: return

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.approveOrder(apiKey, deviceId, order.remoteApprovalId)
            response.isSuccessful
        }

        if (success) {
            orderApprovalDao.updateStatus(order.id, ApprovalStatus.MANUALLY_APPROVED, null)
        } else {
            // Queue for offline sync
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
        }
    }

    /**
     * Auto-approve order by local ID (used for automatic approval after SMS match)
     * Returns true if successfully approved, false if failed or order not found
     */
    suspend fun approveOrder(orderId: Long): Boolean {
        val order = orderApprovalDao.getOrderById(orderId) ?: return false

        // Skip if already approved
        if (order.approvalStatus == ApprovalStatus.AUTO_APPROVED ||
            order.approvalStatus == ApprovalStatus.MANUALLY_APPROVED) {
            return true
        }

        val server = serverConfigDao.getById(order.serverId) ?: return false
        val apiKey = secureStorage.getApiKey(server.id) ?: return false
        val deviceId = secureStorage.getDeviceId() ?: return false

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.approveOrder(apiKey, deviceId, order.remoteApprovalId)
            response.isSuccessful
        }

        return if (success) {
            orderApprovalDao.updateStatus(order.id, ApprovalStatus.AUTO_APPROVED, null)
            true
        } else {
            // Queue for offline sync
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            false
        }
    }

    suspend fun rejectOrder(order: OrderApproval, reason: String = "") {
        val server = serverConfigDao.getById(order.serverId) ?: return
        val apiKey = secureStorage.getApiKey(server.id) ?: return
        val deviceId = secureStorage.getDeviceId() ?: return

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.rejectOrder(apiKey, deviceId, order.remoteApprovalId, RejectBody(reason))
            response.isSuccessful
        }

        if (success) {
            orderApprovalDao.updateStatus(order.id, ApprovalStatus.REJECTED, null)
        } else {
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
        }
    }

    suspend fun syncOfflineQueue() {
        val pending = orderApprovalDao.getPendingActions()
        val deviceId = secureStorage.getDeviceId() ?: return

        for (order in pending) {
            val server = serverConfigDao.getById(order.serverId) ?: continue
            val apiKey = secureStorage.getApiKey(server.id) ?: continue

            // Retry with exponential backoff for unstable network
            val success = RetryHelper.withRetryBoolean {
                val client = apiClientFactory.getClient(server.baseUrl)
                when (order.pendingAction) {
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
            }

            if (success) {
                val newStatus = when (order.pendingAction) {
                    PendingAction.APPROVE -> ApprovalStatus.MANUALLY_APPROVED
                    PendingAction.REJECT -> ApprovalStatus.REJECTED
                    null -> order.approvalStatus
                }
                orderApprovalDao.updateStatus(order.id, newStatus, null)
            }
            // Keep pending if failed, will retry next sync
        }
    }

    suspend fun pullServerChanges() {
        val activeServers = serverConfigDao.getActiveConfigs()
        val deviceId = secureStorage.getDeviceId() ?: return

        for (server in activeServers) {
            val apiKey = secureStorage.getApiKey(server.id) ?: continue

            // Retry with exponential backoff for unstable network
            val success = RetryHelper.withRetryBoolean {
                val sinceVersion = orderApprovalDao.getLatestSyncVersion(server.id)
                val client = apiClientFactory.getClient(server.baseUrl)
                val response = client.syncOrders(apiKey, deviceId, sinceVersion)
                if (response.isSuccessful) {
                    val orders = response.body()?.data?.orders ?: emptyList()
                    for (remote in orders) {
                        val existing = orderApprovalDao.getByRemoteId(remote.id, server.id)
                        val remoteStatus = ApprovalStatus.fromApiValue(remote.approval_status)

                        // Handle server-side deletion — remove from local DB
                        if (remoteStatus == ApprovalStatus.DELETED) {
                            if (existing != null) {
                                orderApprovalDao.deleteById(existing.id)
                            }
                            continue
                        }

                        // Local pending action wins — don't overwrite
                        if (existing != null && existing.pendingAction != null) {
                            continue
                        }

                        // Convert remote to local entity (includes updated amount, status, details)
                        val local = remote.toLocalEntity(server.id)
                        if (existing != null) {
                            orderApprovalDao.update(local.copy(id = existing.id))
                        } else {
                            orderApprovalDao.insert(local)
                        }
                    }
                    true
                } else {
                    false
                }
            }

            try {
                if (success) {
                    serverConfigDao.updateSyncStatus(server.id, System.currentTimeMillis(), "success")
                } else {
                    serverConfigDao.updateSyncStatus(server.id, System.currentTimeMillis(), "failed")
                }
            } catch (e: Exception) { }
        }
    }

    suspend fun fetchDashboardStats(days: Int = 7): DashboardStats {
        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            return DashboardStats()
        }
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
            } catch (e: Exception) {
                // Skip failed server - network error, parse error, etc.
            }
        }

        return combined
    }

    /**
     * ส่ง FCM token ไปยังเซิร์ฟเวอร์ทุกตัวเพื่อรับ push notifications
     */
    suspend fun registerFcmToken(fcmToken: String) {
        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            return
        }
        val deviceId = secureStorage.getDeviceId() ?: return

        for (server in activeServers) {
            val apiKey = secureStorage.getApiKey(server.id) ?: continue
            try {
                val client = apiClientFactory.getClient(server.baseUrl)
                client.registerDevice(
                    apiKey = apiKey,
                    body = DeviceRegistration(
                        device_id = deviceId,
                        device_name = android.os.Build.MODEL,
                        platform = "android",
                        app_version = "1.6.0",
                        fcm_token = fcmToken
                    )
                )
            } catch (e: Exception) {
                // Best effort — will retry on next sync
            }
        }
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
            } catch (e: Exception) {
                // Best effort
            }
        }
    }
}

fun RemoteOrderApproval.toLocalEntity(serverId: Long): OrderApproval {
    val details = order_details_json
    // Server may update amount via order_details_json — prefer it over notification amount
    val serverAmount = (details?.get("amount") as? Number)?.toDouble()
    val notifAmount = notification?.amount?.toDoubleOrNull()
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
        amount = serverAmount ?: notifAmount ?: 0.0,
        bank = notification?.bank,
        paymentTimestamp = null,
        approvedBy = approved_by,
        rejectionReason = rejection_reason,
        syncedVersion = synced_version,
        lastSyncedAt = System.currentTimeMillis()
    )
}
