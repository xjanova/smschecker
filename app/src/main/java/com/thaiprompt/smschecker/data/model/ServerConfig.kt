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
    val approvalMode: String = "auto", // auto, manual, smart — synced from server per-device
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // 🌐 (2026-09-15) ชื่อเว็บที่เซิร์ฟบอกเอง (GET device-settings: server_name → website_name)
    //   null = เซิร์ฟยังไม่ส่งมา → ใช้ name (ชื่อที่ตั้งในเครื่อง) แทน — ดู displayName()
    val siteName: String? = null
) {
    /** ชื่อที่ใช้แสดงทุกที่ที่แอพบอกว่า "เซิร์ฟ/เว็บไหน": ชื่อจากเซิร์ฟก่อน แล้วค่อยชื่อในเครื่อง */
    fun displayName(): String = siteName?.trim()?.takeIf { it.isNotEmpty() } ?: name

    fun getEndpointUrl(): String {
        val url = baseUrl.trimEnd('/')
        return "$url/api/v1/sms-payment/notify"
    }

    fun getStatusUrl(): String {
        val url = baseUrl.trimEnd('/')
        return "$url/api/v1/sms-payment/status"
    }
}
