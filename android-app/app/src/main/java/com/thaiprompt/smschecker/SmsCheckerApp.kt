package com.thaiprompt.smschecker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmsCheckerApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "sms_processing"
        const val NOTIFICATION_CHANNEL_TRANSACTION = "transaction_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val processingChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }

            val transactionChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_TRANSACTION,
                "Transaction Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming/outgoing bank transactions"
                enableVibration(true)
            }

            manager.createNotificationChannel(processingChannel)
            manager.createNotificationChannel(transactionChannel)
        }
    }
}
