package com.thaiprompt.smschecker

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.thaiprompt.smschecker.service.FcmService
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
        initializeFirebaseMessaging()
    }

    /**
     * ดึง FCM token เริ่มต้นและบันทึกไว้ เพื่อส่งไปเซิร์ฟเวอร์ตอน sync
     */
    private fun initializeFirebaseMessaging() {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                Log.d(TAG, "FCM token obtained")
                val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                val existingToken = prefs.getString(FcmService.FCM_TOKEN_KEY, null)

                if (existingToken != token) {
                    prefs.edit()
                        .putString(FcmService.FCM_TOKEN_KEY, token)
                        .putBoolean("fcm_token_needs_sync", true)
                        .apply()
                }
            }.addOnFailureListener { e ->
                Log.w(TAG, "Failed to get FCM token: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not available: ${e.message}")
        }
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
