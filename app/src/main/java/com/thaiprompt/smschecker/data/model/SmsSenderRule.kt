package com.thaiprompt.smschecker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_sender_rules")
data class SmsSenderRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderAddress: String,
    val bankCode: String,
    val sampleMessage: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
