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
                // Create order_approvals table if it doesn't exist
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS order_approvals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId INTEGER NOT NULL,
                        remoteApprovalId INTEGER NOT NULL,
                        notificationId INTEGER,
                        matchedTransactionId INTEGER,
                        approvalStatus TEXT NOT NULL DEFAULT 'PENDING_REVIEW',
                        confidence TEXT NOT NULL DEFAULT 'HIGH',
                        orderNumber TEXT,
                        productName TEXT,
                        productDetails TEXT,
                        quantity INTEGER,
                        websiteName TEXT,
                        customerName TEXT,
                        amount REAL NOT NULL DEFAULT 0.0,
                        bank TEXT,
                        paymentTimestamp INTEGER,
                        approvedBy TEXT,
                        approvedAt INTEGER,
                        rejectedAt INTEGER,
                        rejectionReason TEXT,
                        syncedVersion INTEGER NOT NULL DEFAULT 0,
                        lastSyncedAt INTEGER,
                        pendingAction TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(serverId) REFERENCES server_configs(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_approvals_serverId ON order_approvals(serverId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_approvals_approvalStatus ON order_approvals(approvalStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_approvals_syncedVersion ON order_approvals(syncedVersion)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_order_approvals_remoteApprovalId_serverId ON order_approvals(remoteApprovalId, serverId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration from version 2 to 3
                // Add sms_sender_rules table if needed
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sms_sender_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        senderAddress TEXT NOT NULL,
                        senderName TEXT,
                        bankCode TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sms_sender_rules_senderAddress ON sms_sender_rules(senderAddress)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if column exists before adding
                val cursor = db.query("PRAGMA table_info(bank_transactions)")
                var hasSourceType = false
                val nameColumnIndex = cursor.getColumnIndex("name")
                if (nameColumnIndex >= 0) {
                    while (cursor.moveToNext()) {
                        val columnName = cursor.getString(nameColumnIndex)
                        if (columnName == "sourceType") {
                            hasSourceType = true
                            break
                        }
                    }
                }
                cursor.close()

                if (!hasSourceType) {
                    db.execSQL("ALTER TABLE bank_transactions ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'SMS'")
                }
            }
        }
    }
}
