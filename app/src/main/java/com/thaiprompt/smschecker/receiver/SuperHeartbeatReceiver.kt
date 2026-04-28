package com.thaiprompt.smschecker.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.thaiprompt.smschecker.data.license.LicenseManager
import com.thaiprompt.smschecker.service.RealtimeSyncService

/**
 * Super Mode heartbeat — fired by AlarmManager.setExactAndAllowWhileIdle every ~2 minutes
 * to wake the device out of Doze and force a service revive + WS reconnect.
 *
 * Why a BroadcastReceiver (not getService directly):
 *   setExactAndAllowWhileIdle on API 31+ only wakes the device long enough to deliver a
 *   broadcast — a direct service start can be denied while idle. The broadcast wakes us,
 *   we acquire a short wake lock, then call startForegroundService() ourselves.
 *
 * Rate-limit caveat: without battery-optimization exemption, Android limits this alarm to
 * ~once every 9-15 min during Doze. Super Mode UI must enforce the exemption popup.
 */
class SuperHeartbeatReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SuperHeartbeat"
        const val ACTION_HEARTBEAT = "com.thaiprompt.smschecker.action.SUPER_HEARTBEAT"
        private const val WAKELOCK_TAG = "SmsChecker:SuperHeartbeatWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 30_000L
        private const val PI_REQUEST_CODE = 4242

        // 2 minutes — primary Super Mode interval.
        // Note: OS clamps to ~9-15 min if app NOT battery-exempt.
        const val INTERVAL_MS = 2 * 60 * 1000L

        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + INTERVAL_MS
            val pi = pendingIntent(context)

            val useExact = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    try { am.canScheduleExactAlarms() } catch (_: Exception) { false }
                else -> true
            }

            try {
                if (useExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
                Log.d(TAG, "Scheduled super heartbeat in ${INTERVAL_MS / 1000}s (exact=$useExact)")
            } catch (se: SecurityException) {
                Log.w(TAG, "Exact alarm denied — falling back to inexact", se)
                try {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to schedule super heartbeat", e)
                }
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                am.cancel(pendingIntent(context))
                Log.i(TAG, "Super heartbeat cancelled")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel super heartbeat", e)
            }
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, SuperHeartbeatReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
                `package` = context.packageName
            }
            return PendingIntent.getBroadcast(
                context, PI_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_HEARTBEAT) return

        // Wake lock so the work below actually runs even if device tries to re-sleep.
        val wakeLock = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
            null
        }

        try {
            if (!LicenseManager.isLicenseValid()) {
                Log.w(TAG, "License invalid — stopping super heartbeat chain")
                return
            }

            Log.i(TAG, "💓 Super heartbeat tick — reviving service")
            // Revive (or no-op if alive) the foreground service AND ask it to reconnect.
            try {
                RealtimeSyncService.reconnect(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reconnect RealtimeSyncService", e)
            }
        } finally {
            // Always re-arm the next alarm — this is a recurring chain, not a one-shot.
            try {
                schedule(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-arm super heartbeat", e)
            }
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release wake lock", e)
            }
        }
    }
}
