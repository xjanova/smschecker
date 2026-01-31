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
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromApprovalStatus(value: ApprovalStatus): String = value.name

    @TypeConverter
    fun toApprovalStatus(value: String): ApprovalStatus = ApprovalStatus.valueOf(value)

    @TypeConverter
    fun fromMatchConfidence(value: MatchConfidence): String = value.name

    @TypeConverter
    fun toMatchConfidence(value: String): MatchConfidence = MatchConfidence.valueOf(value)

    @TypeConverter
    fun fromPendingAction(value: PendingAction?): String? = value?.name

    @TypeConverter
    fun toPendingAction(value: String?): PendingAction? = value?.let { PendingAction.valueOf(it) }

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource = TransactionSource.valueOf(value)
}
