package com.thaiprompt.smschecker.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.thaiprompt.smschecker.data.api.ApiClientFactory
import com.thaiprompt.smschecker.data.api.WebSocketManager
import com.thaiprompt.smschecker.data.db.AppDatabase
import com.thaiprompt.smschecker.data.db.MatchHistoryDao
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
import com.thaiprompt.smschecker.data.db.OrphanTransactionDao
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.db.SmsSenderRuleDao
import com.thaiprompt.smschecker.data.db.SyncLogDao
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.domain.parser.BankSmsParser
import com.thaiprompt.smschecker.security.CryptoManager
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smschecker_database"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideServerConfigDao(db: AppDatabase): ServerConfigDao = db.serverConfigDao()

    @Provides
    fun provideSyncLogDao(db: AppDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    fun provideOrderApprovalDao(db: AppDatabase): OrderApprovalDao = db.orderApprovalDao()

    @Provides
    fun provideSmsSenderRuleDao(db: AppDatabase): SmsSenderRuleDao = db.smsSenderRuleDao()

    @Provides
    fun provideOrphanTransactionDao(db: AppDatabase): OrphanTransactionDao = db.orphanTransactionDao()

    @Provides
    fun provideMatchHistoryDao(db: AppDatabase): MatchHistoryDao = db.matchHistoryDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideWebSocketManager(
        @ApplicationContext context: Context,
        serverConfigDao: ServerConfigDao,
        secureStorage: SecureStorage,
        gson: Gson
    ): WebSocketManager = WebSocketManager(context, serverConfigDao, secureStorage, gson)

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    @Provides
    @Singleton
    fun provideApiClientFactory(): ApiClientFactory = ApiClientFactory()

    @Provides
    @Singleton
    fun provideBankSmsParser(): BankSmsParser = BankSmsParser()
}
