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

    // --- Order Approval Endpoints ---

    @GET("api/v1/sms-payment/orders")
    suspend fun getOrders(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Query("status") status: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<OrdersResponse>

    @POST("api/v1/sms-payment/orders/{id}/approve")
    suspend fun approveOrder(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Path("id") orderId: Long
    ): Response<ApiResponse>

    @POST("api/v1/sms-payment/orders/{id}/reject")
    suspend fun rejectOrder(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Path("id") orderId: Long,
        @Body body: RejectBody
    ): Response<ApiResponse>

    @POST("api/v1/sms-payment/orders/bulk-approve")
    suspend fun bulkApproveOrders(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Body body: BulkApproveBody
    ): Response<ApiResponse>

    @GET("api/v1/sms-payment/orders/sync")
    suspend fun syncOrders(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Query("since_version") sinceVersion: Long = 0
    ): Response<SyncResponse>

    /**
     * Match-only mode: Find order by SMS amount.
     * Called when SMS is received instead of fetching all orders.
     * Returns only the order that matches the exact amount (unique decimal).
     */
    @GET("api/v1/sms-payment/orders/match")
    suspend fun matchOrderByAmount(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Query("amount") amount: String
    ): Response<MatchOrderResponse>

    @GET("api/v1/sms-payment/device-settings")
    suspend fun getDeviceSettings(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String
    ): Response<DeviceSettingsResponse>

    @PUT("api/v1/sms-payment/device-settings")
    suspend fun updateDeviceSettings(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Body body: UpdateSettingsBody
    ): Response<ApiResponse>

    @GET("api/v1/sms-payment/dashboard-stats")
    suspend fun getDashboardStats(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Query("days") days: Int = 7
    ): Response<DashboardStatsResponse>
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
    val app_version: String,
    val fcm_token: String? = null
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

// --- Order Approval Data Classes ---

data class OrdersResponse(
    val success: Boolean,
    val data: PaginatedOrders? = null
)

data class PaginatedOrders(
    val data: List<RemoteOrderApproval> = emptyList(),
    val current_page: Int = 1,
    val last_page: Int = 1,
    val total: Int = 0
)

data class RemoteOrderApproval(
    val id: Long,
    val notification_id: Long? = null,
    val matched_transaction_id: Long? = null,
    val device_id: String? = null,
    val approval_status: String,
    val confidence: String = "high",
    val approved_by: String? = null,
    val approved_at: String? = null,
    val rejected_at: String? = null,
    val rejection_reason: String? = null,
    val order_details_json: Map<String, Any?>? = null,
    val server_name: String? = null,
    val synced_version: Long = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
    val notification: RemoteNotification? = null
)

data class RemoteNotification(
    val id: Long,
    val bank: String? = null,
    val type: String? = null,
    val amount: String? = null,
    val sms_timestamp: String? = null,
    val sender_or_receiver: String? = null
)

data class RejectBody(val reason: String = "")

data class BulkApproveBody(val ids: List<Long>)

data class UpdateSettingsBody(val approval_mode: String)

data class SyncResponse(
    val success: Boolean,
    val data: SyncData? = null
)

data class SyncData(
    val orders: List<RemoteOrderApproval> = emptyList(),
    val latest_version: Long = 0
)

data class DeviceSettingsResponse(
    val success: Boolean,
    val data: DeviceSettings? = null
)

data class DeviceSettings(
    val approval_mode: String = "auto"
)

data class DashboardStatsResponse(
    val success: Boolean,
    val data: RemoteDashboardStats? = null
)

data class RemoteDashboardStats(
    val total_orders: Int = 0,
    val auto_approved: Int = 0,
    val manually_approved: Int = 0,
    val pending_review: Int = 0,
    val rejected: Int = 0,
    val total_amount: Double = 0.0,
    val daily_breakdown: List<RemoteDailyStats> = emptyList()
)

data class RemoteDailyStats(
    val date: String,
    val count: Int = 0,
    val approved: Int = 0,
    val rejected: Int = 0,
    val amount: Double = 0.0
)

// --- Match Order Response (for match-only mode) ---

data class MatchOrderResponse(
    val success: Boolean,
    val data: MatchOrderData? = null
)

data class MatchOrderData(
    val matched: Boolean = false,
    val order: RemoteOrderApproval? = null,
    val message: String? = null
)
