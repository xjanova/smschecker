package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_approvals",
    foreignKeys = [
        ForeignKey(
            entity = ServerConfig::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("serverId"),
        Index("approvalStatus"),
        Index("syncedVersion"),
        Index(value = ["remoteApprovalId", "serverId"], unique = true)
    ]
)
data class OrderApproval(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long,
    val remoteApprovalId: Long,
    val notificationId: Long? = null,
    val matchedTransactionId: Long? = null,
    val approvalStatus: ApprovalStatus = ApprovalStatus.PENDING_REVIEW,
    val confidence: MatchConfidence = MatchConfidence.HIGH,
    val orderNumber: String? = null,
    val productName: String? = null,
    val productDetails: String? = null,
    val quantity: Int? = null,
    val websiteName: String? = null,
    val customerName: String? = null,
    val amount: Double = 0.0,
    val bank: String? = null,
    val paymentTimestamp: Long? = null,
    val serverName: String? = null,
    val deviceId: String? = null,
    val approvedBy: String? = null,
    val approvedAt: Long? = null,
    val rejectedAt: Long? = null,
    val rejectionReason: String? = null,
    val syncedVersion: Long = 0,
    val lastSyncedAt: Long? = null,
    val pendingAction: PendingAction? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ApprovalStatus(val apiValue: String, val displayName: String) {
    PENDING_REVIEW("pending_review", "Pending"),
    AUTO_APPROVED("auto_approved", "Auto Approved"),
    MANUALLY_APPROVED("manually_approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    EXPIRED("expired", "Expired"),
    CANCELLED("cancelled", "Cancelled"),
    DELETED("deleted", "Deleted");

    companion object {
        fun fromApiValue(value: String): ApprovalStatus {
            return entries.find { it.apiValue == value } ?: PENDING_REVIEW
        }
    }
}

enum class MatchConfidence(val apiValue: String) {
    HIGH("high"),
    AMBIGUOUS("ambiguous");

    companion object {
        fun fromApiValue(value: String): MatchConfidence {
            return entries.find { it.apiValue == value } ?: HIGH
        }
    }
}

enum class PendingAction {
    APPROVE,
    REJECT
}
