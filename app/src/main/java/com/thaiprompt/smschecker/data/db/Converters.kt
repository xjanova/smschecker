package com.thaiprompt.smschecker.data.db

import androidx.room.TypeConverter
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.MatchConfidence
import com.thaiprompt.smschecker.data.model.PendingAction
import com.thaiprompt.smschecker.data.model.SyncStatus
import com.thaiprompt.smschecker.data.model.TransactionSource
import com.thaiprompt.smschecker.data.model.TransactionType

class Converters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = try {
        TransactionType.valueOf(value)
    } catch (_: Exception) {
        TransactionType.CREDIT
    }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = try {
        SyncStatus.valueOf(value)
    } catch (_: Exception) {
        SyncStatus.PENDING
    }

    @TypeConverter
    fun fromApprovalStatus(value: ApprovalStatus): String = value.name

    @TypeConverter
    fun toApprovalStatus(value: String): ApprovalStatus = try {
        ApprovalStatus.valueOf(value)
    } catch (_: Exception) {
        ApprovalStatus.PENDING_REVIEW
    }

    @TypeConverter
    fun fromMatchConfidence(value: MatchConfidence): String = value.name

    @TypeConverter
    fun toMatchConfidence(value: String): MatchConfidence = try {
        MatchConfidence.valueOf(value)
    } catch (_: Exception) {
        MatchConfidence.HIGH
    }

    @TypeConverter
    fun fromPendingAction(value: PendingAction?): String? = value?.name

    @TypeConverter
    fun toPendingAction(value: String?): PendingAction? = value?.let {
        try { PendingAction.valueOf(it) } catch (_: Exception) { null }
    }

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource = try {
        TransactionSource.valueOf(value)
    } catch (_: Exception) {
        TransactionSource.SMS
    }
}
