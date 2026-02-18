package com.thaiprompt.smschecker.data.repository

import android.util.Log
import com.google.gson.Gson
import com.thaiprompt.smschecker.data.api.*
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.db.SyncLogDao
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.*
import com.thaiprompt.smschecker.security.CryptoManager
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.util.ParallelSyncHelper
import com.thaiprompt.smschecker.util.RetryHelper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val serverConfigDao: ServerConfigDao,
    private val syncLogDao: SyncLogDao,
    private val orderApprovalDao: OrderApprovalDao,
    private val apiClientFactory: ApiClientFactory,
    private val cryptoManager: CryptoManager,
    private val secureStorage: SecureStorage,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "TransactionRepository"
    }

    fun getAllTransactions(): Flow<List<BankTransaction>> = transactionDao.getAllTransactions()

    fun getTransactionsByType(type: TransactionType): Flow<List<BankTransaction>> =
        transactionDao.getTransactionsByType(type)

    fun getUnsyncedCount(): Flow<Int> = transactionDao.getUnsyncedCount()

    fun getTotalCredit(since: Long): Flow<Double> =
        transactionDao.getTotalAmountByType(TransactionType.CREDIT, since)

    fun getTotalDebit(since: Long): Flow<Double> =
        transactionDao.getTotalAmountByType(TransactionType.DEBIT, since)

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<BankTransaction>> =
        transactionDao.getTransactionsByDateRange(startTime, endTime)

    suspend fun saveTransaction(transaction: BankTransaction): Long {
        return transactionDao.insert(transaction)
    }

    /**
     * Sync a transaction to all active servers in PARALLEL.
     * Returns true if at least one server confirmed the transaction.
     *
     * CRITICAL: This is the main entry point for syncing SMS/notification transactions.
     * Using parallel sync reduces latency from O(n * timeout) to O(timeout).
     *
     * FIX: Only sends to servers that haven't received this transaction yet.
     * Previously, isSynced was marked true after first server success,
     * causing other servers to never receive the transaction.
     */
    suspend fun syncTransaction(transaction: BankTransaction): Boolean {
        val activeServers = serverConfigDao.getActiveConfigs()
        if (activeServers.isEmpty()) return false

        val deviceId = secureStorage.getDeviceId() ?: return false

        // FIX: Filter out servers that already received this transaction successfully
        val alreadySyncedServerIds = try {
            syncLogDao.getSuccessfulServerIds(transaction.id)
        } catch (e: Exception) {
            emptyList()
        }

        val serversToSync = activeServers.filter { it.id !in alreadySyncedServerIds }
        if (serversToSync.isEmpty()) {
            // All active servers already have this transaction — mark as fully synced
            if (!transaction.isSynced) {
                transactionDao.markAsSynced(transaction.id, activeServers.first().id, "ALL_SYNCED")
            }
            return true
        }

        // Prepare server list for parallel execution
        val serverList = serversToSync.map { it.id to it.name }

        // Execute sync to all remaining servers in parallel
        val results = ParallelSyncHelper.executeParallelBoolean(
            servers = serverList,
            maxConcurrency = 5,
            timeoutMs = 10_000L  // 10s per server (reduced for real-time)
        ) { serverId ->
            val server = serverConfigDao.getById(serverId) ?: return@executeParallelBoolean false
            syncToServer(transaction, server, deviceId)
        }

        // Update status for each server
        for (result in results.results) {
            try {
                if (result.success) {
                    serverConfigDao.updateSyncStatus(result.serverId, System.currentTimeMillis(), "success")
                } else {
                    serverConfigDao.updateSyncStatus(result.serverId, System.currentTimeMillis(), "failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update status for ${result.serverName}", e)
            }
        }

        // FIX: Only mark transaction as globally synced when ALL active servers have it
        val newSyncedIds = alreadySyncedServerIds + results.results.filter { it.success }.map { it.serverId }
        val allServersSynced = activeServers.all { it.id in newSyncedIds }

        if (allServersSynced) {
            // All servers received the transaction — mark as fully synced
            transactionDao.markAsSynced(transaction.id, activeServers.first().id, "ALL_SYNCED")
            Log.i(TAG, "Transaction ${transaction.id} fully synced to ALL ${activeServers.size} servers")
        } else if (results.anySucceeded) {
            // Partial success — keep isSynced = false so retry picks it up
            val syncedNames = results.results.filter { it.success }.joinToString { it.serverName }
            val failedNames = results.results.filter { !it.success }.joinToString { it.serverName }
            Log.w(TAG, "Transaction ${transaction.id} partial sync: OK=[$syncedNames] FAILED=[$failedNames]")
        }

        Log.d(TAG, "Parallel sync: ${results.successCount}/${results.results.size} servers, " +
                "total synced: ${newSyncedIds.size}/${activeServers.size} in ${results.totalDurationMs}ms")
        return results.anySucceeded
    }

    /**
     * Sync all unsynced transactions to all servers.
     * Picks up transactions that failed to sync to some servers.
     */
    suspend fun syncAllUnsynced(): Int {
        val unsynced = try {
            transactionDao.getUnsyncedTransactions()
        } catch (e: Exception) {
            return 0
        }
        var syncedCount = 0

        for (transaction in unsynced) {
            try {
                if (syncTransaction(transaction)) {
                    syncedCount++
                }
            } catch (e: Exception) { }
        }

        return syncedCount
    }

    private suspend fun syncToServer(
        transaction: BankTransaction,
        server: ServerConfig,
        deviceId: String
    ): Boolean {
        val apiKey = secureStorage.getApiKey(server.id) ?: return false
        val secretKey = secureStorage.getSecretKey(server.id) ?: return false

        val syncLog = SyncLog(
            transactionId = transaction.id,
            serverId = server.id,
            serverName = server.name,
            status = SyncStatus.SENDING
        )
        val logId = syncLogDao.insert(syncLog)

        // Retry with exponential backoff for unstable network
        val success = RetryHelper.withRetryBoolean {
            val nonce = cryptoManager.generateNonce()
            val timestamp = System.currentTimeMillis().toString()

            // Build payload
            val payload = TransactionPayload(
                bank = transaction.bank,
                type = if (transaction.type == TransactionType.CREDIT) "credit" else "debit",
                amount = transaction.amount,
                account_number = transaction.accountNumber,
                sender_or_receiver = transaction.senderOrReceiver,
                reference_number = transaction.referenceNumber,
                sms_timestamp = transaction.timestamp,
                device_id = deviceId,
                nonce = nonce
            )

            val payloadJson = gson.toJson(payload)

            // Encrypt payload
            val encryptedData = cryptoManager.encrypt(payloadJson, secretKey)

            // Generate HMAC signature: HMAC(encrypted_data + nonce + timestamp)
            val signatureData = "$encryptedData$nonce$timestamp"
            val signature = cryptoManager.generateHmac(signatureData, secretKey)

            // Send to server
            val client = apiClientFactory.getClient(server.baseUrl)
            val response = client.notifyTransaction(
                apiKey = apiKey,
                signature = signature,
                nonce = nonce,
                timestamp = timestamp,
                deviceId = deviceId,
                body = EncryptedPayload(data = encryptedData)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                // Process matched order from server response
                val responseData = response.body()?.data
                // ✅ เช็คทั้ง matched (ecommerce) และ fortune_reading (ดูดวง)
                val matched = responseData?.get("matched") as? Boolean ?: false
                val fortuneReading = responseData?.get("fortune_reading") as? Boolean ?: false
                val anyMatched = matched || fortuneReading
                if (anyMatched) {
                    Log.i(TAG, "syncToServer: Server matched payment! matched=$matched fortuneReading=$fortuneReading")
                    try {
                        // ✅ ลองอ่านจาก "order" ก่อน แล้ว fallback เป็น "matched_order"
                        // เซิร์ฟเวอร์ส่งทั้ง 2 key เพื่อ backward compatibility
                        @Suppress("UNCHECKED_CAST")
                        val orderMap = (responseData?.get("order") as? Map<String, Any?>)
                            ?: (responseData?.get("matched_order") as? Map<String, Any?>)
                        if (orderMap != null) {
                            val orderJson = gson.toJson(orderMap)
                            val remoteOrder = gson.fromJson(orderJson, RemoteOrderApproval::class.java)
                            if (remoteOrder != null) {
                                val localOrder = remoteOrder.toLocalEntity(server.id)
                                val existing = orderApprovalDao.getByRemoteId(remoteOrder.id, server.id)
                                if (existing != null) {
                                    orderApprovalDao.update(localOrder.copy(id = existing.id))
                                    Log.i(TAG, "syncToServer: Updated local order ${remoteOrder.id} to status=${remoteOrder.approval_status}")
                                } else {
                                    val insertedId = orderApprovalDao.insert(localOrder)
                                    Log.i(TAG, "syncToServer: Inserted matched order ${remoteOrder.id} as local id=$insertedId")
                                }
                            }
                        } else {
                            Log.w(TAG, "syncToServer: matched=true but no order data in response. Keys: ${responseData?.keys}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "syncToServer: Failed to process matched order from response", e)
                    }
                }
                true
            } else {
                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                Log.w(TAG, "syncToServer: Failed HTTP ${response.code()} - $errorBody")
                false
            }
        }

        return if (success) {
            syncLogDao.update(syncLog.copy(
                id = logId,
                status = SyncStatus.SUCCESS,
                httpStatusCode = 200,
                responseBody = "Success",
                respondedAt = System.currentTimeMillis()
            ))
            true
        } else {
            syncLogDao.update(syncLog.copy(
                id = logId,
                status = SyncStatus.FAILED,
                httpStatusCode = 0,
                responseBody = "Failed after retries",
                respondedAt = System.currentTimeMillis()
            ))
            false
        }
    }

    // Server config management
    fun getAllServerConfigs(): Flow<List<ServerConfig>> = serverConfigDao.getAllConfigs()

    /**
     * Save server config. Returns the server ID.
     * Throws IllegalStateException if a server with the same base URL already exists.
     */
    suspend fun saveServerConfig(
        name: String,
        baseUrl: String,
        apiKey: String,
        secretKey: String,
        isDefault: Boolean = false,
        syncInterval: Int = 300  // Default 5 minutes (FCM push is primary)
    ): Long {
        // ป้องกัน URL ซ้ำ — normalize URL ก่อนเช็ค
        val normalizedUrl = baseUrl.trimEnd('/')
        val existing = serverConfigDao.findByBaseUrl(normalizedUrl)
        if (existing != null) {
            throw IllegalStateException("Server with URL '${normalizedUrl}' already exists (${existing.name})")
        }

        if (isDefault) {
            serverConfigDao.clearDefaultFlag()
        }

        val config = ServerConfig(
            name = name,
            baseUrl = normalizedUrl,
            apiKey = "", // Stored separately in SecureStorage
            secretKey = "", // Stored separately in SecureStorage
            isDefault = isDefault,
            syncInterval = syncInterval.coerceIn(30, 600)  // Enforce 30s-10min range
        )

        val id = serverConfigDao.insert(config)

        // Store keys securely
        secureStorage.saveApiKey(id, apiKey)
        secureStorage.saveSecretKey(id, secretKey)

        return id
    }

    suspend fun deleteServerConfig(config: ServerConfig) {
        secureStorage.deleteServerKeys(config.id)
        serverConfigDao.delete(config)
        apiClientFactory.clearCache()
    }

    suspend fun toggleServerActive(config: ServerConfig) {
        serverConfigDao.update(config.copy(isActive = !config.isActive))
    }

    // Sync logs
    fun getRecentSyncLogs(): Flow<List<SyncLog>> = syncLogDao.getRecentLogs()

    // Deduplication: check if a similar transaction was saved within a time window
    suspend fun findDuplicate(
        bank: String,
        amount: String,
        type: TransactionType,
        timestamp: Long,
        windowMs: Long = 60_000L
    ): Boolean {
        return transactionDao.findDuplicate(bank, amount, type, timestamp, windowMs) != null
    }

    // Get recent transactions from bank app notifications
    suspend fun getRecentNotificationTransactions(): List<BankTransaction> {
        return transactionDao.getRecentNotificationTransactions()
    }
}
