package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Orphan Transaction - เก็บธุรกรรมที่จับคู่ไม่ได้
 *
 * ปัญหาที่แก้:
 * - ลูกค้าโอนยอดทศนิยม (เช่น 1,234.56) มาแล้ว
 * - แต่บิลหมดเวลา/ยกเลิก ก่อนแอพจะ sync จับคู่ได้
 * - ยอดนี้จะ "หลุด" ไปไม่มีบิลรองรับ
 *
 * วิธีแก้:
 * - เก็บธุรกรรมที่จับคู่ไม่ได้ไว้ใน orphan_transactions
 * - เมื่อมี order ใหม่เข้ามา ตรวจสอบกับ orphan ก่อน
 * - ถ้ายอดตรง = จับคู่อัตโนมัติ + แจ้งเตือน
 * - เก็บไว้ 7 วัน แล้วลบอัตโนมัติ
 */
@Entity(
    tableName = "orphan_transactions",
    indices = [
        Index("amount"),
        Index("bank"),
        Index("status"),
        Index("createdAt"),
        Index(value = ["amount", "bank", "status"]) // Composite for fast lookup
    ]
)
data class OrphanTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Transaction details
    val transactionId: Long,          // Reference to original BankTransaction
    val amount: Double,               // Amount for quick matching
    val amountString: String,         // Original amount string (e.g., "1234.56")
    val bank: String,
    val accountNumber: String = "",
    val senderOrReceiver: String = "",
    val referenceNumber: String = "",
    val rawMessage: String = "",

    // Status tracking
    val status: OrphanStatus = OrphanStatus.PENDING,
    val matchAttempts: Int = 0,       // How many times we tried to match
    val lastMatchAttemptAt: Long? = null,

    // If matched later
    val matchedOrderId: Long? = null,
    val matchedServerId: Long? = null,
    val matchedAt: Long? = null,

    // Timestamps
    val transactionTimestamp: Long,   // When the original transaction occurred
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Metadata
    val source: TransactionSource = TransactionSource.SMS,
    val notes: String? = null
)

enum class OrphanStatus(val displayName: String) {
    PENDING("Waiting for match"),        // ยังไม่จับคู่ได้
    MATCHED("Matched"),                  // จับคู่ได้แล้ว
    EXPIRED("Expired"),                  // หมดเวลา (7 วัน)
    MANUALLY_RESOLVED("Manually resolved"), // ผู้ใช้จัดการเอง
    IGNORED("Ignored")                   // ผู้ใช้ไม่สนใจ
}

enum class TransactionSource {
    SMS,
    NOTIFICATION
}
