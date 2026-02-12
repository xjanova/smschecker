package com.thaiprompt.smschecker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.MatchHistory
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.OrphanTransaction
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import com.thaiprompt.smschecker.data.model.SyncLog

@Database(
    entities = [
        BankTransaction::class,
        ServerConfig::class,
        SyncLog::class,
        OrderApproval::class,
        SmsSenderRule::class,
        OrphanTransaction::class,
        MatchHistory::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun orderApprovalDao(): OrderApprovalDao
    abstract fun smsSenderRuleDao(): SmsSenderRuleDao
    abstract fun orphanTransactionDao(): OrphanTransactionDao
    abstract fun matchHistoryDao(): MatchHistoryDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create orphan_transactions table for recovering unmatched decimal payments
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS orphan_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        amountString TEXT NOT NULL,
                        bank TEXT NOT NULL,
                        accountNumber TEXT NOT NULL DEFAULT '',
                        senderOrReceiver TEXT NOT NULL DEFAULT '',
                        referenceNumber TEXT NOT NULL DEFAULT '',
                        rawMessage TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        matchAttempts INTEGER NOT NULL DEFAULT 0,
                        lastMatchAttemptAt INTEGER,
                        matchedOrderId INTEGER,
                        matchedServerId INTEGER,
                        matchedAt INTEGER,
                        transactionTimestamp INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'SMS',
                        notes TEXT
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orphan_transactions_amount ON orphan_transactions(amount)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orphan_transactions_bank ON orphan_transactions(bank)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orphan_transactions_status ON orphan_transactions(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orphan_transactions_createdAt ON orphan_transactions(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orphan_transactions_amount_bank_status ON orphan_transactions(amount, bank, status)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add syncInterval column to server_configs table (default 5 seconds)
                db.execSQL("ALTER TABLE server_configs ADD COLUMN syncInterval INTEGER NOT NULL DEFAULT 5")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create match_history table for tracking successful matches
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS match_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL,
                        amountString TEXT NOT NULL,
                        bank TEXT NOT NULL,
                        transactionTimestamp INTEGER NOT NULL,
                        serverId INTEGER NOT NULL,
                        serverName TEXT NOT NULL,
                        orderNumber TEXT,
                        remoteOrderId INTEGER NOT NULL,
                        serverQueriesCount INTEGER NOT NULL,
                        totalServersQueried INTEGER NOT NULL,
                        matchDurationMs INTEGER NOT NULL,
                        matchResult TEXT NOT NULL DEFAULT 'SUCCESS',
                        approvalResult TEXT NOT NULL DEFAULT 'AUTO_APPROVED',
                        matchedAt INTEGER NOT NULL,
                        approvedAt INTEGER
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_history_amount ON match_history(amount)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_history_matchedAt ON match_history(matchedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_history_serverId ON match_history(serverId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // เพิ่มคอลัมน์ serverName ใน order_approvals เพื่อแสดงว่าบิลมาจากเซิร์ฟไหน
                db.execSQL("ALTER TABLE order_approvals ADD COLUMN serverName TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // อัพเดท syncInterval จาก polling ถี่ (5-60 วินาที) เป็น 5 นาที
                // เพราะ FCM push เป็นกลไกหลักแล้ว ไม่ต้อง poll ถี่
                db.execSQL("UPDATE server_configs SET syncInterval = 300 WHERE syncInterval < 60")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // เพิ่ม approvalMode per-server (auto/manual/smart) — synced from server
                db.execSQL("ALTER TABLE server_configs ADD COLUMN approvalMode TEXT NOT NULL DEFAULT 'auto'")
            }
        }

    }
}
