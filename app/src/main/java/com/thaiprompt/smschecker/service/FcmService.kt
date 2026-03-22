package com.thaiprompt.smschecker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.SmsCheckerApp
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.MatchConfidence
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging Service
 *
 * รับ push notifications จากเซิร์ฟเวอร์เมื่อมีคำสั่งซื้อใหม่ที่รอชำระเงิน
 * เมื่อได้รับ notification จะ:
 * 1. แสดง notification ให้ผู้ใช้เห็น
 * 2. ทริกให้แอพ sync คำสั่งซื้อทันที (ไม่ต้องรอ 15 นาที)
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var orderApprovalDao: OrderApprovalDao
    @Inject lateinit var serverConfigDao: ServerConfigDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "FcmService"
        const val FCM_TOKEN_KEY = "fcm_token"
    }

    /**
     * เมื่อได้รับ FCM token ใหม่ (ครั้งแรก หรือ token ถูก refresh)
     * บันทึกลง SharedPreferences เพื่อส่งไปเซิร์ฟเวอร์ตอน register-device
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")

        // Save token to SharedPreferences
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(FCM_TOKEN_KEY, token)
            .putBoolean("fcm_token_needs_sync", true)
            .apply()

        // Trigger one-time sync to send new token to server
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    /**
     * เมื่อได้รับ push notification จากเซิร์ฟเวอร์
     * Data message format:
     * {
     *   "type": "new_order",
     *   "order_id": "123",
     *   "amount": "500.37",
     *   "order_number": "ORD-20240101-001"
     * }
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        val data = message.data

        when (data["type"]) {
            "new_order" -> handleNewOrder(data)
            "order_update" -> handleOrderUpdate(data)
            "order_approved" -> handleOrderStatusChange(data, "อนุมัติแล้ว", "✅")
            "order_rejected" -> handleOrderStatusChange(data, "ถูกปฏิเสธ", "❌")
            "order_cancelled" -> handleOrderStatusChange(data, "ถูกยกเลิก", "🚫")
            "order_deleted" -> handleOrderDeleted(data)
            "payment_matched" -> handlePaymentMatched(data)
            "settings_changed" -> handleSettingsChanged(data)
            "sync" -> handleSyncRequest()
            else -> {
                Log.w(TAG, "Unknown FCM message type: ${data["type"]}")
                // Default: trigger sync anyway
                handleSyncRequest()
            }
        }
    }

    /**
     * คำสั่งซื้อใหม่ถูกสร้าง — แสดง notification + ทริก sync
     * สำหรับ fortune orders: insert เข้า Room DB ทันทีจาก FCM data
     * เพื่อให้บิลแสดงในหน้า Orders โดยไม่ต้องรอ sync สำเร็จ
     */
    private fun handleNewOrder(data: Map<String, String>) {
        val orderId = data["order_id"] ?: "N/A"
        val amount = data["amount"] ?: "0.00"
        val orderNumber = data["order_number"] ?: "N/A"
        val isFortune = data["is_fortune_reading"] == "true"

        // ใช้ order_number เป็น notificationId key เพื่อให้ notification ทับกันเมื่อสถานะเปลี่ยน
        // (เช่น new_order → order_approved ของบิลเดียวกัน จะทับกันแทนที่จะเด้ง 2 ครั้ง)
        val notifKey = orderNumber.hashCode()

        val title = if (isFortune) "🔮 บิลดูดวงใหม่ รอชำระเงิน" else "คำสั่งซื้อใหม่ รอชำระเงิน"
        val body = if (isFortune) {
            val customer = data["customer_name"] ?: ""
            "บิล #$orderNumber ยอด ฿$amount $customer"
        } else {
            "คำสั่งซื้อ #$orderNumber ยอด ฿$amount กำลังรอการชำระเงิน"
        }

        showNotification(title = title, body = body, notificationId = notifKey)

        // Fortune orders: insert เข้า DB ทันทีจาก FCM data
        // ไม่ต้องรอ sync เพราะ sync อาจ fail หรือ fortune order อาจไม่กลับมาใน response
        if (isFortune) {
            val remoteId = orderId.toLongOrNull()
            val serverUrl = data["server_url"]  // URL ของเซิร์ฟที่ส่ง FCM มา (อาจเป็น null สำหรับ FCM เก่า)
            if (remoteId != null) {
                serviceScope.launch {
                    try {
                        insertFortuneOrderFromFcm(
                            remoteId = remoteId,
                            orderNumber = orderNumber,
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            customerName = data["customer_name"],
                            serverUrl = serverUrl
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert fortune order from FCM: ${e.message}", e)
                    }
                }
            }
        }

        // Trigger immediate sync to get the new order (and update with full server data)
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    /**
     * Insert fortune order เข้า Room DB ทันทีจาก FCM data
     * ใช้ server_url จาก FCM data เพื่อระบุว่า order มาจากเซิร์ฟไหน
     * ถ้าไม่มี server_url ใน FCM (FCM เก่า) → ใช้ server ที่มี URL ตรงกับ order แทน
     * ถ้า order มีอยู่แล้ว (จาก sync ก่อนหน้า) จะไม่ overwrite
     */
    private suspend fun insertFortuneOrderFromFcm(
        remoteId: Long,
        orderNumber: String,
        amount: Double,
        customerName: String?,
        serverUrl: String? = null
    ) {
        val servers = serverConfigDao.getActiveConfigs()
        if (servers.isEmpty()) {
            Log.w(TAG, "insertFortuneOrderFromFcm: No active servers")
            return
        }

        // หาเซิร์ฟที่ตรงกับ server_url จาก FCM (ถ้ามี)
        val server = if (serverUrl != null) {
            // Normalize URL เพื่อเปรียบเทียบ (ตัด trailing slash, lowercase)
            val normalizedFcmUrl = serverUrl.trimEnd('/').lowercase()
            servers.firstOrNull { it.baseUrl.trimEnd('/').lowercase() == normalizedFcmUrl }
                ?: servers.firstOrNull { normalizedFcmUrl.contains(it.baseUrl.trimEnd('/').lowercase()) }
                ?: servers.firstOrNull() // fallback ถ้าหาไม่เจอ
        } else {
            servers.firstOrNull()
        }

        if (server == null) {
            Log.w(TAG, "insertFortuneOrderFromFcm: Could not find matching server (serverUrl=$serverUrl)")
            return
        }

        Log.d(TAG, "insertFortuneOrderFromFcm: Using server=${server.name} (id=${server.id}) for remoteId=$remoteId, fcmServerUrl=$serverUrl")

        // เช็คว่ามีอยู่แล้วหรือไม่
        val existing = orderApprovalDao.getByRemoteId(remoteId, server.id)
        if (existing != null) {
            Log.d(TAG, "insertFortuneOrderFromFcm: Order $remoteId already exists (id=${existing.id}), skip")
            return
        }

        val order = OrderApproval(
            serverId = server.id,
            remoteApprovalId = remoteId,
            approvalStatus = ApprovalStatus.PENDING_REVIEW,
            confidence = MatchConfidence.HIGH,
            orderNumber = orderNumber,
            productName = "ดูดวง",
            customerName = customerName ?: "ลูกค้าดูดวง",
            amount = amount,
            serverName = server.name,
            syncedVersion = System.currentTimeMillis(),
            lastSyncedAt = System.currentTimeMillis()
        )

        val insertedId = orderApprovalDao.insert(order)
        Log.i(TAG, "insertFortuneOrderFromFcm: Inserted fortune order remoteId=$remoteId, orderNum=$orderNumber, amount=$amount, serverId=${server.id}, localId=$insertedId")
    }

    /**
     * สถานะคำสั่งซื้อเปลี่ยน (เช่น ชำระแล้ว, ยกเลิก)
     */
    private fun handleOrderUpdate(data: Map<String, String>) {
        val orderNumber = data["order_number"] ?: "N/A"
        val status = data["status"] ?: "unknown"

        showNotification(
            title = "อัพเดทคำสั่งซื้อ",
            body = "คำสั่งซื้อ #$orderNumber สถานะ: $status",
            notificationId = orderNumber.hashCode()
        )

        // Trigger sync
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    /**
     * สถานะ order เปลี่ยนจาก admin (approved/rejected/cancelled)
     * อัพเดท local DB ทันที ไม่ต้องรอ periodic sync
     *
     * สำหรับ fortune reading (is_fortune_reading=true):
     * - ใช้ข้อความภาษาไทยที่เหมาะสม (เช่น "บิลดูดวงชำระแล้ว")
     * - notificationId ใช้ orderNumber.hashCode() เพื่อทับ notification เดิมของ new_order
     */
    private fun handleOrderStatusChange(data: Map<String, String>, statusLabel: String, emoji: String) {
        val orderNumber = data["order_number"] ?: "N/A"
        val isFortune = data["is_fortune_reading"] == "true"
        val paymentStatus = data["payment_status"] ?: ""
        val isCancelled = paymentStatus == "cancelled"
        Log.i(TAG, "FCM: Order status change - $orderNumber $statusLabel (fortune=$isFortune, cancelled=$isCancelled)")

        val title = when {
            isFortune && isCancelled -> "🚫 บิลดูดวงถูกยกเลิก"
            isFortune -> "💰 บิลดูดวงชำระแล้ว!"
            else -> "$emoji คำสั่งซื้อ $statusLabel"
        }
        val amount = data["amount"] ?: ""
        val bank = data["bank"] ?: ""
        val body = when {
            isFortune && isCancelled -> "บิล #$orderNumber ถูกยกเลิกโดยลูกค้า"
            isFortune && amount.isNotEmpty() -> "บิล #$orderNumber ยอด ฿$amount จับคู่สำเร็จ" + if (bank.isNotEmpty()) " ($bank)" else ""
            else -> "คำสั่งซื้อ #$orderNumber $statusLabel"
        }

        showNotification(
            title = title,
            body = body,
            notificationId = orderNumber.hashCode()
        )

        // Trigger immediate sync to update local DB
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    /**
     * Order ถูกลบจาก admin → sync ทันทีเพื่อลบจาก local DB
     */
    private fun handleOrderDeleted(data: Map<String, String>) {
        val orderNumber = data["order_number"] ?: "N/A"
        Log.i(TAG, "FCM: Order deleted - $orderNumber")

        // Silent: no notification for deletion, just trigger sync
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    /**
     * การชำระเงินถูกจับคู่กับคำสั่งซื้อ → sync ทันที
     * ใช้ orderNumber.hashCode() เป็น notificationId เพื่อทับ notification เดิม
     */
    private fun handlePaymentMatched(data: Map<String, String>) {
        val orderNumber = data["order_number"] ?: "N/A"
        val amount = data["amount"] ?: "0.00"
        val bank = data["bank"] ?: ""
        val isFortune = data["is_fortune_reading"] == "true"

        Log.i(TAG, "FCM: Payment matched - $orderNumber ฿$amount ($bank) fortune=$isFortune")

        val title = if (isFortune) "💰 บิลดูดวงชำระแล้ว!" else "💰 ยืนยันการชำระเงินแล้ว"
        val body = if (isFortune) {
            "บิล #$orderNumber ยอด ฿$amount จับคู่สำเร็จ" + if (bank.isNotEmpty()) " ($bank)" else ""
        } else {
            "คำสั่งซื้อ #$orderNumber ยอด ฿$amount ชำระเงินสำเร็จ"
        }

        showNotification(
            title = title,
            body = body,
            notificationId = orderNumber.hashCode()
        )

        // Trigger immediate sync
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    /**
     * เซิร์ฟเวอร์ขอให้ sync (silent push)
     */
    /**
     * Server settings changed (e.g. admin changed approval_mode on web) — trigger sync.
     */
    private fun handleSettingsChanged(data: Map<String, String>) {
        Log.i(TAG, "Settings changed from server: $data")
        // Trigger full sync to pull updated settings (approval_mode, etc.)
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    private fun handleSyncRequest() {
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
    }

    private fun showNotification(title: String, body: String, notificationId: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "orders")
        }

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SmsCheckerApp.NOTIFICATION_CHANNEL_TRANSACTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
