package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * รายงานข้อความที่แอพแยกประเภทผิด
 * ใช้เพื่อเก็บข้อมูลสำหรับวิเคราะห์และปรับปรุง Parser
 */
@Entity(tableName = "misclassification_reports")
data class MisclassificationReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // ข้อมูลต้นฉบับจาก Transaction
    val transactionId: Long,
    val bank: String,
    val rawMessage: String,
    val senderAddress: String,
    val timestamp: Long,

    // ผลลัพธ์ที่แอพตีความไว้
    val detectedType: TransactionType,
    val detectedAmount: String,

    // ประเภทปัญหาที่พบ
    val issueType: MisclassificationIssueType,

    // ข้อมูลที่ถูกต้อง (ถ้าผู้ใช้ระบุ)
    val correctType: TransactionType? = null,
    val correctAmount: String? = null,

    // สถานะการวิเคราะห์
    val isAnalyzed: Boolean = false,
    val analyzerNotes: String? = null,
    val isFixed: Boolean = false,

    // Metadata
    val reportedAt: Long = System.currentTimeMillis(),
    val deviceId: String? = null,
    val appVersion: String? = null
)

/**
 * ประเภทปัญหาที่พบ
 */
enum class MisclassificationIssueType {
    WRONG_TRANSACTION_TYPE,    // แอพคิดว่าเป็นเงินเข้า แต่จริงๆ เป็นเงินออก (หรือตรงกันข้าม)
    WRONG_AMOUNT,              // แอพแยกจำนวนเงินผิด
    NOT_A_TRANSACTION,         // ข้อความนี้ไม่ใช่รายการธุรกรรม แต่แอพจับได้
    CANNOT_PARSE,              // แอพจับไม่ได้เลย แต่จริงๆ เป็นรายการธุรกรรม
    OTHER                      // อื่นๆ
}
