package com.thaiprompt.smschecker.data.repository

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.thaiprompt.smschecker.BuildConfig
import com.thaiprompt.smschecker.data.api.*
import com.thaiprompt.smschecker.data.db.MatchHistoryDao
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.model.*
import com.thaiprompt.smschecker.security.CryptoManager
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
    private val cryptoManager: CryptoManager,
    private val secureStorage: SecureStorage,
    private val gson: Gson,
    private val bugReportApi: BugReportApiService
) {
    companion object {
        private const val TAG = "OrderRepository"
    }

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
    suspend fun getPendingOrdersList(): List<OrderApproval> {
        return orderApprovalDao.getPendingReviewOrders()
    }

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
                // 🔍 Debug: log แต่ละ order ที่ได้รับจาก server
                for ((idx, order) in orders.withIndex()) {
                    Log.d("OrderRepository", "  order[$idx]: id=${order.id}, status=${order.approval_status}, " +
                        "orderNum=${order.order_details_json?.get("order_number")}, " +
                        "amount=${order.order_details_json?.get("amount")}, " +
                        "notif=${order.notification?.let { "bank=${it.bank},amt=${it.amount}" } ?: "null"}")
                }
                if (orders.isNotEmpty()) {
                    // Smart upsert: ป้องกัน pendingAction (offline queue) หาย
                    // และป้องกัน server เขียนทับ approved status ที่แอพ approve ไปแล้ว
                    var successCount = 0
                    var errorCount = 0
                    var skipCount = 0
                    // 🔍 แยกนับ fortune orders เพื่อ debug
                    val fortuneIds = mutableListOf<Long>()
                    for (remote in orders) {
                        try {
                            val isFortune = remote.id > 10_000_000
                            if (isFortune) fortuneIds.add(remote.id)

                            val localOrder = remote.toLocalEntity(serverId)
                            val existing = orderApprovalDao.getByRemoteId(remote.id, serverId)

                            if (existing != null) {
                                // ถ้า local มี pendingAction ค้างอยู่ → ข้ามไป (offline action ยังไม่ได้ส่ง)
                                if (existing.pendingAction != null) {
                                    skipCount++
                                    Log.d("OrderRepository", "  SKIP order id=${remote.id} (pendingAction=${existing.pendingAction})")
                                    continue
                                }

                                // Server is the source of truth — always sync server status
                                orderApprovalDao.update(localOrder.copy(id = existing.id))
                                Log.d("OrderRepository", "  UPDATE order id=${remote.id}, fortune=$isFortune, status=${remote.approval_status}, orderNum=${localOrder.orderNumber}")
                            } else {
                                orderApprovalDao.insert(localOrder)
                                Log.d("OrderRepository", "  INSERT order id=${remote.id}, fortune=$isFortune, status=${remote.approval_status}, orderNum=${localOrder.orderNumber}, product=${localOrder.productName}")
                            }
                            successCount++
                        } catch (e: Exception) {
                            errorCount++
                            Log.e("OrderRepository", "FAILED upsert order id=${remote.id}, status=${remote.approval_status}, orderNum=${remote.order_details_json?.get("order_number")}, error=${e.message}", e)
                        }
                    }
                    Log.i("OrderRepository", "Upsert summary: $successCount ok, $errorCount failed, $skipCount skipped / ${orders.size} total from ${server.name}")
                    if (fortuneIds.isNotEmpty()) {
                        Log.i("OrderRepository", "  Fortune orders (${fortuneIds.size}): $fortuneIds")
                    }

                    // 🔍 ส่ง debug report ไป xman4289.com เมื่อมี fortune orders
                    if (fortuneIds.isNotEmpty() || errorCount > 0) {
                        try {
                            sendFortuneDebugReport(
                                action = "fetchOrders",
                                serverName = server.name,
                                totalOrders = orders.size,
                                fortuneCount = fortuneIds.size,
                                fortuneIds = fortuneIds,
                                successCount = successCount,
                                errorCount = errorCount,
                                skipCount = skipCount,
                                errorDetails = if (errorCount > 0) "See logcat for details" else null
                            )
                        } catch (e: Exception) {
                            Log.w("OrderRepository", "sendFortuneDebugReport failed: ${e.message}")
                        }
                    }
                }

                // Sync approval_mode from server (bidirectional: web admin → app)
                try {
                    syncApprovalModeFromServer(serverId)
                } catch (e: Exception) {
                    Log.w("OrderRepository", "Failed to sync approval mode from ${server.name}", e)
                }

                true
            } else {
                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                Log.e("OrderRepository", "Failed to fetch orders from ${server.name}: HTTP ${response.code()} - $errorBody")
                // Save descriptive error status for UI to display
                // Parse error body for specific messages (e.g. "Device is inactive")
                val syncStatus = when {
                    response.code() == 403 && errorBody?.contains("inactive", ignoreCase = true) == true ->
                        "failed:device_inactive"
                    response.code() == 403 && errorBody?.contains("mismatch", ignoreCase = true) == true ->
                        "failed:device_mismatch"
                    else -> "failed:${response.code()}"
                }
                try {
                    serverConfigDao.updateSyncStatus(serverId, System.currentTimeMillis(), syncStatus)
                } catch (_: Exception) { }
                false
            }
        }
    }

    suspend fun approveOrder(order: OrderApproval) {
        Log.i(TAG, "approveOrder: START orderId=${order.id}, remoteId=${order.remoteApprovalId}, orderNumber=${order.orderNumber}, serverId=${order.serverId}")

        val server = serverConfigDao.getById(order.serverId)
        if (server == null) {
            Log.e(TAG, "approveOrder: Server not found for serverId=${order.serverId} — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return
        }

        val apiKey = secureStorage.getApiKey(server.id)
        if (apiKey == null) {
            Log.e(TAG, "approveOrder: No API key for server ${server.name} (id=${server.id}) — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return
        }

        val secretKey = secureStorage.getSecretKey(server.id)
        if (secretKey == null) {
            Log.e(TAG, "approveOrder: No secret key for server ${server.name} (id=${server.id}) — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return
        }

        val deviceId = secureStorage.getDeviceId()
        if (deviceId == null) {
            Log.e(TAG, "approveOrder: No device ID — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return
        }

        // ใช้ orderNumber (bill_reference) ถ้ามี, fallback เป็น remoteApprovalId
        val identifier = order.orderNumber ?: order.remoteApprovalId.toString()
        Log.i(TAG, "approveOrder: identifier=$identifier, amount=${order.amount}, bank=${order.bank}, server=${server.name}")

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            sendEncryptedAction(
                server = server,
                apiKey = apiKey,
                secretKey = secretKey,
                deviceId = deviceId,
                action = "approve",
                orderIdentifier = identifier,
                amount = order.amount,
                bank = order.bank,
                reason = null
            )
        }

        if (success) {
            Log.i(TAG, "approveOrder: ✅ SUCCESS for $identifier")
            orderApprovalDao.updateStatus(order.id, ApprovalStatus.MANUALLY_APPROVED, null)
        } else {
            Log.w(TAG, "approveOrder: ❌ FAILED for $identifier — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
        }
    }

    /**
     * Auto-approve order by local ID (used for automatic approval after SMS match)
     * Returns true if successfully approved, false if failed or order not found
     */
    suspend fun approveOrder(orderId: Long): Boolean {
        Log.i(TAG, "approveOrder(id): START orderId=$orderId")

        val order = orderApprovalDao.getOrderById(orderId)
        if (order == null) {
            Log.e(TAG, "approveOrder(id): Order not found in local DB for id=$orderId")
            return false
        }

        // Skip if already approved
        if (order.approvalStatus == ApprovalStatus.AUTO_APPROVED ||
            order.approvalStatus == ApprovalStatus.MANUALLY_APPROVED) {
            Log.i(TAG, "approveOrder(id): Already approved (${order.approvalStatus}), skipping")
            return true
        }

        val server = serverConfigDao.getById(order.serverId)
        if (server == null) {
            Log.e(TAG, "approveOrder(id): Server not found for serverId=${order.serverId}")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return false
        }

        val apiKey = secureStorage.getApiKey(server.id)
        if (apiKey == null) {
            Log.e(TAG, "approveOrder(id): No API key for server ${server.name}")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return false
        }

        val secretKey = secureStorage.getSecretKey(server.id)
        if (secretKey == null) {
            Log.e(TAG, "approveOrder(id): No secret key for server ${server.name}")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return false
        }

        val deviceId = secureStorage.getDeviceId()
        if (deviceId == null) {
            Log.e(TAG, "approveOrder(id): No device ID")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            return false
        }

        // ใช้ orderNumber (bill_reference) ถ้ามี, fallback เป็น remoteApprovalId
        val identifier = order.orderNumber ?: order.remoteApprovalId.toString()
        Log.i(TAG, "approveOrder(id): identifier=$identifier, amount=${order.amount}, bank=${order.bank}, server=${server.name}")

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            sendEncryptedAction(
                server = server,
                apiKey = apiKey,
                secretKey = secretKey,
                deviceId = deviceId,
                action = "approve",
                orderIdentifier = identifier,
                amount = order.amount,
                bank = order.bank,
                reason = null
            )
        }

        return if (success) {
            Log.i(TAG, "approveOrder(id): ✅ SUCCESS for $identifier")
            orderApprovalDao.updateStatus(order.id, ApprovalStatus.AUTO_APPROVED, null)
            true
        } else {
            Log.w(TAG, "approveOrder(id): ❌ FAILED for $identifier — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.APPROVE)
            false
        }
    }

    suspend fun rejectOrder(order: OrderApproval, reason: String = "") {
        Log.i(TAG, "rejectOrder: START orderId=${order.id}, orderNumber=${order.orderNumber}")

        val server = serverConfigDao.getById(order.serverId)
        if (server == null) {
            Log.e(TAG, "rejectOrder: Server not found — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
            return
        }
        val apiKey = secureStorage.getApiKey(server.id)
        if (apiKey == null) {
            Log.e(TAG, "rejectOrder: No API key — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
            return
        }
        val secretKey = secureStorage.getSecretKey(server.id)
        if (secretKey == null) {
            Log.e(TAG, "rejectOrder: No secret key — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
            return
        }
        val deviceId = secureStorage.getDeviceId()
        if (deviceId == null) {
            Log.e(TAG, "rejectOrder: No device ID — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
            return
        }

        val identifier = order.orderNumber ?: order.remoteApprovalId.toString()
        Log.i(TAG, "rejectOrder: identifier=$identifier, server=${server.name}")

        val success = RetryHelper.withRetryBoolean {
            sendEncryptedAction(
                server = server,
                apiKey = apiKey,
                secretKey = secretKey,
                deviceId = deviceId,
                action = "reject",
                orderIdentifier = identifier,
                amount = order.amount,
                bank = order.bank,
                reason = reason
            )
        }

        if (success) {
            Log.i(TAG, "rejectOrder: ✅ SUCCESS for $identifier")
            orderApprovalDao.updateStatus(order.id, ApprovalStatus.REJECTED, null)
        } else {
            Log.w(TAG, "rejectOrder: ❌ FAILED for $identifier — queuing offline")
            orderApprovalDao.updateStatus(order.id, order.approvalStatus, PendingAction.REJECT)
        }
    }

    suspend fun syncOfflineQueue() {
        val pending = orderApprovalDao.getPendingActions()
        val deviceId = secureStorage.getDeviceId() ?: return

        for (order in pending) {
            val server = serverConfigDao.getById(order.serverId) ?: continue
            val apiKey = secureStorage.getApiKey(server.id) ?: continue
            val secretKey = secureStorage.getSecretKey(server.id) ?: continue

            // ใช้ orderNumber (bill_reference) ถ้ามี, fallback เป็น remoteApprovalId
            val identifier = order.orderNumber ?: order.remoteApprovalId.toString()

            // Retry with exponential backoff for unstable network
            val success = RetryHelper.withRetryBoolean {
                when (order.pendingAction) {
                    PendingAction.APPROVE -> {
                        sendEncryptedAction(
                            server = server,
                            apiKey = apiKey,
                            secretKey = secretKey,
                            deviceId = deviceId,
                            action = "approve",
                            orderIdentifier = identifier,
                            amount = order.amount,
                            bank = order.bank,
                            reason = null
                        )
                    }
                    PendingAction.REJECT -> {
                        sendEncryptedAction(
                            server = server,
                            apiKey = apiKey,
                            secretKey = secretKey,
                            deviceId = deviceId,
                            action = "reject",
                            orderIdentifier = identifier,
                            amount = order.amount,
                            bank = order.bank,
                            reason = order.rejectionReason
                        )
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
     * Send encrypted action (approve/reject) to server via notify-action endpoint.
     * Uses AES-256-GCM encryption + HMAC-SHA256 signing (same as notify endpoint).
     *
     * @return true if server accepted the action, false otherwise
     */
    private suspend fun sendEncryptedAction(
        server: ServerConfig,
        apiKey: String,
        secretKey: String,
        deviceId: String,
        action: String,
        orderIdentifier: String,
        amount: Double,
        bank: String?,
        reason: String?
    ): Boolean {
        Log.d(TAG, "sendEncryptedAction: $action for $orderIdentifier on ${server.name} (${server.baseUrl})")

        val nonce = cryptoManager.generateNonce()
        val timestamp = System.currentTimeMillis().toString()

        // Build action payload
        val payload = ActionPayload(
            action = action,
            order_identifier = orderIdentifier,
            amount = amount,
            bank = bank,
            sms_reference = null,
            device_id = deviceId,
            reason = reason,
            nonce = nonce
        )

        val payloadJson = gson.toJson(payload)
        Log.d(TAG, "sendEncryptedAction: payload=$payloadJson")

        // Encrypt payload (AES-256-GCM)
        val encryptedData = try {
            cryptoManager.encrypt(payloadJson, secretKey)
        } catch (e: Exception) {
            Log.e(TAG, "sendEncryptedAction: ENCRYPT FAILED: ${e.message}", e)
            return false
        }

        // Generate HMAC signature: HMAC(encrypted_data + nonce + timestamp)
        val signatureData = "$encryptedData$nonce$timestamp"
        val signature = try {
            cryptoManager.generateHmac(signatureData, secretKey)
        } catch (e: Exception) {
            Log.e(TAG, "sendEncryptedAction: HMAC FAILED: ${e.message}", e)
            return false
        }

        Log.d(TAG, "sendEncryptedAction: encrypted OK, sending to ${server.baseUrl}/api/v1/sms-payment/notify-action")

        // Send to server
        val client = apiClientFactory.getClient(server.baseUrl)
        val response = try {
            client.notifyAction(
                apiKey = apiKey,
                signature = signature,
                nonce = nonce,
                timestamp = timestamp,
                deviceId = deviceId,
                body = EncryptedPayload(data = encryptedData)
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendEncryptedAction: NETWORK ERROR: ${e.javaClass.simpleName}: ${e.message}", e)
            return false
        }

        Log.i(TAG, "sendEncryptedAction: HTTP ${response.code()}, success=${response.body()?.success}")

        return if (response.isSuccessful && response.body()?.success == true) {
            Log.i(TAG, "✅ Encrypted $action sent for $orderIdentifier on ${server.name}")

            // Update local order with server response data if available
            val serverOrder = response.body()?.data?.order
            if (serverOrder != null) {
                try {
                    val localOrder = serverOrder.toLocalEntity(server.id)
                    val existing = orderApprovalDao.getByRemoteId(serverOrder.id, server.id)
                    if (existing != null) {
                        orderApprovalDao.update(localOrder.copy(id = existing.id))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update local order from response", e)
                }
            }
            true
        } else if (response.code() == 422) {
            // Server says order already in target status → treat as success
            Log.i(TAG, "Order $orderIdentifier already ${action}d on server (422)")
            true
        } else if (response.code() == 409) {
            // Duplicate nonce → already processed → treat as success
            Log.i(TAG, "Duplicate nonce for $orderIdentifier (409) - already processed")
            true
        } else {
            val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
            Log.e(TAG, "❌ Encrypted $action failed for $orderIdentifier: HTTP ${response.code()} - $errorBody")
            false
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
                var successCount = 0
                var errorCount = 0
                var skipCount = 0
                val fortuneIds = mutableListOf<Long>()
                for (remote in orders) {
                    try {
                        val isFortune = remote.id > 10_000_000
                        if (isFortune) fortuneIds.add(remote.id)

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
                            // Local pending action wins (offline action ยังไม่ได้ส่ง)
                            if (existing.pendingAction != null) {
                                skipCount++
                                continue
                            }

                            // Server is the source of truth — always accept server status
                        }

                        val local = remote.toLocalEntity(serverId)
                        if (existing != null) {
                            orderApprovalDao.update(local.copy(id = existing.id))
                            Log.d("OrderRepository", "  SYNC-UPDATE id=${remote.id}, fortune=$isFortune, status=${remote.approval_status}")
                        } else {
                            orderApprovalDao.insert(local)
                            Log.d("OrderRepository", "  SYNC-INSERT id=${remote.id}, fortune=$isFortune, status=${remote.approval_status}, product=${local.productName}")
                        }
                        successCount++
                    } catch (e: Exception) {
                        errorCount++
                        Log.e("OrderRepository", "SYNC-FAILED id=${remote.id}, status=${remote.approval_status}, error=${e.message}", e)
                    }
                }
                Log.i("OrderRepository", "Sync summary: $successCount ok, $errorCount failed, $skipCount skipped / ${orders.size} total from ${server.name}")
                if (fortuneIds.isNotEmpty()) {
                    Log.i("OrderRepository", "  Sync fortune orders (${fortuneIds.size}): $fortuneIds")
                    // ส่ง debug report ไป xman4289.com
                    try {
                        sendFortuneDebugReport(
                            action = "syncOrders",
                            serverName = server.name,
                            totalOrders = orders.size,
                            fortuneCount = fortuneIds.size,
                            fortuneIds = fortuneIds,
                            successCount = successCount,
                            errorCount = errorCount,
                            skipCount = skipCount
                        )
                    } catch (e: Exception) {
                        Log.w("OrderRepository", "sendFortuneDebugReport (sync) failed: ${e.message}")
                    }
                }
                true
            } else {
                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                Log.e("OrderRepository", "Failed to sync from ${server.name}: HTTP ${response.code()} - $errorBody")
                // Save descriptive error status for UI to display
                val syncStatus = when {
                    response.code() == 403 && errorBody?.contains("inactive", ignoreCase = true) == true ->
                        "failed:device_inactive"
                    response.code() == 403 && errorBody?.contains("mismatch", ignoreCase = true) == true ->
                        "failed:device_mismatch"
                    else -> "failed:${response.code()}"
                }
                try {
                    serverConfigDao.updateSyncStatus(serverId, System.currentTimeMillis(), syncStatus)
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
     * Get count of active servers and servers with API keys.
     * Used for debug report.
     */
    suspend fun getActiveServerCount(): Pair<Int, Int> {
        val activeServers = serverConfigDao.getActiveConfigs()
        var withKeys = 0
        for (server in activeServers) {
            if (secureStorage.getApiKey(server.id) != null) withKeys++
        }
        return Pair(activeServers.size, withKeys)
    }

    /**
     * ส่ง FCM token ไปยังเซิร์ฟเวอร์ทุกตัวเพื่อรับ push notifications
     * ใช้ sequential loop แทน ParallelSyncHelper เพื่อ debug ได้ง่ายกว่า
     * @return true ถ้าส่งไป **ทุก** เซิร์ฟเวอร์สำเร็จ, false ถ้ายังมีที่ยังส่งไม่สำเร็จ
     */
    suspend fun registerFcmToken(fcmToken: String): Boolean {
        Log.i("OrderRepository", "registerFcmToken: START, tokenLength=${fcmToken.length}, tokenPrefix=${fcmToken.take(20)}")

        // Step 1: Get active servers
        val activeServers = try {
            serverConfigDao.getActiveConfigs()
        } catch (e: Exception) {
            Log.e("OrderRepository", "registerFcmToken: FAILED getActiveConfigs: ${e.javaClass.simpleName}: ${e.message}", e)
            return false
        }
        Log.i("OrderRepository", "registerFcmToken: activeServers=${activeServers.size} [${activeServers.map { "${it.id}:${it.name}" }}]")

        // Step 2: Get device ID
        val deviceId = try {
            secureStorage.getDeviceId()
        } catch (e: Exception) {
            Log.e("OrderRepository", "registerFcmToken: FAILED getDeviceId: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
        if (deviceId == null) {
            Log.w("OrderRepository", "registerFcmToken: No device ID - ABORTING")
            return false
        }
        Log.i("OrderRepository", "registerFcmToken: deviceId=$deviceId")

        if (activeServers.isEmpty()) {
            Log.w("OrderRepository", "registerFcmToken: No active servers - ABORTING")
            return false
        }

        // Step 3: Send to each server SEQUENTIALLY (easier to debug)
        var sentCount = 0
        for (server in activeServers) {
            Log.i("OrderRepository", "registerFcmToken: Processing server id=${server.id}, name=${server.name}, url=${server.baseUrl}")

            val apiKey = try {
                secureStorage.getApiKey(server.id)
            } catch (e: Exception) {
                Log.e("OrderRepository", "registerFcmToken: FAILED getApiKey for server ${server.id}: ${e.javaClass.simpleName}: ${e.message}", e)
                null
            }

            if (apiKey == null) {
                Log.w("OrderRepository", "registerFcmToken: No API key for server ${server.id} - skipping")
                continue
            }
            Log.i("OrderRepository", "registerFcmToken: apiKey found, length=${apiKey.length}, prefix=${apiKey.take(8)}")

            try {
                val client = apiClientFactory.getClient(server.baseUrl)
                Log.i("OrderRepository", "registerFcmToken: Client created for ${server.baseUrl}")

                val body = DeviceRegistration(
                    device_id = deviceId,
                    device_name = android.os.Build.MODEL,
                    platform = "android",
                    app_version = com.thaiprompt.smschecker.BuildConfig.VERSION_NAME,
                    fcm_token = fcmToken
                )
                Log.i("OrderRepository", "registerFcmToken: Body created: device_id=$deviceId, device_name=${android.os.Build.MODEL}, app_version=${com.thaiprompt.smschecker.BuildConfig.VERSION_NAME}")

                Log.i("OrderRepository", "registerFcmToken: >>> CALLING registerDevice API...")
                val response = client.registerDevice(apiKey = apiKey, body = body)
                Log.i("OrderRepository", "registerFcmToken: <<< API response: code=${response.code()}, isSuccessful=${response.isSuccessful}")

                if (response.isSuccessful) {
                    sentCount++
                    val responseBody = try { response.body()?.toString() } catch (_: Exception) { "N/A" }
                    Log.i("OrderRepository", "registerFcmToken: ✅ SUCCESS for ${server.name}! Response: $responseBody")
                } else {
                    val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { "N/A" }
                    Log.w("OrderRepository", "registerFcmToken: ❌ FAILED for ${server.name}: HTTP ${response.code()}, error=$errorBody")
                }
            } catch (e: Exception) {
                Log.e("OrderRepository", "registerFcmToken: 💥 EXCEPTION for server ${server.name}: ${e.javaClass.simpleName}: ${e.message}", e)
                // ส่ง stack trace เต็มเพื่อ debug
                Log.e("OrderRepository", "registerFcmToken: Stack trace:", e)
            }
        }

        Log.i("OrderRepository", "registerFcmToken: END. Sent to $sentCount/${activeServers.size} servers")
        // Return true only if ALL servers succeeded
        // This ensures fcm_token_needs_sync flag stays set until every server gets the token
        return sentCount == activeServers.size
    }

    /**
     * Send debug report to server for remote diagnostics.
     * Temporary - for debugging FCM token registration issues.
     */
    suspend fun sendDebugReport(report: DebugReportBody) {
        try {
            val activeServers = serverConfigDao.getActiveConfigs()
            if (activeServers.isEmpty()) {
                Log.w("OrderRepository", "sendDebugReport: No active servers")
                return
            }
            for (server in activeServers) {
                val apiKey = secureStorage.getApiKey(server.id) ?: continue
                try {
                    val client = apiClientFactory.getClient(server.baseUrl)
                    val response = client.debugReport(apiKey = apiKey, body = report)
                    Log.i("OrderRepository", "sendDebugReport: ${server.name} → HTTP ${response.code()}")
                } catch (e: Exception) {
                    Log.e("OrderRepository", "sendDebugReport: ${server.name} failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("OrderRepository", "sendDebugReport: FAILED: ${e.message}")
        }
    }

    /**
     * ส่ง fortune order debug report ไป xman4289.com/admin/bug-reports
     * เพื่อ remote debugging ว่า fortune orders ถูก sync สำเร็จหรือไม่
     */
    private suspend fun sendFortuneDebugReport(
        action: String,
        serverName: String,
        totalOrders: Int,
        fortuneCount: Int,
        fortuneIds: List<Long>,
        successCount: Int,
        errorCount: Int,
        skipCount: Int,
        errorDetails: String? = null
    ) {
        try {
            val deviceId = secureStorage.getDeviceId() ?: "unknown"
            val report = BugReportRequest(
                productName = "smschecker",
                productVersion = BuildConfig.VERSION_NAME,
                reportType = "bug",
                title = "Fortune Order Sync: ${fortuneCount} fortune, ${errorCount} errors",
                description = buildString {
                    appendLine("**Action:** $action")
                    appendLine("**Server:** $serverName")
                    appendLine()
                    appendLine("**Orders Summary:**")
                    appendLine("- Total from server: $totalOrders")
                    appendLine("- Fortune orders: $fortuneCount")
                    appendLine("- Success: $successCount")
                    appendLine("- Failed: $errorCount")
                    appendLine("- Skipped (pendingAction): $skipCount")
                    appendLine()
                    if (fortuneIds.isNotEmpty()) {
                        appendLine("**Fortune IDs (with 10M offset):** ${fortuneIds.joinToString(", ")}")
                        appendLine("**Fortune IDs (real):** ${fortuneIds.map { it - 10_000_000 }.joinToString(", ")}")
                    }
                    if (errorDetails != null) {
                        appendLine()
                        appendLine("**Error Details:** $errorDetails")
                    }
                },
                metadata = mapOf(
                    "action" to action,
                    "server_name" to serverName,
                    "total_orders" to totalOrders,
                    "fortune_count" to fortuneCount,
                    "fortune_ids" to fortuneIds.joinToString(","),
                    "success_count" to successCount,
                    "error_count" to errorCount,
                    "skip_count" to skipCount,
                    "timestamp" to System.currentTimeMillis()
                ),
                deviceId = deviceId,
                priority = if (errorCount > 0) "high" else "low",
                severity = if (errorCount > 0) "major" else "minor",
                osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                appVersion = BuildConfig.VERSION_NAME,
                additionalInfo = mapOf(
                    "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "build_number" to BuildConfig.VERSION_CODE
                )
            )

            val response = bugReportApi.submitReport(report)
            Log.i(TAG, "sendFortuneDebugReport: HTTP ${response.code()} → ${response.body()?.message}")
        } catch (e: Exception) {
            Log.w(TAG, "sendFortuneDebugReport failed: ${e.message}")
        }
    }

    /**
     * Update approval mode for a SPECIFIC server.
     * 1. Save to local DB (per-server)
     * 2. Push to server via API
     */
    suspend fun updateApprovalMode(serverId: Long, mode: ApprovalMode) {
        val deviceId = secureStorage.getDeviceId() ?: return

        // Update local DB per-server
        serverConfigDao.updateApprovalMode(serverId, mode.apiValue)

        // Also update legacy global setting for backward compatibility
        secureStorage.setApprovalMode(mode.apiValue)

        // Push to server
        val server = serverConfigDao.getById(serverId) ?: return
        val apiKey = secureStorage.getApiKey(serverId) ?: return
        try {
            val client = apiClientFactory.getClient(server.baseUrl)
            client.updateDeviceSettings(apiKey, deviceId, UpdateSettingsBody(mode.apiValue))
            Log.i(TAG, "updateApprovalMode: ${mode.apiValue} pushed to ${server.name}")
        } catch (e: Exception) {
            Log.w(TAG, "updateApprovalMode: Failed to push to ${server.name}: ${e.message}")
        }
    }

    /**
     * Legacy: Update approval mode for ALL servers (backward compat).
     */
    suspend fun updateApprovalMode(mode: ApprovalMode) {
        val activeServers = serverConfigDao.getActiveConfigs()
        val deviceId = secureStorage.getDeviceId() ?: return

        secureStorage.setApprovalMode(mode.apiValue)

        for (server in activeServers) {
            serverConfigDao.updateApprovalMode(server.id, mode.apiValue)
            val apiKey = secureStorage.getApiKey(server.id) ?: continue
            try {
                val client = apiClientFactory.getClient(server.baseUrl)
                client.updateDeviceSettings(apiKey, deviceId, UpdateSettingsBody(mode.apiValue))
            } catch (e: Exception) {
                // Best effort
            }
        }
    }

    /**
     * Sync approval_mode FROM server for a specific server.
     * Called during fetch/pull to keep local DB in sync with server.
     */
    suspend fun syncApprovalModeFromServer(serverId: Long) {
        val deviceId = secureStorage.getDeviceId() ?: return
        val apiKey = secureStorage.getApiKey(serverId) ?: return
        val server = serverConfigDao.getById(serverId) ?: return

        try {
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.getDeviceSettings(apiKey, deviceId)
            if (response.isSuccessful) {
                val serverMode = response.body()?.data?.approval_mode ?: "auto"
                val localMode = server.approvalMode
                if (serverMode != localMode) {
                    Log.i(TAG, "syncApprovalMode: ${server.name} changed from $localMode → $serverMode")
                    serverConfigDao.updateApprovalMode(serverId, serverMode)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncApprovalMode: Failed for ${server.name}: ${e.message}")
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
        deviceId = device_id,
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
