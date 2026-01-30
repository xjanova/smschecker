package com.thaiprompt.smschecker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.model.SyncLog

@Database(
    entities = [
        BankTransaction::class,
        ServerConfig::class,
        SyncLog::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun syncLogDao(): SyncLogDao
}
