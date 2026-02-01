package com.thaiprompt.smschecker.data.repository

import com.google.gson.Gson
import com.thaiprompt.smschecker.data.api.*
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.db.SyncLogDao
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.*
import com.thaiprompt.smschecker.security.CryptoManager
import com.thaiprompt.smschecker.security.SecureStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val serverConfigDao: ServerConfigDao,
    private val syncLogDao: SyncLogDao,
    private val apiClientFactory: ApiClientFactory,
    private val cryptoManager: CryptoManager,
    private val secureStorage: SecureStorage,
    private val gson: Gson
) {

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
     * Sync a transaction to all active servers.
     * Returns true if at least one server confirmed the transaction.
     */
    suspend fun syncTransaction(transaction: BankTransaction): Boolean {
        val activeServers = serverConfigDao.getActiveConfigs()
        if (activeServers.isEmpty()) return false

        val deviceId = secureStorage.getDeviceId() ?: return false
        var anySynced = false

        for (server in activeServers) {
            val result = syncToServer(transaction, server, deviceId)
            if (result) {
                anySynced = true
                transactionDao.markAsSynced(transaction.id, server.id, "OK")
                serverConfigDao.updateSyncStatus(server.id, System.currentTimeMillis(), "success")
            } else {
                serverConfigDao.updateSyncStatus(server.id, System.currentTimeMillis(), "failed")
            }
        }

        return anySynced
    }

    /**
     * Sync all unsynced transactions.
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

        return try {
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
                syncLogDao.update(syncLog.copy(
                    id = logId,
                    status = SyncStatus.SUCCESS,
                    httpStatusCode = response.code(),
                    responseBody = gson.toJson(response.body()),
                    respondedAt = System.currentTimeMillis()
                ))
                true
            } else {
                syncLogDao.update(syncLog.copy(
                    id = logId,
                    status = SyncStatus.FAILED,
                    httpStatusCode = response.code(),
                    responseBody = response.errorBody()?.string(),
                    respondedAt = System.currentTimeMillis()
                ))
                false
            }
        } catch (e: Exception) {
            syncLogDao.update(syncLog.copy(
                id = logId,
                status = SyncStatus.FAILED,
                errorMessage = e.message,
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
        isDefault: Boolean = false
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
            isDefault = isDefault
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
