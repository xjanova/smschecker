package com.thaiprompt.smschecker.data.model

enum class ApprovalMode(
    val apiValue: String,
    val displayName: String,
    val description: String
) {
    AUTO("auto", "Auto Approve", "All matched payments are approved automatically"),
    MANUAL("manual", "Manual Review", "All matched payments require manual approval"),
    SMART("smart", "Smart Mode", "Auto-approve confident matches, flag ambiguous ones for review");

    companion object {
        fun fromApiValue(value: String): ApprovalMode {
            return entries.find { it.apiValue == value } ?: AUTO
        }
    }
}
