package com.thaiprompt.smschecker.data.repository

import android.util.Log
import com.google.gson.Gson
import com.thaiprompt.smschecker.data.api.*
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.db.SyncLogDao
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.*
import com.thaiprompt.smschecker.domain.attribution.AttributionEvent
import com.thaiprompt.smschecker.domain.attribution.SiteAttribution
import com.thaiprompt.smschecker.domain.attribution.SiteAttributionMerger
import com.thaiprompt.smschecker.domain.attribution.SiteNames
import com.thaiprompt.smschecker.security.CryptoManager
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.service.SiteConflictNotifier
import com.thaiprompt.smschecker.util.ParallelSyncHelper
import com.thaiprompt.smschecker.util.RetryHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
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
    private val gson: Gson,
    private val conflictNotifier: SiteConflictNotifier
) {
    /** คำตอบ /notify ของเซิร์ฟ 1 ตัว ในมุม "ยอดนี้เป็นของเว็บไหน" */
    private data class NotifyAttribution(
        val matched: Boolean,
        val siteName: String,
        val externalSite: String?
    )
    companion object {
        private const val TAG = "TransactionRepository"
        // cross-source (SMS ↔ notification ของแอปธนาคาร) dedup window — กว้างกว่า same-source (60s)
        // เพราะ 2 ช่องทางของ payment เดียวกันมา timestamp คนละ clock (SMSC time vs app postTime)
        // 3 นาที ครอบคลุม SMS delay ตอน Doze; เช็คเฉพาะข้าม source จึงไม่กระทบยอดจริงคนละรายการ
        private const val CROSS_SOURCE_DEDUP_WINDOW_MS = 180_000L // 3 นาที
    }

    /**
     * Serializes the dedup-check-then-insert critical section. Without this, two coroutines
     * (e.g. SMS arrives at the same millisecond as a bank-app push-notification) can both
     * see "no duplicate" and both insert, bypassing app-level dedup entirely.
     *
     * App-wide Mutex (not per-instance) because TransactionRepository is @Singleton.
     */
    private val dedupMutex = Mutex()

    /**
     * Atomic "check duplicate, then insert" — call this instead of findDuplicate + saveTransaction
     * to guarantee dedup under concurrent delivery.
     *
     * @return the inserted id, or null if a duplicate was already present.
     */
    suspend fun insertIfNotDuplicate(
        transaction: BankTransaction,
        dedupWindowMs: Long = 60_000L
    ): Long? = dedupMutex.withLock {
        // 1) same-payment re-delivery (any source) ใน window แคบ — กัน SMS resend / rescan / SMS+notif ใกล้กัน
        val dup = transactionDao.findDuplicate(
            bank = transaction.bank,
            amount = transaction.amount,
            type = transaction.type,
            timestamp = transaction.timestamp,
            windowMs = dedupWindowMs
        )
        if (dup != null) return@withLock null

        // 2) 🔁 (2026-06-04) cross-source dedup: payment เดียวกันที่มาทั้ง SMS และ notification ของแอปธนาคาร
        //    บนเครื่องเดียวกัน — timestamp มาคนละ clock (SMSC time vs app postTime) อาจห่างกันเกิน window แคบ
        //    เช็คเฉพาะ "ข้าม source" (sourceType ต่างกัน) ด้วย window กว้างขึ้น → ยอดจริงคนละรายการที่เป็น
        //    source เดียวกัน (เช่น ลูกค้า 2 คนยอดเท่ากันผ่าน SMS ทั้งคู่) ยังถูกบันทึกครบ ไม่โดนตัดทิ้ง
        val crossDup = transactionDao.findCrossSourceDuplicate(
            bank = transaction.bank,
            amount = transaction.amount,
            type = transaction.type,
            currentSource = transaction.sourceType,
            timestamp = transaction.timestamp,
            windowMs = CROSS_SOURCE_DEDUP_WINDOW_MS
        )
        if (crossDup != null) {
            Log.d(TAG, "Cross-source dup (SMS↔notification) skipped: ${transaction.bank} ${transaction.amount} (${transaction.sourceType} vs ${crossDup.sourceType})")
            return@withLock null
        }

        transactionDao.insert(transaction)
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

    suspend fun getTransaction(id: Long): BankTransaction? = transactionDao.getById(id)

    // =====================================================================
    // 🌐 (2026-09-15) Multi-site attribution — CONTRACT §E
    // =====================================================================

    /** serialize read-merge-write ของ attribution — /orders/match, /notify, reconciler อาจมาพร้อมกัน */
    private val attributionMutex = Mutex()

    /**
     * รวมหลักฐานใหม่เข้ากับสถานะ "ยอดนี้เป็นของเว็บไหน" ของ transaction (กติกาใน SiteAttributionMerger)
     * ถ้าผลคือ conflict ใหม่ (หรือรายชื่อเว็บที่ชนเปลี่ยน) → เด้งแจ้งเตือนทันที
     * ไม่ throw — การบันทึกเว็บพลาดต้องไม่ทำให้การประมวลผลเงินเข้าล้ม
     */
    suspend fun recordAttribution(transactionId: Long, event: AttributionEvent): SiteAttribution? {
        if (transactionId <= 0) return null
        return try {
            attributionMutex.withLock {
                val tx = transactionDao.getById(transactionId) ?: return@withLock null
                val before = SiteAttribution.of(tx)
                // เครื่องที่ผูกเซิร์ฟเดียว: "match ที่เซิร์ฟตัวเอง" ไม่มีอะไรต้องแยกเว็บ → ไม่บันทึก
                //   เพื่อให้แถว/เสียงประกาศ/แจ้งเตือนของผู้ใช้เซิร์ฟเดียวเหมือนเดิมทุกอย่าง
                //   (hint/conflict ยังบันทึกเสมอ — เป็นเรื่องหลายเว็บโดยธรรมชาติ)
                if (event is AttributionEvent.Matched && !isMultiSiteDevice()) return@withLock before
                val after = SiteAttributionMerger.merge(before, event)
                if (after != before) {
                    transactionDao.updateAttribution(
                        id = transactionId,
                        serverId = after.serverId,
                        siteName = after.siteName,
                        conflict = after.conflict,
                        conflictSites = after.conflictSitesColumn(),
                        source = after.source
                    )
                    if (after.conflict) {
                        Log.w(TAG, "⚠️ MULTI-SITE CONFLICT tx=$transactionId ${tx.bank} ${tx.amount} sites=${after.conflictSites} (event=$event)")
                    } else {
                        Log.i(TAG, "🌐 tx=$transactionId attributed to '${after.siteName}' (server=${after.serverId}, via=${after.source})")
                    }
                    if (before.source == SiteAttribution.SOURCE_HINT && !after.conflict && after.serverId != before.serverId) {
                        Log.w(TAG, "🌐 tx=$transactionId hint said '${before.siteName}' but server ${after.serverId} ('${after.siteName}') matched it")
                    }
                }
                if (after.conflict && after.conflictSites != before.conflictSites) {
                    conflictNotifier.notifyConflict(
                        transaction = tx,
                        sites = after.conflictSites,
                        alreadyApprovedAt = (event as? AttributionEvent.Conflict)?.alreadyApprovedAt ?: emptyList()
                    )
                }
                after
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "recordAttribution failed tx=$transactionId event=$event", e)
            null
        }
    }

    /** ผูกเซิร์ฟที่เปิดใช้งานมากกว่า 1 ตัว (อ่านไม่ได้ = ถือว่าใช่ — บันทึกไว้ก่อนปลอดภัยกว่า) */
    private suspend fun isMultiSiteDevice(): Boolean =
        try { serverConfigDao.getActiveConfigs().size > 1 } catch (_: Exception) { true }

    /**
     * บันทึกว่ายอดนี้เป็นของเว็บของบิล [order] (reconciler/smart-auto อนุมัติบิลให้ยอดนี้แล้ว)
     * ชื่อเว็บ: server_name → website_name → ชื่อเซิร์ฟในเครื่อง
     */
    suspend fun recordOrderAttribution(
        transactionId: Long,
        order: OrderApproval,
        source: String = SiteAttribution.SOURCE_MATCH
    ): SiteAttribution? {
        val server = try { serverConfigDao.getById(order.serverId) } catch (_: Exception) { null }
        val name = SiteNames.resolve(order.serverName, order.websiteName, server?.displayName())
            ?: return null
        return recordAttribution(transactionId, AttributionEvent.Matched(order.serverId, name, source))
    }

    /** บันทึกว่ายอดนี้เป็นของเซิร์ฟ [serverId] (เช่น smart-auto ยืนยันบิลของเซิร์ฟนั้น) — ชื่อ = ชื่อเซิร์ฟที่ใช้แสดง */
    suspend fun recordServerAttribution(
        transactionId: Long,
        serverId: Long,
        source: String = SiteAttribution.SOURCE_MATCH
    ): SiteAttribution? {
        val server = try { serverConfigDao.getById(serverId) } catch (_: Exception) { null } ?: return null
        return recordAttribution(transactionId, AttributionEvent.Matched(serverId, server.displayName(), source))
    }

    /**
     * external_site (CONTRACT §C3) — เซิร์ฟ [fromServerId] บอกว่ายอดนี้เป็นของเว็บ [site]
     * ผูกกับเซิร์ฟในเครื่องที่ชื่อ/โดเมนตรงกัน (ถ้ามี) — เป็นแค่ hint ไม่ใช้อนุมัติ
     */
    suspend fun recordExternalSiteHint(
        transactionId: Long,
        site: String,
        fromServerId: Long?
    ): SiteAttribution? {
        val name = SiteNames.clean(site) ?: return null
        val servers = try { serverConfigDao.getActiveConfigs() } catch (_: Exception) { emptyList() }
        val serverId = SiteNames.serverIdForSite(name, servers)?.takeIf { it != fromServerId }
        return recordAttribution(transactionId, AttributionEvent.Hint(serverId, name, fromServerId))
    }

    /**
     * นำคำตอบ /notify ของทุกเซิร์ฟมารวม — เรียงตามลำดับเซิร์ฟ (deterministic):
     * matched=true ก่อน (ถ้าตอบ matched สองเซิร์ฟ = conflict), แล้วค่อย external_site hint
     */
    private suspend fun applyNotifyAttributions(
        transaction: BankTransaction,
        servers: List<ServerConfig>,
        replies: Map<Long, NotifyAttribution>
    ) {
        if (transaction.type != TransactionType.CREDIT || replies.isEmpty()) return
        val ordered = servers.mapNotNull { s -> replies[s.id]?.let { s to it } }
        for ((server, reply) in ordered) {
            if (reply.matched) {
                recordAttribution(
                    transaction.id,
                    AttributionEvent.Matched(server.id, reply.siteName, SiteAttribution.SOURCE_NOTIFY)
                )
            }
        }
        for ((server, reply) in ordered) {
            val site = reply.externalSite ?: continue
            if (reply.matched) continue
            recordExternalSiteHint(transaction.id, site, server.id)
        }
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

        // 🌐 คำตอบ /notify แต่ละเซิร์ฟ (matched / external_site) — thread-safe, รวมหลังทุกตัวเสร็จ
        val notifyReplies = ConcurrentHashMap<Long, NotifyAttribution>()

        // Execute sync to all remaining servers in parallel
        val results = ParallelSyncHelper.executeParallelBoolean(
            servers = serverList,
            maxConcurrency = 5,
            timeoutMs = 10_000L  // 10s per server (reduced for real-time)
        ) { serverId ->
            val server = serverConfigDao.getById(serverId) ?: return@executeParallelBoolean false
            syncToServer(transaction, server, deviceId, notifyReplies)
        }

        applyNotifyAttributions(transaction, serversToSync, notifyReplies)

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

        // Mark as synced ทันทีเมื่อ server ใด ๆ confirm — ผู้ใช้จะเห็นไฟเขียวทันที
        // multi-server retry ทำงานต่อได้ผ่าน sync_logs (getUnsyncedTransactions ใช้ sync_logs เป็นฐาน
        // ไม่ใช่ isSynced) — รายการที่ partial sync จะถูก retry จนครบทุก server
        val newSyncedIds = alreadySyncedServerIds + results.results.filter { it.success }.map { it.serverId }
        val allServersSynced = activeServers.all { it.id in newSyncedIds }

        if (results.anySucceeded) {
            val firstSuccessfulServerId = results.results.firstOrNull { it.success }?.serverId
                ?: activeServers.first().id
            transactionDao.markAsSynced(
                transaction.id,
                firstSuccessfulServerId,
                if (allServersSynced) "ALL_SYNCED" else "PARTIAL_SYNCED"
            )
            if (allServersSynced) {
                Log.i(TAG, "Transaction ${transaction.id} fully synced to ALL ${activeServers.size} servers")
            } else {
                val syncedNames = results.results.filter { it.success }.joinToString { it.serverName }
                val failedNames = results.results.filter { !it.success }.joinToString { it.serverName }
                Log.w(TAG, "Transaction ${transaction.id} partial sync: OK=[$syncedNames] FAILED=[$failedNames] — retry via sync_logs")
            }
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
        deviceId: String,
        notifyReplies: MutableMap<Long, NotifyAttribution>? = null
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
                // 🌐 CONTRACT §C3: เซิร์ฟบอกว่ายอดนี้เป็นของเว็บอื่น (hint — ไม่ใช่ match)
                val externalSite = SiteNames.clean(responseData?.get("external_site") as? String)
                var matchedSiteName: String? = null
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
                                matchedSiteName = SiteNames.resolve(
                                    remoteOrder.server_name,
                                    remoteOrder.order_details_json?.get("website_name")?.toString(),
                                    null
                                )
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
                if (anyMatched || externalSite != null) {
                    notifyReplies?.put(
                        server.id,
                        NotifyAttribution(
                            matched = anyMatched,
                            siteName = matchedSiteName ?: server.displayName(),
                            externalSite = externalSite
                        )
                    )
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
