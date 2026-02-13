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
    fun toTransactionType(value: String): TransactionType {
        return try {
            TransactionType.valueOf(value)
        } catch (e: Exception) {
            TransactionType.DEBIT
        }
    }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: Exception) {
            SyncStatus.PENDING
        }
    }

    @TypeConverter
    fun fromApprovalStatus(value: ApprovalStatus): String = value.name

    @TypeConverter
    fun toApprovalStatus(value: String): ApprovalStatus {
        return try {
            ApprovalStatus.valueOf(value)
        } catch (e: Exception) {
            ApprovalStatus.PENDING_REVIEW
        }
    }

    @TypeConverter
    fun fromMatchConfidence(value: MatchConfidence): String = value.name

    @TypeConverter
    fun toMatchConfidence(value: String): MatchConfidence {
        return try {
            MatchConfidence.valueOf(value)
        } catch (e: Exception) {
            MatchConfidence.HIGH
        }
    }

    @TypeConverter
    fun fromPendingAction(value: PendingAction?): String? = value?.name

    @TypeConverter
    fun toPendingAction(value: String?): PendingAction? {
        if (value == null) return null
        return try {
            PendingAction.valueOf(value)
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource {
        return try {
            TransactionSource.valueOf(value)
        } catch (e: Exception) {
            TransactionSource.SMS
        }
    }
}
