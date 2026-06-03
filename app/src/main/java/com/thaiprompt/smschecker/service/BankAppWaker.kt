package com.thaiprompt.smschecker.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 🏦 (2026-05-21) Bank App Waker
 *
 * เคสปัญหา: แอพธนาคาร (K+, SCB Easy, KMA, etc.) อยู่ใน background หลายชั่วโมง
 *           → Android Doze + OEM kill → แอพธนาคารหยุด sync transactions
 *           → ลูกค้าโอนเงินมา แต่แอพธนาคารไม่ส่ง notification ออกมา
 *           → smschecker NotificationListener ไม่ได้ trigger เลย
 *
 * Workaround: เปิด launcher intent ของแอพธนาคารแต่ละตัว → OS resume แอพ
 *             → แอพธนาคาร refresh transactions → ส่ง notification ใหม่
 *             → NotificationListener ของ smschecker รับ → process
 *
 * ข้อจำกัด Android:
 *   - Android 12+ จำกัด background activity launch (BAL)
 *     → ต้องเรียกตอน smschecker app เป็น "alive" (foreground / recently active)
 *   - หน้าจอ admin จะเปลี่ยน (open bank app) — admin device dedicated → ok
 *   - ไม่ guarantee 100% — บางแอพอาจ ignore relaunch ถ้าเพิ่งเปิดไป
 *
 * Permission:
 *   - Android 11+ ต้อง `<queries>` ใน manifest list bank packages (มีอยู่แล้ว)
 *   - ไม่ต้อง QUERY_ALL_PACKAGES (เลี่ยง Play policy review)
 */
object BankAppWaker {

    private const val TAG = "BankAppWaker"

    /**
     * รายชื่อ package ของแอพธนาคารไทย (15 ธนาคารหลัก)
     * ถ้าธนาคารเพิ่มเติม — add ที่นี่ + เพิ่มใน AndroidManifest <queries>
     */
    private val BANK_PACKAGES = listOf(
        "com.kasikorn.retail.mbanking.wap" to "KBank K+",
        "com.scb.phone" to "SCB Easy",
        "com.ktb.netbank" to "KTB NEXT (Krungthai)",
        "com.bbl.mobilebanking" to "Bualuang mBanking (BBL)",
        "com.gsb.mymo" to "GSB MyMo",
        "com.krungsri.kma" to "KMA (Krungsri)",
        "com.ttbbank.touch" to "ttb touch",
        "com.cimbthai.mobile" to "CIMB Mobile",
        "com.kkpfg.app" to "KKP Mobile",
        "com.lhbank.lhbplus" to "LH Bank LHBPlus",
        "com.tisco.mobilebanking" to "Tisco My Wealth",
        "com.uob.tmrw" to "TMRW (UOB)",
        "com.icbc.icbcth" to "ICBC Thai",
        "com.baac.app" to "BAAC A-Mobile",
        "com.alpha.kplus" to "K PLUS alternate"
    )

    /**
     * เปิดแอพธนาคารทุกตัวที่ติดตั้งบนเครื่อง — ทีละตัว
     *
     * @return จำนวนแอพที่เปิดสำเร็จ
     *
     * suspend: ใช้ delay() ระหว่างแต่ละแอพ (ไม่ block thread) — เดิมใช้ Thread.sleep บล็อก IO dispatcher
     * และกิน execution budget ~10s ของ FcmService.onMessageReceived จน rescan ตามหลังไม่ทันได้รัน
     */
    suspend fun wakeAllInstalled(context: Context): Int {
        val pm = context.packageManager
        var woken = 0
        var skipped = 0

        for ((pkg, name) in BANK_PACKAGES) {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent == null) {
                    skipped++
                    continue
                }

                // FLAG_ACTIVITY_NEW_TASK จำเป็น (launch จาก Service/non-Activity context)
                // FLAG_ACTIVITY_NO_USER_ACTION + FLAG_ACTIVITY_NO_ANIMATION = ลด UX disruption
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        or Intent.FLAG_ACTIVITY_NO_ANIMATION
                )

                context.startActivity(launchIntent)
                woken++
                Log.i(TAG, "🏦 woke: $name ($pkg)")

                // ให้แต่ละแอพมีเวลา resume ~500ms ก่อนเปิดตัวถัดไป
                // (กัน Android crush เปิดทีเดียวหลายแอพ + ให้แต่ละแอพมีโอกาส sync)
                // delay() = suspend, ไม่ block thread (เดิม Thread.sleep บล็อก + กิน budget FCM)
                delay(500)
            } catch (e: SecurityException) {
                // Android 12+ BAL block — เกิดเมื่อ smschecker ไม่ active enough
                Log.w(TAG, "🏦 BAL blocked for $name: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "🏦 failed to wake $name: ${e.message}")
            }
        }

        Log.i(TAG, "🏦 BankAppWaker done: woken=$woken skipped=$skipped (not installed) total=${BANK_PACKAGES.size}")
        return woken
    }

    /**
     * ตรวจว่ามีแอพธนาคารกี่ตัวติดตั้งบนเครื่อง (debug/dashboard)
     */
    fun countInstalled(context: Context): Int {
        val pm = context.packageManager
        return BANK_PACKAGES.count { (pkg, _) ->
            pm.getLaunchIntentForPackage(pkg) != null
        }
    }
}
