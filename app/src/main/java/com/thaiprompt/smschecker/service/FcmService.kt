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
import com.thaiprompt.smschecker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

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

    companion object {
        private const val TAG = "FcmService"
        const val FCM_TOKEN_KEY = "fcm_token"
    }

    /**
     * เมื่อได้รับ FCM token ใหม่ (ครั้งแรก หรือ token ถูก refresh)
     * บันทึกลง SharedPreferences เพื่อส่งไปเซิร์ฟเวอร์ตอน register-device
     */
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
     */
    private fun handleNewOrder(data: Map<String, String>) {
        val orderId = data["order_id"] ?: "N/A"
        val amount = data["amount"] ?: "0.00"
        val orderNumber = data["order_number"] ?: "N/A"

        // Show notification
        showNotification(
            title = "คำสั่งซื้อใหม่ รอชำระเงิน",
            body = "คำสั่งซื้อ #$orderNumber ยอด ฿$amount กำลังรอการชำระเงิน",
            notificationId = orderId.hashCode()
        )

        // Trigger immediate sync to get the new order
        OrderSyncWorker.enqueueOneTimeSync(applicationContext)
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
     * เซิร์ฟเวอร์ขอให้ sync (silent push)
     */
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
