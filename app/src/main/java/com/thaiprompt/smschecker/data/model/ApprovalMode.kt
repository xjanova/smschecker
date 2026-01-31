package com.thaiprompt.smschecker.data.model

enum class ApprovalMode(
    val apiValue: String,
    val displayNameTh: String,
    val descriptionTh: String,
    val displayNameEn: String,
    val descriptionEn: String
) {
    AUTO(
        "auto",
        "อนุมัติอัตโนมัติ", "รายการชำระเงินที่ตรงกันจะถูกอนุมัติอัตโนมัติ",
        "Auto Approve", "All matched payments are approved automatically"
    ),
    MANUAL(
        "manual",
        "ตรวจสอบด้วยตนเอง", "รายการชำระเงินที่ตรงกันต้องอนุมัติด้วยตนเอง",
        "Manual Review", "All matched payments require manual approval"
    ),
    SMART(
        "smart",
        "โหมดอัจฉริยะ", "อนุมัติอัตโนมัติเมื่อตรงกันชัดเจน กรณีไม่ชัดเจนรอตรวจสอบ",
        "Smart Mode", "Auto-approve confident matches, flag ambiguous ones for review"
    );

    // Legacy properties for backward compatibility
    val displayName: String get() = displayNameEn
    val description: String get() = descriptionEn

    companion object {
        fun fromApiValue(value: String): ApprovalMode {
            return entries.find { it.apiValue == value } ?: AUTO
        }
    }
}
