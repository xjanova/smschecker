package com.thaiprompt.smschecker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.SmsCheckerApp
import com.thaiprompt.smschecker.data.db.SmsSenderRuleDao
import com.thaiprompt.smschecker.data.license.LicenseManager
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionSource
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.OrphanTransactionRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.domain.attribution.AttributionEvent
import com.thaiprompt.smschecker.domain.attribution.SiteAttribution
import com.thaiprompt.smschecker.domain.parser.BankSmsParser
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SmsProcessingService : Service() {

    companion object {
        const val ACTION_PROCESS_SMS = "com.thaiprompt.smschecker.PROCESS_SMS"
        const val ACTION_PROCESS_NOTIFICATION = "com.thaiprompt.smschecker.PROCESS_NOTIFICATION"
        const val ACTION_START_MONITORING = "com.thaiprompt.smschecker.START_MONITORING"
        const val ACTION_SYNC_ALL = "com.thaiprompt.smschecker.SYNC_ALL"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_SOURCE_PACKAGE = "source_package"

        private const val TAG = "SmsProcessingService"
        private const val NOTIFICATION_ID = 1001
        private const val DEDUP_WINDOW_MS = 60_000L // 60 seconds dedup window

        fun enqueueWork(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    @Inject lateinit var repository: TransactionRepository
    @Inject lateinit var secureStorage: SecureStorage
    @Inject lateinit var smsSenderRuleDao: SmsSenderRuleDao
    @Inject lateinit var parser: BankSmsParser
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var orderRepository: OrderRepository
    @Inject lateinit var orphanRepository: OrphanTransactionRepository

    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Session counters for notification display — atomic for concurrent access
    private val sessionDetectedCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val sessionMatchedCount = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Persist the timestamp of the last transaction we processed so RealtimeSyncService
     * (which owns the heartbeat notification) can display it. Survives service restart.
     */
    private fun markTransactionSeen() {
        try {
            applicationContext.getSharedPreferences("smschecker_heartbeat", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_transaction_at", System.currentTimeMillis())
                .apply()
        } catch (_: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Recreate scope if previously cancelled (service restart via START_STICKY)
        if (!serviceScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive) {
            serviceScope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        }
        startForeground(NOTIFICATION_ID, createNotification("SMS Payment Checker กำลังทำงาน"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // License check — don't process if license expired
        if (!LicenseManager.isLicenseValid()) {
            Log.w(TAG, "License not valid — ignoring command: ${intent?.action}")
            return START_STICKY
        }

        when (intent?.action) {
            ACTION_PROCESS_SMS -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return START_STICKY
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
                val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())

                processSms(sender, message, timestamp)
            }
            ACTION_PROCESS_NOTIFICATION -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return START_STICKY
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
                val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
                val sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE) ?: ""

                processNotification(sender, message, timestamp, sourcePackage)
            }
            ACTION_SYNC_ALL -> {
                syncAllUnsynced()
            }
            ACTION_START_MONITORING -> {
                Log.d(TAG, "Monitoring service started")
            }
        }

        return START_STICKY
    }

    private fun processSms(sender: String, message: String, timestamp: Long) {
        if (!secureStorage.isMonitoringEnabled()) return

        serviceScope.launch {
            try {
                // Load custom rules
                val rules = smsSenderRuleDao.getActiveRules()
                parser.setCustomRules(rules)

                // Check if this is a bank transaction SMS
                if (!parser.isBankTransactionSms(sender, message)) {
                    Log.d(TAG, "Not a bank SMS from: $sender")
                    return@launch
                }

                // Parse the SMS
                val transaction = parser.parse(sender, message, timestamp)
                if (transaction == null) {
                    Log.w(TAG, "Failed to parse bank SMS from: $sender")
                    return@launch
                }

                Log.d(TAG, "Parsed transaction: ${transaction.bank} ${transaction.type} ${transaction.amount}")

                // Atomic dedup + insert (uses app-wide Mutex to prevent race condition
                // when SMS and bank-app notification arrive within same millisecond)
                val savedId = repository.insertIfNotDuplicate(transaction, DEDUP_WINDOW_MS)
                if (savedId == null) {
                    Log.d(TAG, "Duplicate SMS transaction detected, skipping: ${transaction.bank} ${transaction.amount}")
                    return@launch
                }
                val savedTransaction = transaction.copy(id = savedId)
                sessionDetectedCount.incrementAndGet()
                markTransactionSeen()

                // Update foreground notification with counters
                updateNotification("กำลังทำงาน | ตรวจจับ ${sessionDetectedCount.get()} | แมท ${sessionMatchedCount.get()}")

                // Try to match with orders using MATCH-ONLY MODE
                // Query servers with SMS amount instead of fetching all orders
                var creditMatch = CreditMatch()  // ← TTS อ่านรายละเอียดบิลเฉพาะเมื่อ server approve จริงเท่านั้น
                if (transaction.type == TransactionType.CREDIT) {
                    try {
                        val amountDouble = transaction.amount.toDoubleOrNull()
                        if (amountDouble != null) {
                            creditMatch = matchCreditToOrders(
                                savedTransaction = savedTransaction,
                                amountDouble = amountDouble,
                                timestamp = timestamp,
                                source = TransactionSource.SMS,
                                via = "SMS"
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Order matching failed", e)
                    }
                }

                // Sync to servers
                val synced = repository.syncTransaction(savedTransaction)
                // 🌐 อ่านแถวล่าสุด — /orders/match + /notify อาจบันทึกว่ายอดนี้เป็นของเว็บไหน/ชนหลายเว็บ
                val finalTransaction = latestOf(savedTransaction)
                if (synced) {
                    Log.d(TAG, "Transaction synced successfully")
                    showTransactionNotification(finalTransaction)
                } else {
                    Log.w(TAG, "Transaction saved locally but sync failed")
                }

                // TTS announcement
                // 🔊 (2026-06-03) อ่านออกเสียง "ทุกครั้ง" ที่ตรวจจับเงินเข้า/ออก — ไม่เงียบแม้ approve เน็ตพลาด
                //   บั๊กเดิม: gating ทั้งก้อนด้วย isServerApproved (shouldSpeak) ทำให้เคส
                //     "ลูกค้าจ่ายจริง + match บิลได้ แต่ approve round-trip พลาด (เน็ตสะดุด/timeout)" → เงียบสนิท
                //     ทั้งที่ approveOrder() ได้ queue PendingAction.APPROVE ไว้ retry แล้ว บิลจะเขียวในที่สุด
                //   แก้: พูดยอดเสมอ; ส่วนรายละเอียดบิล (เลขบิล/สินค้า/เจ้าของ) อ่านเฉพาะเมื่อ server ยืนยันแล้ว (เขียว)
                //     เพื่อกันการอ่าน "ชื่อเจ้าของผิดบิล" ก่อนยืนยัน (กรณียอดซ้ำหลายบิล)
                speakTransaction(finalTransaction, creditMatch)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            }
        }
    }

    private fun processNotification(sender: String, message: String, timestamp: Long, sourcePackage: String) {
        if (!secureStorage.isNotificationListeningEnabled()) return

        serviceScope.launch {
            try {
                // Load custom rules
                val rules = smsSenderRuleDao.getActiveRules()
                parser.setCustomRules(rules)

                // Check if this is a bank transaction notification
                if (!parser.isBankTransactionSms(sender, message)) {
                    Log.d(TAG, "Not a bank notification from: $sender ($sourcePackage)")
                    return@launch
                }

                // Parse the notification
                val transaction = parser.parse(sender, message, timestamp)
                if (transaction == null) {
                    Log.w(TAG, "Failed to parse bank notification from: $sender")
                    return@launch
                }

                // Mark as notification source and include package info
                val notifTransaction = transaction.copy(
                    sourceType = TransactionSource.NOTIFICATION,
                    senderAddress = "$sender ($sourcePackage)"
                )

                Log.d(TAG, "Parsed notification: ${notifTransaction.bank} ${notifTransaction.type} ${notifTransaction.amount}")

                // Atomic dedup + insert (see SMS path above for rationale)
                val savedId = repository.insertIfNotDuplicate(notifTransaction, DEDUP_WINDOW_MS)
                if (savedId == null) {
                    Log.d(TAG, "Duplicate notification transaction detected, skipping: ${notifTransaction.bank} ${notifTransaction.amount}")
                    return@launch
                }
                val savedTransaction = notifTransaction.copy(id = savedId)
                sessionDetectedCount.incrementAndGet()
                markTransactionSeen()

                // Update foreground notification with counters
                updateNotification("กำลังทำงาน | ตรวจจับ ${sessionDetectedCount.get()} | แมท ${sessionMatchedCount.get()}")

                // Try to match with orders using MATCH-ONLY MODE
                // Query servers with notification amount instead of fetching all orders
                var creditMatch = CreditMatch()  // ← TTS อ่านรายละเอียดบิลเฉพาะเมื่อ server approve จริงเท่านั้น
                if (notifTransaction.type == TransactionType.CREDIT) {
                    try {
                        val amountDouble = notifTransaction.amount.toDoubleOrNull()
                        if (amountDouble != null) {
                            creditMatch = matchCreditToOrders(
                                savedTransaction = savedTransaction,
                                amountDouble = amountDouble,
                                timestamp = timestamp,
                                source = TransactionSource.NOTIFICATION,
                                via = "notification"
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Order matching failed for notification", e)
                    }
                }

                // Sync to servers
                val synced = repository.syncTransaction(savedTransaction)
                val finalTransaction = latestOf(savedTransaction)
                if (synced) {
                    Log.d(TAG, "Notification transaction synced successfully")
                    showTransactionNotification(finalTransaction)
                } else {
                    Log.w(TAG, "Notification transaction saved locally but sync failed")
                }

                // TTS announcement — ดูเหตุผลใน processSms() path ด้านบน (พูดยอดเสมอ, รายละเอียดบิลเมื่อเขียว)
                speakTransaction(finalTransaction, creditMatch)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    /** ผลจับคู่เงินเข้า — รายละเอียดบิลใช้ประกอบเสียงประกาศ (อ่านเฉพาะเมื่อ server ยืนยันแล้ว) */
    private data class CreditMatch(
        val orderNumber: String? = null,
        val productName: String? = null,
        val customerName: String? = null,
        val isServerApproved: Boolean = false
    )

    private fun ApprovalStatus?.isApproved(): Boolean =
        this == ApprovalStatus.AUTO_APPROVED || this == ApprovalStatus.MANUALLY_APPROVED

    /**
     * จับคู่ยอดเงินเข้ากับบิลบน "ทุกเซิร์ฟ" — ใช้ร่วมกันทั้งทาง SMS และแจ้งเตือนแอปธนาคาร
     * (เดิมเป็นโค้ดซ้ำสองชุด แยกไว้ที่เดียวเพื่อให้กติกา multi-site เหมือนกันทั้งสองทาง)
     *
     * 🌐 (2026-09-15) Multi-site (CONTRACT §E) — ตัดสินจากคำตอบครบทุกเซิร์ฟ:
     *   ≥2 เว็บตรง → ห้ามอนุมัติที่ไหนเลย + ธงชนบนยอดเงิน + แจ้งเตือนชื่อเว็บ
     *                ไม่เก็บเป็น orphan — กัน reconciler มา auto-approve ใบใดใบหนึ่งทีหลัง (แอดมินต้องเลือกเอง)
     *   1 เว็บตรง  → อนุมัติที่เว็บนั้น (ทางเดิม) + บันทึกว่ายอดนี้เป็นของเว็บนั้น
     *   0 เว็บ     → orphan (ทางเดิม); ถ้าเซิร์ฟตอบ external_site → จำเป็น hint และไม่ smart-auto
     */
    private suspend fun matchCreditToOrders(
        savedTransaction: BankTransaction,
        amountDouble: Double,
        timestamp: Long,
        source: TransactionSource,
        via: String
    ): CreditMatch {
        Log.d(TAG, "🔍 MATCH-ONLY MODE: Querying servers for $via amount: $amountDouble")

        // Use match-only mode: query servers with amount (include bank and timestamp for history)
        val decision = orderRepository.matchOrderByAmount(
            amount = amountDouble,
            bank = savedTransaction.bank,
            transactionTimestamp = timestamp
        )

        // ⚠️ ยอดเดียวตรงกับบิลหลายเว็บ — ห้ามส่ง approve ไปที่ไหนเลย
        if (decision.isConflict) {
            val sites = decision.matches.map { it.siteName }
            // เซิร์ฟบางตัว auto-confirm เองตอน /orders/match (auto_confirm_matched) — บอกแอดมินว่าเว็บไหนอนุมัติไปแล้ว
            val alreadyApprovedAt = decision.matches
                .filter { it.match?.order?.approvalStatus.isApproved() }
                .map { it.siteName }
            Log.w(TAG, "⚠️ MULTI-SITE CONFLICT ($via): amount=$amountDouble matched $sites — NOT approving anywhere (already approved server-side at: $alreadyApprovedAt)")
            repository.recordAttribution(
                savedTransaction.id,
                AttributionEvent.Conflict(sites, alreadyApprovedAt)
            )
            return CreditMatch()
        }

        val matchResult = decision.winner?.match
        if (matchResult != null) {
            val matchedOrder = matchResult.order
            sessionMatchedCount.incrementAndGet()
            Log.d(TAG, "✅ Matched $via with order: ${matchedOrder.orderNumber} on server ${matchResult.serverName} (site '${matchResult.siteName}')")
            updateNotification("กำลังทำงาน | ตรวจจับ ${sessionDetectedCount.get()} | แมท ${sessionMatchedCount.get()}")

            // 🌐 ยอดนี้เป็นของเว็บนี้ (บันทึกแม้ approve รอบนี้พลาด — approveOrder queue retry ไว้แล้ว)
            repository.recordAttribution(
                savedTransaction.id,
                AttributionEvent.Matched(matchResult.serverId, matchResult.siteName, SiteAttribution.SOURCE_MATCH)
            )

            // Server's /match endpoint already auto-approves when auto_confirm=true
            // Only send approve if order is still pending (server didn't auto-approve)
            var isServerApproved = false
            if (!matchedOrder.approvalStatus.isApproved()) {
                try {
                    val approvalSuccess = orderRepository.approveOrder(matchedOrder.id)
                    if (approvalSuccess) {
                        Log.d(TAG, "✅ Successfully approved order ($via): ${matchedOrder.orderNumber}")
                        isServerApproved = true
                    } else {
                        Log.w(TAG, "⚠️ Failed to approve order ($via): ${matchedOrder.orderNumber}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error approving order ($via): ${matchedOrder.orderNumber}", e)
                }
            } else {
                Log.d(TAG, "✅ Order already approved by server ($via): ${matchedOrder.orderNumber}")
                isServerApproved = true
            }
            return CreditMatch(
                orderNumber = matchedOrder.orderNumber,
                productName = matchedOrder.productName,
                customerName = matchedOrder.customerName,
                isServerApproved = isServerApproved
            )
        }

        // ไม่พบออเดอร์ที่ตรงกัน → เก็บเป็น Orphan Transaction
        // 🔧 (2026-05-21) เก็บ orphan **ทุกยอด** (รวมเลขกลม .00)
        //   เคสบั๊กเดิม: filter hasDecimal ทำให้ SMS เลขกลม (ลูกค้าโอน 39, 100, 500 บาท)
        //   ถูก skip → admin ไม่เห็นใน app → ไม่มีโอกาส manual match → ลูกค้าเดือดร้อน
        //   user spec (2026-05-21): "ยอดที่ลูกค้าโอน แบบไม่มีเศษสตางค์ ทำไมไม่เห็น"
        //   ผลที่ตามมา: amount=.00 จะ match UPA ไม่ได้ (ระบบใช้ทศนิยม) แต่
        //   admin เห็นใน Orphans tab + Force Approve ทีละบิลได้
        Log.d(TAG, "⏳ No matching order for $via amount $amountDouble, saving as orphan")

        // 🌐 CONTRACT §C3: เซิร์ฟบอกว่ายอดนี้เป็นของเว็บอื่น → จำเป็น hint (ไม่ใช่ match ไม่อนุมัติ)
        val externalSite = decision.externalSite
        if (externalSite != null) {
            repository.recordExternalSiteHint(savedTransaction.id, externalSite, decision.externalSiteFromServerId)
        }

        var savedOrphanId: Long = -1
        try {
            savedOrphanId = orphanRepository.saveAsOrphan(
                transaction = savedTransaction,
                source = source
            )
            Log.i(TAG, "💾 Saved orphan transaction ($via): ${savedTransaction.bank} ${savedTransaction.amount}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save orphan transaction ($via)", e)
        }

        if (externalSite != null) {
            // 🛡️ เซิร์ฟบอกแล้วว่าเงินนี้เป็นของ '$externalSite' — ห้าม smart-auto ไปยืนยันบิลของเว็บอื่น
            //   (fail-open ไปหาแอดมิน ไม่ใช่ auto-credit)
            Log.i(TAG, "🌐 external_site='$externalSite' ($via) — skip smart auto-match, leave for admin")
            return CreditMatch()
        }

        // 🤖 (2026-05-21) Smart mode auto-match
        //   ถ้า device approval_mode=SMART + เจอ candidate confidence สูง
        //   → confirm ทันที (ไม่รอ admin กดที่ orphans tab)
        //   Criteria: 1 candidate เดียว + name_score>=70 + time_delta<=60min
        try {
            val smartMatch = orderRepository.attemptSmartMatchForOrphan(
                amount = amountDouble,
                senderName = savedTransaction.senderOrReceiver,
                smsTimestamp = savedTransaction.timestamp
            )
            if (smartMatch != null) {
                val matchedBill = smartMatch.billReference
                Log.w(TAG, "🤖 SMART AUTO ($via): SMS matched to $matchedBill (no admin click needed)")
                // 🛡️ (2026-06-04) mark orphan ว่า resolved ทันที กัน reconciler (RealtimeSyncService/
                //   OrderSyncWorker checkOrphansForNewOrders) มาจับ orphan เดิมแล้ว approve/dispatch ซ้ำ
                //   (บิลดูดวงโดน dispatch 2 รอบ = เสียงาน). orphan ที่ confirm แล้วต้องออกจาก PENDING
                if (savedOrphanId > 0) {
                    try { orphanRepository.markAsManuallyResolved(savedOrphanId, "smart-auto:$matchedBill") }
                    catch (e: Exception) { Log.w(TAG, "mark orphan resolved failed ($via)", e) }
                }
                repository.recordServerAttribution(savedTransaction.id, smartMatch.serverId)
                updateNotification("กำลังทำงาน | ตรวจจับ ${sessionDetectedCount.get()} | แมท ${sessionMatchedCount.incrementAndGet()} 🤖")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Smart auto-match ($via) failed (non-fatal)", e)
        }
        return CreditMatch()
    }

    /** แถวล่าสุดจาก DB (มีข้อมูลเว็บที่ /orders/match + /notify เพิ่งบันทึก) — อ่านไม่ได้ใช้ตัวเดิม */
    private suspend fun latestOf(transaction: BankTransaction): BankTransaction =
        try { repository.getTransaction(transaction.id) ?: transaction } catch (e: Exception) { transaction }

    /**
     * ประกาศเสียง — พูดยอดเสมอ, รายละเอียดบิลเฉพาะเมื่อ server ยืนยันแล้ว
     * 🌐 เงินเข้าที่รู้เว็บ → "…จาก <เว็บ>"; ชนหลายเว็บ → เตือนสั้นๆ; ไม่รู้เว็บ = ประโยคเดิม
     */
    private fun speakTransaction(transaction: BankTransaction, credit: CreditMatch) {
        try {
            val isCredit = transaction.type == TransactionType.CREDIT
            ttsManager.speakTransaction(
                bankName = transaction.bank,
                amount = transaction.amount,
                isCredit = isCredit,
                orderNumber = if (credit.isServerApproved) credit.orderNumber else null,
                productName = if (credit.isServerApproved) credit.productName else null,
                customerName = if (credit.isServerApproved) credit.customerName else null,
                siteName = if (isCredit) transaction.attributedSiteName() else null,
                siteConflict = isCredit && transaction.matchConflict
            )
        } catch (e: Exception) {
            Log.w(TAG, "TTS announcement failed (${transaction.sourceType})", e)
        }
    }

    private fun syncAllUnsynced() {
        serviceScope.launch {
            try {
                val count = repository.syncAllUnsynced()
                Log.d(TAG, "Synced $count transactions")
                if (count > 0) {
                    updateNotification("ซิงค์สำเร็จ $count รายการ")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing transactions", e)
            }
        }
    }

    private fun showTransactionNotification(transaction: com.thaiprompt.smschecker.data.model.BankTransaction) {
        val title = if (transaction.type == com.thaiprompt.smschecker.data.model.TransactionType.CREDIT) {
            "💰 เงินเข้า - ${transaction.bank}"
        } else {
            "💸 เงินออก - ${transaction.bank}"
        }

        // 🌐 บอกเว็บของยอดนี้ในแจ้งเตือนด้วย (ไม่รู้เว็บ = ข้อความเดิม)
        val siteText = if (transaction.matchConflict) "ตรงกับหลายเว็บ — กรุณาตรวจสอบ" else transaction.attributedSiteName()
        val notification = NotificationCompat.Builder(this, SmsCheckerApp.NOTIFICATION_CHANNEL_TRANSACTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(listOfNotNull(transaction.getFormattedAmount(), siteText).joinToString(" · "))
            .setSubText("ตรวจจับ ${sessionDetectedCount.get()} | แมท ${sessionMatchedCount.get()}")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(transaction.id.toInt() + 2000, notification)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, SmsCheckerApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SMS Payment Checker")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(createPendingIntent())
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 🛡️ (2026-06-04) Android 14: dataSync foreground service มี cumulative timeout ~6 ชม./24 ชม.
     *   เมื่อหมดโควต้า ระบบเรียก onTimeout แล้วเราต้อง stop ภายในไม่กี่วินาที ไม่งั้นถูก force-kill
     *   (และการไม่ handle บน A14 อาจถูกบันทึกเป็น crash). service นี้เป็น on-demand (start ต่อ SMS)
     *   จึงปิดตัวสะอาดได้ทันที — SMS ถัดไป start ใหม่ผ่าน SmsBroadcastReceiver (ภายใต้ exemption ของ SMS_RECEIVED)
     */
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "dataSync FGS timeout (A14) — stopping cleanly")
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf(startId)
    }

    override fun onDestroy() {
        // 🔊 (2026-06-03) ไม่เรียก ttsManager.stop() ที่นี่อีกต่อไป
        //   TtsManager เป็น @Singleton ใช้ร่วมทั้งแอป — การ stop() ตอน service ถูก destroy จะ
        //   "ตัดเสียงประกาศที่กำลังพูดค้างอยู่" ทุกครั้งที่ระบบ kill/recreate service นี้
        //   (เกิดบ่อยมากกับ on-demand foreground service ภายใต้ memory pressure / dataSync timeout)
        //   = อีกหนึ่งสาเหตุของอาการ "บางครั้งเงียบ". ปล่อยให้ประโยคที่ค้างอยู่พูดจนจบเอง.
        serviceScope.cancel()
        super.onDestroy()
    }
}
