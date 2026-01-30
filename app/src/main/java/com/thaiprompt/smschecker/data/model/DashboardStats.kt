package com.thaiprompt.smschecker.data.model

data class DashboardStats(
    val totalOrders: Int = 0,
    val autoApproved: Int = 0,
    val manuallyApproved: Int = 0,
    val pendingReview: Int = 0,
    val rejected: Int = 0,
    val dailyBreakdown: List<DailyStats> = emptyList(),
    val totalAmount: Double = 0.0
)

data class DailyStats(
    val date: String,
    val count: Int,
    val approved: Int,
    val rejected: Int,
    val amount: Double
)
