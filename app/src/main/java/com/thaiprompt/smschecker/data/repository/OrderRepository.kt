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
     * ทำความสะอาดบิลที่หมดอายุ/ยกเลิก
     * - บิล EXPIRED/CANCELLED เก่ากว่า 1 ชม. → ย้ายไปถังขยะ (DELETED)
     * - บิล DELETED เก่ากว่า 7 วัน → ลบถาวร
     */
    suspend fun cleanupExpiredOrders() {
        try {
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000L)
            val softDeleted = orderApprovalDao.softDeleteExpiredOrders(cutoff = oneHourAgo)
            if (softDeleted > 0) {
                Log.i("OrderRepository", "Moved $softDeleted expired/cancelled orders to trash")
            }

            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val permanentlyDeleted = orderApprovalDao.permanentlyDeleteOldTrash(cutoff = sevenDaysAgo)
            if (permanentlyDeleted > 0) {
                Log.i("OrderRepository", "Permanently deleted $permanentlyDeleted old trash orders")
            }
        } catch (e: Exception) {
            Log.e("OrderRepository", "Error cleaning up expired orders", e)
        }
    }

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
     * ดึงทั้ง pending และ recent completed/failed เพื่ออัพเดทสถานะในแอพ
     */
    private suspend fun fetchOrdersFromServer(serverId: Long, deviceId: String): Boolean {
        val server = serverConfigDao.getById(serverId) ?: return false
        val apiKey = secureStorage.getApiKey(serverId) ?: return false

        return RetryHelper.withRetryBoolean(maxRetries = 2) {
            val client = apiClientFactory.getClient(server.baseUrl)
            // ดึง orders ทุกสถานะเพื่อให้แอพอัพเดทสถานะบิลได้ถูกต้อง
            val response = client.getOrders(apiKey, deviceId, status = "all", perPage = 50)
            if (response.isSuccessful) {
                val orders = response.body()?.data?.data ?: emptyList()
                Log.d("OrderRepository", "Fetched ${orders.size} orders from ${server.name} (id=$serverId)")
                if (orders.isNotEmpty()) {
                    try {
                        // Smart upsert: ป้องกัน pendingAction (offline queue) หาย
                        // และป้องกัน server เขียนทับ approved status ที่แอพ approve ไปแล้ว
                        for (remote in orders) {
                            val localOrder = remote.toLocalEntity(serverId)
                            val existing = orderApprovalDao.getByRemoteId(remote.id, serverId)

                            if (existing != null) {
                                // ถ้า local มี pendingAction ค้างอยู่ → ข้ามไป (offline action สำคัญกว่า)
                                if (existing.pendingAction != null) {
                                    continue
                                }

                                // ป้องกัน green-to-red revert:
                                // ถ้า local เป็น approved แล้ว แต่ server ยังเป็น pending_review → ข้ามไป
                                // เพราะ server อาจยัง process approve ไม่เสร็จ (race condition)
                                val remoteStatus = ApprovalStatus.fromApiValue(remote.approval_status)
                                val localIsApproved = existing.approvalStatus == ApprovalStatus.AUTO_APPROVED ||
                                        existing.approvalStatus == ApprovalStatus.MANUALLY_APPROVED
                                val remoteIsPending = remoteStatus == ApprovalStatus.PENDING_REVIEW

                                if (localIsApproved && remoteIsPending) {
                                    Log.d("OrderRepository", "Skipping revert for order ${remote.id}: local=${existing.approvalStatus}, remote=$remoteStatus")
                                    continue
                                }

                                orderApprovalDao.update(localOrder.copy(id = existing.id))
                            } else {
                                orderApprovalDao.insert(localOrder)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OrderRepository", "Failed to upsert orders", e)
                    }
                }
                true
            } else {
                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                Log.e("OrderRepository", "Failed to fetch orders from ${server.name}: HTTP ${response.code()} - $errorBody")
                // Save descriptive error status (e.g. "failed:403") for UI to display
                try {
                    serverConfigDao.updateSyncStatus(serverId, System.currentTimeMillis(), "failed:${response.code()}")
                } catch (_: Exception) { }
                false
            }
        }
    }

    suspend fun approveOrder(order: OrderApproval) {
        val server = serverConfigDao.getById(order.serverId) ?: return
        val apiKey = secureStorage.getApiKey(server.id) ?: return
        val deviceId = secureStorage.getDeviceId() ?: return

        // ใช้ orderNumber (bill_reference) ถ้ามี, fallback เป็น remoteApprovalId
        val identifier = order.orderNumber ?: order.remoteApprovalId.toString()

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.approveOrder(apiKey, deviceId, identifier)
            if (response.isSuccessful) {
                true
            } else if (response.code() == 422) {
                // Server บอกว่า order approved แล้ว → ถือว่าสำเร็จ
                Log.i("OrderRepository", "Order $identifier already approved on server (422)")
                true
            } else {
                false
            }
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

        // ใช้ orderNumber (bill_reference) ถ้ามี, fallback เป็น remoteApprovalId
        val identifier = order.orderNumber ?: order.remoteApprovalId.toString()

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.approveOrder(apiKey, deviceId, identifier)
            if (response.isSuccessful) {
                true
            } else if (response.code() == 422) {
                // Server บอกว่า transaction ไม่ได้ pending แล้ว (อาจ auto-approved ตอน match)
                // ถือว่าสำเร็จ ไม่ต้อง retry ซ้ำตลอด
                Log.i("OrderRepository", "Order $identifier already approved on server (422)")
                true
            } else {
                false
            }
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

        // ใช้ orderNumber (bill_reference) ถ้ามี, fallback เป็น remoteApprovalId
        val identifier = order.orderNumber ?: order.remoteApprovalId.toString()

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.rejectOrder(apiKey, deviceId, identifier, RejectBody(reason))
            if (response.isSuccessful) {
                true
            } else if (response.code() == 422) {
                // Server บอกว่า order rejected แล้ว → ถือว่าสำเร็จ
                Log.i("OrderRepository", "Order $identifier already rejected on server (422)")
                true
            } else {
                false
            }
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

            // ใช้ orderNumber (bill_reference) ถ้ามี, fallback เป็น remoteApprovalId
            val identifier = order.orderNumber ?: order.remoteApprovalId.toString()

            // Retry with exponential backoff for unstable network
            val success = RetryHelper.withRetryBoolean {
                val client = apiClientFactory.getClient(server.baseUrl)
                when (order.pendingAction) {
                    PendingAction.APPROVE -> {
                        val resp = client.approveOrder(apiKey, deviceId, identifier)
                        if (resp.isSuccessful) {
                            true
                        } else if (resp.code() == 422) {
                            // Already approved on server → treat as success
                            Log.i("OrderRepository", "Offline queue: Order $identifier already approved (422)")
                            true
                        } else {
                            false
                        }
                    }
                    PendingAction.REJECT -> {
                        val resp = client.rejectOrder(
                            apiKey, deviceId, identifier,
                            RejectBody(order.rejectionReason ?: "")
                        )
                        if (resp.isSuccessful) {
                            true
                        } else if (resp.code() == 422) {
                            // Already rejected on server → treat as success
                            Log.i("OrderRepository", "Offline queue: Order $identifier already rejected (422)")
                            true
                        } else {
                            false
                        }
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
                Log.d("OrderRepository", "Synced ${orders.size} orders from ${server.name} (id=$serverId)")
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

                    if (existing != null) {
                        // Local pending action wins
                        if (existing.pendingAction != null) {
                            continue
                        }

                        // ป้องกัน green-to-red revert:
                        // ถ้า local เป็น approved แล้ว แต่ server ยังเป็น pending_review → ข้ามไป
                        val localIsApproved = existing.approvalStatus == ApprovalStatus.AUTO_APPROVED ||
                                existing.approvalStatus == ApprovalStatus.MANUALLY_APPROVED
                        val remoteIsPending = remoteStatus == ApprovalStatus.PENDING_REVIEW

                        if (localIsApproved && remoteIsPending) {
                            Log.d("OrderRepository", "Skipping sync revert for order ${remote.id}: local=${existing.approvalStatus}, remote=$remoteStatus")
                            continue
                        }
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
                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                Log.e("OrderRepository", "Failed to sync from ${server.name}: HTTP ${response.code()} - $errorBody")
                // Save descriptive error status (e.g. "failed:403") for UI to display
                try {
                    serverConfigDao.updateSyncStatus(serverId, System.currentTimeMillis(), "failed:${response.code()}")
                } catch (_: Exception) { }
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
     * @return true ถ้าส่งไปอย่างน้อย 1 เซิร์ฟเวอร์สำเร็จ, false ถ้าไม่ได้ส่งเลย
     */
    suspend fun registerFcmToken(fcmToken: String): Boolean {
        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            android.util.Log.w("OrderRepository", "registerFcmToken: Failed to get active configs: ${e.message}")
            return false
        }
        val deviceId = secureStorage.getDeviceId()
        if (deviceId == null) {
            android.util.Log.w("OrderRepository", "registerFcmToken: No device ID")
            return false
        }

        if (activeServers.isEmpty()) {
            android.util.Log.w("OrderRepository", "registerFcmToken: No active servers configured")
            return false
        }

        val serverList = activeServers.mapNotNull { server ->
            val apiKey = secureStorage.getApiKey(server.id)
            if (apiKey != null) server.id to server.name else null
        }

        if (serverList.isEmpty()) {
            android.util.Log.w("OrderRepository", "registerFcmToken: No servers with API keys")
            return false
        }

        android.util.Log.i("OrderRepository", "registerFcmToken: Sending to ${serverList.size} server(s)...")

        var sentCount = 0
        // Register to all servers in parallel (fire-and-forget, best effort)
        ParallelSyncHelper.executeParallel(
            servers = serverList,
            maxConcurrency = 5,
            timeoutMs = 10_000L
        ) { serverId ->
            val server = serverConfigDao.getById(serverId) ?: return@executeParallel
            val apiKey = secureStorage.getApiKey(serverId) ?: return@executeParallel
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.registerDevice(
                apiKey = apiKey,
                body = DeviceRegistration(
                    device_id = deviceId,
                    device_name = android.os.Build.MODEL,
                    platform = "android",
                    app_version = com.thaiprompt.smschecker.BuildConfig.VERSION_NAME,
                    fcm_token = fcmToken
                )
            )
            if (response.isSuccessful) {
                sentCount++
                android.util.Log.i("OrderRepository", "registerFcmToken: Sent to ${server.name} successfully")
            } else {
                android.util.Log.w("OrderRepository", "registerFcmToken: Failed for ${server.name}: HTTP ${response.code()}")
            }
        }

        android.util.Log.i("OrderRepository", "registerFcmToken: Done. Sent to $sentCount/${serverList.size} servers")
        return sentCount > 0
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
                    var localOrder = data.order.toLocalEntity(serverId)
                    // Save to local database and get actual ID
                    try {
                        val existingId = orderApprovalDao.getByRemoteId(data.order.id, serverId)?.id
                        if (existingId != null) {
                            localOrder = localOrder.copy(id = existingId)
                            orderApprovalDao.update(localOrder)
                        } else {
                            val insertedId = orderApprovalDao.insert(localOrder)
                            localOrder = localOrder.copy(id = insertedId)
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
                // Save descriptive error status for UI
                try {
                    serverConfigDao.updateSyncStatus(serverId, System.currentTimeMillis(), "failed:${response.code()}")
                } catch (_: Exception) { }
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

    // แปลง created_at (ISO 8601) จากเซิร์ฟเป็น milliseconds สำหรับแสดงเวลาสร้างบิลจริง
    val serverCreatedAtMs = created_at?.let { parseIso8601ToMillis(it) }

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
        paymentTimestamp = serverCreatedAtMs,
        serverName = server_name,
        approvedBy = approved_by,
        rejectionReason = rejection_reason,
        syncedVersion = synced_version,
        lastSyncedAt = System.currentTimeMillis()
    )
}

/**
 * แปลง ISO 8601 timestamp string เป็น milliseconds
 * รองรับ formats: "2025-02-07T15:30:45+07:00", "2025-02-07T08:30:45.000000Z"
 */
private fun parseIso8601ToMillis(isoString: String): Long? {
    return try {
        val cleaned = isoString.replace("T", " ").replace("Z", "+00:00")
        // ลอง parse ด้วย SimpleDateFormat หลาย format
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )
        for (format in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                return sdf.parse(isoString)?.time
            } catch (_: Exception) { }
        }
        // Fallback: ตัด timezone แล้ว parse เป็น local
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.parse(isoString.take(19))?.time
        } catch (_: Exception) { null }
    } catch (e: Exception) {
        Log.w("OrderRepository", "Failed to parse ISO 8601 timestamp: $isoString", e)
        null
    }
}
