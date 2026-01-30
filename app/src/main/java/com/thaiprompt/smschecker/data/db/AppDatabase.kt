package com.thaiprompt.smschecker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.model.SyncLog

@Database(
    entities = [
        BankTransaction::class,
        ServerConfig::class,
        SyncLog::class,
        OrderApproval::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun orderApprovalDao(): OrderApprovalDao
}
