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
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getAmountAsBigDecimal(): BigDecimal = BigDecimal(amount)

    fun getFormattedAmount(): String {
        val bd = getAmountAsBigDecimal()
        val prefix = if (type == TransactionType.CREDIT) "+" else "-"
        return "$prefix฿${String.format("%,.2f", bd)}"
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
