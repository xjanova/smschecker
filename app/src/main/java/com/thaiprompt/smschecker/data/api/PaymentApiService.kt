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

    /**
     * Send encrypted action (approve/reject) to server.
     * Uses the same security model as notifyTransaction (AES-256-GCM + HMAC-SHA256).
     * Server decrypts and executes approve/reject, returns updated order data.
     */
    @POST("api/v1/sms-payment/notify-action")
    suspend fun notifyAction(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Signature") signature: String,
        @Header("X-Nonce") nonce: String,
        @Header("X-Timestamp") timestamp: String,
        @Header("X-Device-Id") deviceId: String,
        @Body body: EncryptedPayload
    ): Response<ActionResponse>

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
        @Path("id") identifier: String
    ): Response<ApiResponse>

    @POST("api/v1/sms-payment/orders/{id}/reject")
    suspend fun rejectOrder(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Path("id") identifier: String,
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

    /**
     * Debug report: Send diagnostic data to server for debugging.
     * Temporary endpoint for FCM token registration debugging.
     */
    @POST("api/v1/sms-payment/debug-report")
    suspend fun debugReport(
        @Header("X-Api-Key") apiKey: String,
        @Body body: DebugReportBody
    ): Response<ApiResponse>

    /**
     * 🔍 (2026-05-21) Find bill candidates for orphan SMS
     *
     * Use case: ลูกค้าโอนเลขกลม → SMS เป็น orphan → admin หาบิลตรงกัน
     * Backend จะค้นบิล pending ที่ amount >= base_price + name fuzzy + time window
     */
    @POST("api/v1/sms-payment/orphans/find-bill-candidates")
    suspend fun findBillCandidatesForOrphan(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Body body: OrphanFindBillBody
    ): Response<OrphanBillCandidatesResponse>

    /**
     * ✅ (2026-05-21) Admin confirm: ผูก SMS เข้าบิล + approve + dispatch
     */
    @POST("api/v1/sms-payment/orphans/confirm-match")
    suspend fun confirmOrphanMatch(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Body body: OrphanConfirmMatchBody
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
    // 🏷️ (2026-05-25) Server now sends cancellation_reason + cancellation_reason_label
    //   ตั้งแต่ Thaiprompt-Affiliate commit a4f9ee76a (SmsPaymentController.transformFortuneReadingToOrderApproval)
    //   - cancellation_reason       = enum key (auto_expired / auto_expired_grace / user_cancelled / unknown)
    //   - cancellation_reason_label = Thai display text ("ยกเลิกโดยระบบ" / "ยกเลิกโดยลูกค้า")
    //   ทั้งคู่จะเป็น null ถ้า order ไม่ใช่ cancelled
    val cancellation_reason: String? = null,
    val cancellation_reason_label: String? = null,
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

data class BulkApproveBody(val ids: List<String>)

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

// --- Encrypted Action (approve/reject) Response ---

data class ActionResponse(
    val success: Boolean,
    val message: String,
    val data: ActionResponseData? = null
)

data class ActionResponseData(
    val order: RemoteOrderApproval? = null
)

/**
 * The unencrypted action payload (encrypted before sending).
 * Used for approve/reject actions via notify-action endpoint.
 */
data class ActionPayload(
    val action: String,             // "approve" or "reject"
    val order_identifier: String,   // order_number or numeric ID
    val amount: Double,             // verified amount
    val bank: String?,              // bank name (if known)
    val sms_reference: String?,     // SMS reference number (if available)
    val device_id: String,
    val reason: String?,            // rejection reason (for reject)
    val nonce: String,
    // 🚀 (2026-05-21) Force approve flag — bypass SMS matching ที่ฝั่ง backend
    //   เคสใช้: ลูกค้าโอนยอดผิด / SMS หาย / UPA mismatch
    //   Backend executeFortuneApproveAction:2670 — ถ้า force=true → ไม่ require valid SMS
    //   Log critical audit ที่ backend ทุกครั้ง (admin_action='force_approve_no_sms')
    val force: Boolean = false
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

/**
 * Debug report body for sending diagnostic data to server.
 * Temporary - remove after FCM debugging is complete.
 */
data class DebugReportBody(
    val device_id: String?,
    val app_version: String?,
    val fcm_token_length: Int,
    val fcm_token_prefix: String?,
    val fcm_needs_sync: Boolean,
    val active_servers_count: Int,
    val servers_with_api_keys: Int,
    val register_fcm_result: String?,
    val build_number: Int,
    val device_model: String?,
    val timestamp: Long
)

// =================================================================
// 🔍 (2026-05-21) Orphan SMS → Bill Match (admin-side fuzzy)
// =================================================================

data class OrphanFindBillBody(
    val amount: Double,
    val sender_name: String?,
    val sms_timestamp: String,   // ISO 8601
    val window_hours: Int? = 24
)

data class OrphanBillCandidatesResponse(
    val success: Boolean,
    val data: OrphanBillCandidatesData? = null,
    val message: String? = null
)

data class OrphanBillCandidatesData(
    val candidates: List<BillCandidate> = emptyList(),
    val count: Int = 0
)

data class BillCandidate(
    val bill_reference: String,
    val reading_id: Long,
    val reading_type: String,         // "deep" / "celtic_cross"
    val customer_name: String?,
    val expected_amount: Double,      // ยอดที่บิลต้องจ่าย (unique_amount with decimals)
    val base_price: Double,           // ราคาเต็ม (39, 99)
    val name_score: Int,              // 0-100
    val time_delta_minutes: Int,
    val amount_delta: Double,         // ลูกค้าโอนเกินกี่บาท
    val bill_created_at: String?,
    val platform: String?
)

data class OrphanConfirmMatchBody(
    val bill_reference: String,
    val sms_notification_id: Long? = null,
    // 🤖 (2026-05-21) Smart mode flag — set true เมื่อ app auto-match ไม่รอ admin
    //   ใช้ใน backend audit log: แยก "admin manual click" vs "smart auto"
    val auto_smart: Boolean = false
)
