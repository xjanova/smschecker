package com.thaiprompt.smschecker.data.api

import retrofit2.Response
import retrofit2.http.*

interface PaymentApiService {

    @POST("api/v1/sms-payment/notify")
    suspend fun notifyTransaction(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Signature") signature: String,
        @Header("X-Nonce") nonce: String,
        @Header("X-Timestamp") timestamp: String,
        @Header("X-Device-Id") deviceId: String,
        @Body body: EncryptedPayload
    ): Response<ApiResponse>

    @GET("api/v1/sms-payment/status")
    suspend fun checkStatus(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String
    ): Response<StatusResponse>

    @POST("api/v1/sms-payment/register-device")
    suspend fun registerDevice(
        @Header("X-Api-Key") apiKey: String,
        @Body body: DeviceRegistration
    ): Response<ApiResponse>
}

data class EncryptedPayload(
    val data: String,      // AES-256-GCM encrypted JSON
    val iv: String? = null  // IV is included in data for GCM
)

data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null,
    val transaction_id: String? = null
)

data class StatusResponse(
    val success: Boolean,
    val status: String,      // active, inactive, blocked
    val pending_count: Int,
    val message: String? = null
)

data class DeviceRegistration(
    val device_id: String,
    val device_name: String,
    val platform: String = "android",
    val app_version: String
)

/**
 * The unencrypted transaction payload (encrypted before sending)
 */
data class TransactionPayload(
    val bank: String,
    val type: String,           // credit, debit
    val amount: String,         // "500.37"
    val account_number: String,
    val sender_or_receiver: String,
    val reference_number: String,
    val sms_timestamp: Long,
    val device_id: String,
    val nonce: String
)
