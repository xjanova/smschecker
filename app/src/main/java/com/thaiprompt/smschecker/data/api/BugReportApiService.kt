package com.thaiprompt.smschecker.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API service for submitting bug reports and misclassification reports
 * to xmanstudio backend (https://xman4289.com)
 */
interface BugReportApiService {

    @POST("v1/bug-reports")
    suspend fun submitReport(
        @Body report: BugReportRequest
    ): Response<BugReportResponse>

    @POST("v1/bug-reports/batch")
    suspend fun submitBatchReports(
        @Body request: BatchBugReportRequest
    ): Response<BatchBugReportResponse>
}

// ============================================================================
// Request Models
// ============================================================================

data class BugReportRequest(
    @SerializedName("product_name")
    val productName: String = "smschecker",

    @SerializedName("product_version")
    val productVersion: String? = null,

    @SerializedName("report_type")
    val reportType: String, // "misclassification", "bug", "crash", "performance", "feature_request"

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("metadata")
    val metadata: Map<String, Any>,

    @SerializedName("device_id")
    val deviceId: String? = null,

    @SerializedName("user_email")
    val userEmail: String? = null,

    @SerializedName("priority")
    val priority: String = "medium", // "low", "medium", "high", "critical"

    @SerializedName("severity")
    val severity: String = "moderate", // "minor", "moderate", "major", "critical"

    @SerializedName("os_version")
    val osVersion: String? = null,

    @SerializedName("app_version")
    val appVersion: String? = null,

    @SerializedName("stack_trace")
    val stackTrace: String? = null,

    @SerializedName("additional_info")
    val additionalInfo: Map<String, Any>? = null
)

data class BatchBugReportRequest(
    @SerializedName("reports")
    val reports: List<BugReportRequest>
)

// ============================================================================
// Response Models
// ============================================================================

data class BugReportResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("data")
    val data: BugReportData? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("errors")
    val errors: Map<String, List<String>>? = null
)

data class BugReportData(
    @SerializedName("id")
    val id: Long,

    @SerializedName("status")
    val status: String,

    @SerializedName("github_issue_url")
    val githubIssueUrl: String? = null
)

data class BatchBugReportResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("data")
    val data: BatchBugReportData? = null,

    @SerializedName("message")
    val message: String? = null
)

data class BatchBugReportData(
    @SerializedName("created_count")
    val createdCount: Int,

    @SerializedName("failed_count")
    val failedCount: Int,

    @SerializedName("created_ids")
    val createdIds: List<Long>,

    @SerializedName("failed_reports")
    val failedReports: List<FailedReport>
)

data class FailedReport(
    @SerializedName("title")
    val title: String,

    @SerializedName("error")
    val error: String
)
