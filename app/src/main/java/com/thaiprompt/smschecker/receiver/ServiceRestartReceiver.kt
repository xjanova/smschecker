package com.thaiprompt.smschecker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.thaiprompt.smschecker.service.RealtimeSyncService

/**
 * Triggered by AlarmManager.setExactAndAllowWhileIdle after the user swipes the task away
 * (see RealtimeSyncService.onTaskRemoved). Restarts RealtimeSyncService even while device
 * is in Doze mode.
 *
 * Using a BroadcastReceiver rather than PendingIntent.getService() because
 * setExactAndAllowWhileIdle() only wakes the device long enough for a broadcast; a direct
 * service start can be denied on API 31+ when device is idle.
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
        const val ACTION_RESTART = "com.thaiprompt.smschecker.action.RESTART_SYNC_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RESTART) return
        Log.i(TAG, "Received restart alarm — starting RealtimeSyncService")

        try {
            RealtimeSyncService.start(context.applicationContext, fromBoot = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart RealtimeSyncService from alarm", e)
        }
    }
}
