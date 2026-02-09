package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_configs")
data class ServerConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // Display name (e.g., "Thaiprompt Main")
    val baseUrl: String,        // https://example.com
    val apiKey: String,         // Encrypted API key
    val secretKey: String,      // Encrypted secret for HMAC
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val syncInterval: Int = 300,  // Sync interval in seconds (default 5min - FCM push is primary mechanism)
    val lastSyncAt: Long? = null,
    val lastSyncStatus: String? = null, // success, failed, timeout
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getEndpointUrl(): String {
        val url = baseUrl.trimEnd('/')
        return "$url/api/v1/sms-payment/notify"
    }

    fun getStatusUrl(): String {
        val url = baseUrl.trimEnd('/')
        return "$url/api/v1/sms-payment/status"
    }
}
