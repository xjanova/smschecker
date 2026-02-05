package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ประวัติการจับคู่และอนุมัติสำเร็จ
 *
 * เก็บข้อมูล:
 * - ยอดเงินที่จับคู่
 * - จำนวนครั้งที่ถามเซิร์ฟเวอร์กว่าจะสำเร็จ
 * - เซิร์ฟเวอร์ที่จับคู่ได้
 * - เวลาที่ใช้ในการจับคู่
 */
@Entity(
    tableName = "match_history",
    indices = [
        Index("amount"),
        Index("matchedAt"),
        Index("serverId")
    ]
)
data class MatchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Transaction info
    val amount: Double,                    // ยอดเงินที่จับคู่
    val amountString: String,              // "500.37"
    val bank: String,                      // ธนาคารที่โอนมา
    val transactionTimestamp: Long,        // เวลา SMS เข้ามา

    // Match info
    val serverId: Long,                    // เซิร์ฟเวอร์ที่จับคู่ได้
    val serverName: String,                // ชื่อเซิร์ฟเวอร์
    val orderNumber: String?,              // เลขออเดอร์
    val remoteOrderId: Long,               // ID ออเดอร์ในเซิร์ฟเวอร์

    // Statistics
    val serverQueriesCount: Int,           // จำนวนครั้งที่ถามเซิร์ฟเวอร์กว่าจะสำเร็จ
    val totalServersQueried: Int,          // จำนวนเซิร์ฟเวอร์ที่ถามทั้งหมด
    val matchDurationMs: Long,             // เวลาที่ใช้จับคู่ (milliseconds)

    // Result
    val matchResult: MatchResult = MatchResult.SUCCESS,
    val approvalResult: ApprovalResult = ApprovalResult.AUTO_APPROVED,

    // Timestamps
    val matchedAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null
)

enum class MatchResult(val displayName: String) {
    SUCCESS("จับคู่สำเร็จ"),
    NO_MATCH("ไม่พบออเดอร์"),
    TIMEOUT("หมดเวลา"),
    ERROR("เกิดข้อผิดพลาด")
}

enum class ApprovalResult(val displayName: String) {
    AUTO_APPROVED("อนุมัติอัตโนมัติ"),
    MANUAL_APPROVED("อนุมัติด้วยตนเอง"),
    PENDING("รอการอนุมัติ"),
    FAILED("อนุมัติไม่สำเร็จ")
}
