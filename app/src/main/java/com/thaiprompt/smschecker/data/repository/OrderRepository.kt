package com.thaiprompt.smschecker.data.repository

import android.util.Log
import com.thaiprompt.smschecker.data.api.*
import com.thaiprompt.smschecker.data.db.MatchHistoryDao
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.model.*
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.util.ParallelSyncHelper
import com.thaiprompt.smschecker.util.RetryHelper
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val orderApprovalDao: OrderApprovalDao,
    private val serverConfigDao: ServerConfigDao,
    private val matchHistoryDao: MatchHistoryDao,
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

    /**
     * Get list of pending review orders (for orphan matching).
     */
    suspend fun getPendingOrdersList(): List<OrderApproval> = orderApprovalDao.getPendingReviewOrders()

    /**
     * Fetch orders from all active servers in PARALLEL.
     * Much faster than sequential fetch, especially with multiple servers.
     */
    suspend fun fetchOrders() {
        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            return
        }
        val deviceId = secureStorage.getDeviceId() ?: return

        if (activeServers.isEmpty()) return

        // Prepare server list for parallel execution
        val serverList = activeServers.mapNotNull { server ->
            val apiKey = secureStorage.getApiKey(server.id)
            if (apiKey != null) server.id to server.name else null
        }

        // Execute in parallel with 15s timeout per server
        val results = ParallelSyncHelper.executeParallel(
            servers = serverList,
            maxConcurrency = 5,
            timeoutMs = 15_000L
        ) { serverId ->
            fetchOrdersFromServer(serverId, deviceId)
        }

        // Update sync status for each server
        for (result in results.results) {
            try {
                serverConfigDao.updateSyncStatus(
                    result.serverId,
                    System.currentTimeMillis(),
                    if (result.success) "success" else "failed"
                )
            } catch (e: Exception) {
                Log.e("OrderRepository", "Failed to update sync status for ${result.serverName}", e)
            }
        }

        Log.d("OrderRepository", "Parallel fetch completed: ${results.successCount}/${results.results.size} servers in ${results.totalDurationMs}ms")
    }

    /**
     * Fetch orders from a single server (used internally).
     */
    private suspend fun fetchOrdersFromServer(serverId: Long, deviceId: String): Boolean {
        val server = serverConfigDao.getById(serverId) ?: return false
        val apiKey = secureStorage.getApiKey(serverId) ?: return false

        return RetryHelper.withRetryBoolean(maxRetries = 2) {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.getOrders(apiKey, deviceId)
            if (response.isSuccessful) {
                val orders = response.body()?.data?.data ?: emptyList()
                if (orders.isNotEmpty()) {
                    val localOrders = orders.map { it.toLocalEntity(serverId) }
                    try {
                        orderApprovalDao.insertAll(localOrders)
                    } catch (e: Exception) {
                        Log.e("OrderRepository", "Failed to insert orders", e)
                    }
                }
                true
            } else {
                false
            }
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

    /**
     * Pull changes from all active servers in PARALLEL.
     */
    suspend fun pullServerChanges() {
        val activeServers = serverConfigDao.getActiveConfigs()
        val deviceId = secureStorage.getDeviceId() ?: return

        if (activeServers.isEmpty()) return

        // Prepare server list for parallel execution
        val serverList = activeServers.mapNotNull { server ->
            val apiKey = secureStorage.getApiKey(server.id)
            if (apiKey != null) server.id to server.name else null
        }

        // Execute in parallel
        val results = ParallelSyncHelper.executeParallel(
            servers = serverList,
            maxConcurrency = 5,
            timeoutMs = 15_000L
        ) { serverId ->
            pullChangesFromServer(serverId, deviceId)
        }

        // Update sync status
        for (result in results.results) {
            try {
                serverConfigDao.updateSyncStatus(
                    result.serverId,
                    System.currentTimeMillis(),
                    if (result.success) "success" else "failed"
                )
            } catch (e: Exception) { }
        }

        Log.d("OrderRepository", "Parallel pull completed: ${results.successCount}/${results.results.size} servers in ${results.totalDurationMs}ms")
    }

    /**
     * Pull changes from a single server (used internally).
     */
    private suspend fun pullChangesFromServer(serverId: Long, deviceId: String): Boolean {
        val server = serverConfigDao.getById(serverId) ?: return false
        val apiKey = secureStorage.getApiKey(serverId) ?: return false

        return RetryHelper.withRetryBoolean(maxRetries = 2) {
            val sinceVersion = orderApprovalDao.getLatestSyncVersion(serverId)
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.syncOrders(apiKey, deviceId, sinceVersion)
            if (response.isSuccessful) {
                val orders = response.body()?.data?.orders ?: emptyList()
                for (remote in orders) {
                    val existing = orderApprovalDao.getByRemoteId(remote.id, serverId)
                    val remoteStatus = ApprovalStatus.fromApiValue(remote.approval_status)

                    // Handle server-side deletion
                    if (remoteStatus == ApprovalStatus.DELETED) {
                        if (existing != null) {
                            orderApprovalDao.deleteById(existing.id)
                        }
                        continue
                    }

                    // Local pending action wins
                    if (existing != null && existing.pendingAction != null) {
                        continue
                    }

                    val local = remote.toLocalEntity(serverId)
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
     * ส่ง FCM token ไปยังเซิร์ฟเวอร์ทุกตัวเพื่อรับ push notifications (PARALLEL)
     */
    suspend fun registerFcmToken(fcmToken: String) {
        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            return
        }
        val deviceId = secureStorage.getDeviceId() ?: return

        if (activeServers.isEmpty()) return

        val serverList = activeServers.mapNotNull { server ->
            val apiKey = secureStorage.getApiKey(server.id)
            if (apiKey != null) server.id to server.name else null
        }

        // Register to all servers in parallel (fire-and-forget, best effort)
        ParallelSyncHelper.executeParallel(
            servers = serverList,
            maxConcurrency = 5,
            timeoutMs = 10_000L
        ) { serverId ->
            val server = serverConfigDao.getById(serverId) ?: return@executeParallel
            val apiKey = secureStorage.getApiKey(serverId) ?: return@executeParallel
            val client = apiClientFactory.getClient(server.baseUrl)
            client.registerDevice(
                apiKey = apiKey,
                body = DeviceRegistration(
                    device_id = deviceId,
                    device_name = android.os.Build.MODEL,
                    platform = "android",
                    app_version = "1.9.0",
                    fcm_token = fcmToken
                )
            )
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

    // =====================================================================
    // Match-Only Mode: Query servers with SMS amount
    // =====================================================================

    /**
     * Match order by SMS amount - queries all active servers in PARALLEL.
     * Called when SMS is received instead of fetching all orders.
     *
     * @param amount The exact SMS amount to match (e.g., "500.37")
     * @param bank Optional bank name for logging
     * @param transactionTimestamp Timestamp of the SMS transaction
     * @return MatchResult containing the matched order, server ID, and query statistics
     */
    suspend fun matchOrderByAmount(
        amount: Double,
        bank: String = "Unknown",
        transactionTimestamp: Long = System.currentTimeMillis()
    ): MatchResult? {
        val startTime = System.currentTimeMillis()
        val queriesCounter = AtomicInteger(0)

        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get active servers", e)
            return null
        }
        val deviceId = secureStorage.getDeviceId() ?: return null

        if (activeServers.isEmpty()) {
            Log.d("OrderRepository", "No active servers to query")
            return null
        }

        val amountStr = "%.2f".format(amount)
        Log.i("OrderRepository", "Matching order by amount: $amountStr on ${activeServers.size} servers")

        // Query all servers in PARALLEL
        val serverList = activeServers.mapNotNull { server ->
            val apiKey = secureStorage.getApiKey(server.id)
            if (apiKey != null) server.id to server.name else null
        }

        var matchedResult: MatchResult? = null

        val results = ParallelSyncHelper.executeParallel(
            servers = serverList,
            maxConcurrency = 5,
            timeoutMs = 15_000L
        ) { serverId ->
            queriesCounter.incrementAndGet()
            val result = matchOrderFromServer(serverId, deviceId, amountStr)
            if (result != null) {
                matchedResult = result
            }
        }

        val matchDuration = System.currentTimeMillis() - startTime
        val totalQueries = queriesCounter.get()

        if (matchedResult != null) {
            Log.i("OrderRepository", "✅ Order matched! server=${matchedResult!!.serverId}, order=${matchedResult!!.order.remoteApprovalId}, queries=$totalQueries, duration=${matchDuration}ms")

            // Save match history
            try {
                val history = MatchHistory(
                    amount = amount,
                    amountString = amountStr,
                    bank = bank,
                    transactionTimestamp = transactionTimestamp,
                    serverId = matchedResult!!.serverId,
                    serverName = matchedResult!!.serverName,
                    orderNumber = matchedResult!!.order.orderNumber,
                    remoteOrderId = matchedResult!!.order.remoteApprovalId,
                    serverQueriesCount = totalQueries,
                    totalServersQueried = serverList.size,
                    matchDurationMs = matchDuration,
                    matchResult = com.thaiprompt.smschecker.data.model.MatchResult.SUCCESS
                )
                matchHistoryDao.insert(history)
                Log.d("OrderRepository", "💾 Saved match history: $totalQueries queries, ${matchDuration}ms")
            } catch (e: Exception) {
                Log.e("OrderRepository", "Failed to save match history", e)
            }
        } else {
            Log.d("OrderRepository", "⏳ No matching order found on any server for amount $amountStr (queries=$totalQueries, duration=${matchDuration}ms)")
        }

        return matchedResult
    }

    /**
     * Query a single server for matching order by amount.
     */
    private suspend fun matchOrderFromServer(serverId: Long, deviceId: String, amount: String): MatchResult? {
        val server = serverConfigDao.getById(serverId) ?: return null
        val apiKey = secureStorage.getApiKey(serverId) ?: return null

        return try {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.matchOrderByAmount(apiKey, deviceId, amount)

            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data?.matched == true && data.order != null) {
                    val localOrder = data.order.toLocalEntity(serverId)
                    // Save to local database
                    try {
                        val existingId = orderApprovalDao.getByRemoteId(data.order.id, serverId)?.id
                        if (existingId != null) {
                            orderApprovalDao.update(localOrder.copy(id = existingId))
                        } else {
                            orderApprovalDao.insert(localOrder)
                        }
                    } catch (e: Exception) {
                        Log.e("OrderRepository", "Failed to save matched order", e)
                    }

                    // Update sync status
                    serverConfigDao.updateSyncStatus(serverId, System.currentTimeMillis(), "success")

                    MatchResult(
                        serverId = serverId,
                        serverName = server.name,
                        order = localOrder
                    )
                } else {
                    null
                }
            } else {
                Log.w("OrderRepository", "Match request failed for ${server.name}: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("OrderRepository", "Match request error for ${server.name}", e)
            null
        }
    }

    /**
     * Result of matching order by amount.
     */
    data class MatchResult(
        val serverId: Long,
        val serverName: String,
        val order: OrderApproval
    )
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
