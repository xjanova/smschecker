package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "bank_transactions")
data class BankTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bank: String,           // KBANK, SCB, KTB, BBL, GSB, BAY, TTB
    val type: TransactionType,  // CREDIT or DEBIT
    val amount: String,         // Stored as string for precision (BigDecimal)
    val accountNumber: String,  // Last 4 digits masked
    val senderOrReceiver: String, // Counterparty name/account
    val referenceNumber: String,  // Ref from SMS if available
    val rawMessage: String,     // Original SMS text
    val senderAddress: String,  // SMS sender number
    val timestamp: Long,        // When SMS was received
    val isSynced: Boolean = false,
    val syncedServerId: Long? = null, // Server ID that confirmed sync
    val syncResponse: String? = null,
    val sourceType: TransactionSource = TransactionSource.SMS,
    val createdAt: Long = System.currentTimeMillis(),
    // 🌐 (2026-09-15) Multi-site attribution (CONTRACT §E) — ยอดนี้เป็นของ "เว็บไหน"
    //   เครื่องเดียวอ่าน SMS บัญชีเดียว แต่ผูกหลายเซิร์ฟ (Thaiprompt + จันทรา.online)
    //   matchedServerId  = เซิร์ฟที่ยอดนี้เป็นของมัน (ต่างจาก syncedServerId = เซิร์ฟแรกที่ /notify ผ่าน)
    //   matchedSiteName  = ชื่อที่แสดง (server_name/website_name จากเซิร์ฟ → ชื่อที่ตั้งในเครื่อง)
    //   matchConflict    = ยอดตรงกับบิลมากกว่า 1 เว็บ → แอพไม่อนุมัติที่ไหนเลย รอแอดมินตรวจ
    //   conflictSites    = ชื่อเว็บที่ชนกัน คั่นด้วย '\n' (ใช้ conflictSiteList() อ่าน)
    //   attributionSource = match | notify | hint | conflict (SiteAttribution.SOURCE_*)
    val matchedServerId: Long? = null,
    val matchedSiteName: String? = null,
    val matchConflict: Boolean = false,
    val conflictSites: String? = null,
    val attributionSource: String? = null
) {
    /** ชื่อเว็บที่ชนกัน (ว่าง = ไม่มี conflict) */
    fun conflictSiteList(): List<String> =
        conflictSites?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** ชื่อเว็บที่ยอดนี้เป็นของมัน — null เมื่อยังไม่รู้ หรือเมื่อชนกันหลายเว็บ */
    fun attributedSiteName(): String? =
        if (matchConflict) null else matchedSiteName?.takeIf { it.isNotBlank() }

    fun getAmountAsBigDecimal(): BigDecimal = try {
        BigDecimal(amount)
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

    fun getFormattedAmount(): String {
        return try {
            val bd = getAmountAsBigDecimal()
            val prefix = if (type == TransactionType.CREDIT) "+" else "-"
            "$prefix฿${String.format("%,.2f", bd)}"
        } catch (e: Exception) {
            val prefix = if (type == TransactionType.CREDIT) "+" else "-"
            "$prefix฿$amount"
        }
    }

    fun getFormattedTimestamp(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "เมื่อสักครู่"
            diff < 3600_000 -> "${diff / 60_000} นาทีที่แล้ว"
            diff < 86400_000 -> "${diff / 3600_000} ชั่วโมงที่แล้ว"
            diff < 604800_000 -> "${diff / 86400_000} วันที่แล้ว"
            else -> {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            }
        }
    }

    fun getMaskedAccount(): String {
        return if (accountNumber.length > 4) {
            "xxx-${accountNumber.takeLast(4)}"
        } else {
            accountNumber
        }
    }
}

enum class TransactionType {
    CREDIT,  // เงินเข้า
    DEBIT    // เงินออก
}

enum class TransactionSource {
    SMS,          // จาก SMS
    NOTIFICATION  // จากแจ้งเตือนแอปธนาคาร
}
