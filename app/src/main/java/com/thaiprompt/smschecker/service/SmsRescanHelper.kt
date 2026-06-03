package com.thaiprompt.smschecker.service

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * 📡 (2026-05-21) SMS Re-scan Helper
 *
 * เคสใช้: ลูกค้ากดเช็คสถานะ / ส่งสลิป → backend ส่ง FCM `trigger_sms_rescan`
 *         → FcmService รับ → เรียก SmsRescanHelper.rescanInbox()
 *
 * ทำงาน:
 *   1. Query Telephony.Sms.Inbox content provider (READ_SMS permission)
 *   2. Filter date >= now - lookbackHours
 *   3. Loop SMS rows → fire ACTION_PROCESS_SMS intent ไป SmsProcessingService
 *   4. SmsProcessingService.insertIfNotDuplicate() จะ skip SMS ที่เคย process แล้ว
 *      → process เฉพาะ SMS ใหม่ที่ broadcast receiver พลาดไป
 *
 * Why needed: Android Doze mode / OEM kill อาจทำให้ SmsReceiver ไม่ทำงาน
 *             SMS เข้ามาในกล่องโทรศัพท์แล้ว แต่ app ไม่เคย process
 */
object SmsRescanHelper {

    private const val TAG = "SmsRescanHelper"

    /**
     * Re-scan SMS inbox + dispatch unprocessed messages to SmsProcessingService
     *
     * @param lookbackHours ดูย้อนหลังกี่ชั่วโมง (default 6)
     * @param reason audit log reason (e.g., "check_status", "slip_uploaded")
     * @return จำนวน SMS ที่ re-dispatch ไป processing service
     */
    fun rescanInbox(context: Context, lookbackHours: Int = 6, reason: String = "fcm_trigger"): Int {
        val cutoffMs = System.currentTimeMillis() - lookbackHours * 60L * 60L * 1000L
        var dispatched = 0

        try {
            val resolver: ContentResolver = context.contentResolver
            // ดึงเฉพาะ field ที่ใช้ — ลด memory + performance
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = "${Telephony.Sms.DATE} >= ?"
            val selectionArgs = arrayOf(cutoffMs.toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            val cursor = resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { c ->
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)

                if (addressIdx < 0 || bodyIdx < 0 || dateIdx < 0) {
                    Log.e(TAG, "rescanInbox: missing column indices (addr=$addressIdx body=$bodyIdx date=$dateIdx)")
                    return 0
                }

                Log.i(TAG, "📡 SMS rescan started: lookback=${lookbackHours}h reason=$reason rows=${c.count}")

                while (c.moveToNext()) {
                    val sender = c.getString(addressIdx) ?: continue
                    val body = c.getString(bodyIdx) ?: continue
                    val timestamp = c.getLong(dateIdx)

                    // Fire intent ไป SmsProcessingService — dedup จัดการที่ insertIfNotDuplicate()
                    // ถ้าเคย process แล้ว → skip; ถ้ายังไม่เคย → process ตามปกติ
                    val intent = Intent(context, SmsProcessingService::class.java).apply {
                        action = SmsProcessingService.ACTION_PROCESS_SMS
                        putExtra(SmsProcessingService.EXTRA_SENDER, sender)
                        putExtra(SmsProcessingService.EXTRA_MESSAGE, body)
                        putExtra(SmsProcessingService.EXTRA_TIMESTAMP, timestamp)
                    }

                    try {
                        // 🔧 (2026-06-03) ใช้ enqueueWork (startForegroundService บน O+) แทน startService
                        //   บั๊กเดิม: context.startService() จาก FCM background → IllegalStateException
                        //   ถ้า SmsProcessingService ยังไม่รัน → SMS ที่ตั้งใจ rescan ไม่ถูก dispatch → จับเงินไม่ได้
                        //   (FCM trigger + bank-wake delay ช่วยให้มี FGS-start window; ถ้าโดน A14 จำกัด จะ throw แล้วถูก catch)
                        SmsProcessingService.enqueueWork(context, intent)
                        dispatched++
                    } catch (e: Exception) {
                        Log.w(TAG, "rescanInbox: failed to start service for SMS from $sender", e)
                    }
                }
            } ?: run {
                Log.w(TAG, "rescanInbox: ContentResolver returned null cursor (no SMS permission?)")
            }

            Log.i(TAG, "📡 SMS rescan done: dispatched=$dispatched (dedup จะ skip ที่ process แล้ว)")
        } catch (e: SecurityException) {
            Log.e(TAG, "rescanInbox: READ_SMS permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "rescanInbox: unexpected error", e)
        }

        return dispatched
    }
}
