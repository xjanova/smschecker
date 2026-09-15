package com.thaiprompt.smschecker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.SmsCheckerApp
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🌐 (2026-09-15) แจ้งเตือน "ยอดนี้ตรงกับหลายเว็บ" (CONTRACT §E)
 *
 * เรียกจาก TransactionRepository.recordAttribution() จุดเดียว — ทุกทางที่ทำให้เกิด conflict
 * (/orders/match ตรงหลายเซิร์ฟ, /notify ตอบ matched จากสองเซิร์ฟ, reconciler) จึงเด้งเตือนเหมือนกัน
 * และเด้งซ้ำเฉพาะเมื่อรายชื่อเว็บเปลี่ยน (notification id ผูกกับ transaction → อัพเดททับของเดิม)
 */
@Singleton
class SiteConflictNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SiteConflictNotifier"

        // ช่วงของตัวเอง — ไม่ชน 1001 (foreground), 2000+id (เงินเข้า), 9911/9912 (watchdog/voice)
        private const val NOTIFICATION_ID_BASE = 700_000

        fun notificationId(transactionId: Long): Int =
            NOTIFICATION_ID_BASE + (transactionId % 100_000L).toInt()

        /** ข้อความหลักที่ใช้ทั้งแจ้งเตือนและในแอพ */
        fun conflictMessage(sites: List<String>): String =
            "ยอดนี้ตรงกับหลายเว็บ: ${sites.joinToString(", ")} — กรุณาตรวจสอบ"
    }

    fun notifyConflict(
        transaction: BankTransaction,
        sites: List<String>,
        alreadyApprovedAt: List<String> = emptyList()
    ) {
        try {
            val headline = conflictMessage(sites)
            val body = buildString {
                append("${transaction.getFormattedAmount()} (${transaction.bank}) — ")
                append(headline)
                append("\nแอพจะไม่อนุมัติยอดนี้ที่เว็บใดเอง")
                if (alreadyApprovedAt.isNotEmpty()) {
                    append("\nเว็บที่แจ้งว่าอนุมัติไปแล้ว: ${alreadyApprovedAt.joinToString(", ")}")
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, SmsCheckerApp.NOTIFICATION_CHANNEL_TRANSACTION)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ยอดตรงกับหลายเว็บ — ยังไม่อนุมัติ")
                .setContentText(headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(notificationId(transaction.id), notification)
        } catch (e: Exception) {
            // ไม่มีสิทธิ์แจ้งเตือน (Android 13+) ฯลฯ — ชิปเตือนในแอพยังแสดงอยู่ ไม่ต้อง crash
            Log.w(TAG, "notifyConflict failed for tx=${transaction.id}", e)
        }
    }
}
