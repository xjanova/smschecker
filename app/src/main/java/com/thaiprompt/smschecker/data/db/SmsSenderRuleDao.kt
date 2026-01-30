package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsSenderRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SmsSenderRule): Long

    @Update
    suspend fun update(rule: SmsSenderRule)

    @Delete
    suspend fun delete(rule: SmsSenderRule)

    @Query("SELECT * FROM sms_sender_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<SmsSenderRule>>

    @Query("SELECT * FROM sms_sender_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<SmsSenderRule>

    @Query("SELECT * FROM sms_sender_rules WHERE senderAddress = :address AND isActive = 1 LIMIT 1")
    suspend fun findRuleBySender(address: String): SmsSenderRule?
}
