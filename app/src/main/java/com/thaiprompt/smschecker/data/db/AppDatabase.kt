package com.thaiprompt.smschecker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import com.thaiprompt.smschecker.data.model.SyncLog

@Database(
    entities = [
        BankTransaction::class,
        ServerConfig::class,
        SyncLog::class,
        OrderApproval::class,
        SmsSenderRule::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun orderApprovalDao(): OrderApprovalDao
    abstract fun smsSenderRuleDao(): SmsSenderRuleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration from version 1 to 2
                // Add any schema changes needed
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration from version 2 to 3
                // Add any schema changes needed
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bank_transactions ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'SMS'")
            }
        }
    }
}
