package com.thaiprompt.smschecker

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmsCheckerApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "sms_processing"
        const val NOTIFICATION_CHANNEL_TRANSACTION = "transaction_alerts"
        private const val TAG = "SmsCheckerApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Global crash handler to log unhandled exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(processingChannel)
            manager.createNotificationChannel(transactionChannel)
        }
    }
}
