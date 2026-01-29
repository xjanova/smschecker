package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val serverId: Long,
    val serverName: String,
    val status: SyncStatus,
    val httpStatusCode: Int? = null,
    val responseBody: String? = null,
    val errorMessage: String? = null,
    val sentAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null
)

enum class SyncStatus {
    PENDING,
    SENDING,
    SUCCESS,
    FAILED,
    TIMEOUT
}
