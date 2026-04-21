@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.health

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.thaiprompt.smschecker.BuildConfig
import com.thaiprompt.smschecker.service.RealtimeSyncService
import com.thaiprompt.smschecker.service.TtsManager
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import android.content.ContextWrapper

private enum class CheckSeverity { OK, WARN, FAIL }

private data class HealthCheck(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val severity: CheckSeverity,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

@Composable
fun SystemHealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) } // bumped to force re-check on resume / refresh

    // Re-evaluate checks every time the user returns to this screen (after changing a setting)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            tick++
        }
    }

    val checks = remember(tick) { computeChecks(context) }

    val allOk = checks.none { it.severity == CheckSeverity.FAIL }
    val hasWarning = checks.any { it.severity == CheckSeverity.WARN }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("สุขภาพระบบ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "กลับ")
                    }
                },
                actions = {
                    IconButton(onClick = { tick++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "รีเฟรช")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(premiumBackgroundBrush())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Overall summary card
            item {
                OverallSummaryCard(
                    allOk = allOk,
                    hasWarning = hasWarning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Individual check items
            items(checks.size) { i ->
                val check = checks[i]
                HealthCheckItem(
                    check = check,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Test actions
            item {
                TestActionsCard(
                    onTestTts = {
                        try {
                            val tts = resolveTtsManager(context)
                            tts?.speakPreview("ทดสอบเสียงแจ้งเตือน เงินเข้า บัญชี KBANK จำนวน 100 บาท")
                        } catch (_: Exception) {}
                    },
                    onRestartService = {
                        try {
                            RealtimeSyncService.start(context.applicationContext)
                            tick++
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Device info footer
            item {
                DeviceInfoCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

private data class SummaryState(
    val emoji: String,
    val title: String,
    val desc: String,
    val color: Color
)

@Composable
private fun OverallSummaryCard(
    allOk: Boolean,
    hasWarning: Boolean,
    modifier: Modifier = Modifier
) {
    val state = when {
        !allOk -> SummaryState(
            emoji = "🚨",
            title = "พบปัญหาที่ต้องแก้!",
            desc = "มีการตั้งค่าที่ยังไม่พร้อม กดรายการข้างล่างเพื่อแก้ไข",
            color = AppColors.DebitRed
        )
        hasWarning -> SummaryState(
            emoji = "⚠️",
            title = "ทำงานได้ แต่ยังไม่ดีที่สุด",
            desc = "มีบางรายการที่ควรแก้เพื่อความเสถียรสูงสุด",
            color = AppColors.WarningOrange
        )
        else -> SummaryState(
            emoji = "✅",
            title = "ระบบพร้อม 100%",
            desc = "ทุกอย่างพร้อมทำงานตลอด 24 ชั่วโมง",
            color = AppColors.CreditGreen
        )
    }

    GradientHeader(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.emoji, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    state.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = state.color.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun HealthCheckItem(check: HealthCheck, modifier: Modifier = Modifier) {
    val (iconColor, bgColor) = when (check.severity) {
        CheckSeverity.OK -> AppColors.CreditGreen to AppColors.CreditGreen.copy(alpha = 0.1f)
        CheckSeverity.WARN -> AppColors.WarningOrange to AppColors.WarningOrange.copy(alpha = 0.1f)
        CheckSeverity.FAIL -> AppColors.DebitRed to AppColors.DebitRed.copy(alpha = 0.12f)
    }

    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                val statusIcon = when (check.severity) {
                    CheckSeverity.OK -> Icons.Default.CheckCircle
                    CheckSeverity.WARN -> Icons.Default.Warning
                    CheckSeverity.FAIL -> Icons.Default.Error
                }
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    check.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    check.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (check.action != null && check.actionLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = check.action,
                    colors = ButtonDefaults.textButtonColors(contentColor = iconColor)
                ) {
                    Text(check.actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TestActionsCard(
    onTestTts: () -> Unit,
    onRestartService: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = AppColors.GoldAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "ทดสอบระบบ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GoldAccent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onTestTts,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔊 ทดสอบเสียงแจ้งเตือน")
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onRestartService,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 รีสตาร์ท Service")
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column {
            Text(
                "ข้อมูลอุปกรณ์",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            InfoRow("แอปเวอร์ชัน", "${BuildConfig.VERSION_NAME} (#${BuildConfig.VERSION_CODE})")
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow("เครื่อง", "${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// =====================================================================
// Check Computation
// =====================================================================

private fun computeChecks(context: Context): List<HealthCheck> {
    val now = System.currentTimeMillis()

    return buildList {
        // 1. Battery optimization exemption
        val batteryOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
        add(
            HealthCheck(
                icon = Icons.Default.CheckCircle,
                title = "ทำงานเบื้องหลังได้",
                description = if (batteryOk)
                    "แอปได้รับการยกเว้นจาก Battery Optimization"
                else
                    "Android อาจปิดแอปตอนพักหน้าจอ — เสี่ยงพลาด SMS ตอนดึก",
                severity = if (batteryOk) CheckSeverity.OK else CheckSeverity.FAIL,
                actionLabel = if (!batteryOk) "อนุญาต" else null,
                action = if (!batteryOk) ({
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }) else null
            )
        )

        // 2. SMS Permission
        val smsOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        add(
            HealthCheck(
                icon = Icons.Default.CheckCircle,
                title = "อนุญาตรับ SMS",
                description = if (smsOk)
                    "รับข้อความธนาคารผ่าน SMS ได้"
                else
                    "ยังไม่ได้อนุญาต — ไม่สามารถอ่าน SMS จากธนาคารได้",
                severity = if (smsOk) CheckSeverity.OK else CheckSeverity.FAIL,
                actionLabel = if (!smsOk) "ตั้งค่า" else null,
                action = if (!smsOk) ({
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }) else null
            )
        )

        // 3. Notification Access (for bank apps / wallets / LINE Pay)
        val enabledListeners = try {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        } catch (_: Exception) { "" }
        val notifAccessOk = enabledListeners.contains(context.packageName)
        add(
            HealthCheck(
                icon = Icons.Default.CheckCircle,
                title = "อ่านแจ้งเตือนแอปธนาคาร",
                description = if (notifAccessOk)
                    "รับแจ้งเตือนจาก K PLUS, SCB, เป๋าตัง, ถุงเงิน, LINE Pay ฯลฯ ได้"
                else
                    "ยังไม่ได้เปิด Notification Access — แอพธนาคาร/wallet ไม่ถูกตรวจ",
                severity = if (notifAccessOk) CheckSeverity.OK else CheckSeverity.WARN,
                actionLabel = if (!notifAccessOk) "เปิด" else null,
                action = if (!notifAccessOk) ({
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (_: Exception) {}
                }) else null
            )
        )

        // 4. POST_NOTIFICATIONS (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val postNotifOk = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            add(
                HealthCheck(
                    icon = Icons.Default.CheckCircle,
                    title = "แสดงแจ้งเตือนได้",
                    description = if (postNotifOk)
                        "แอปสามารถแสดงแจ้งเตือนบนหน้าจอได้"
                    else
                        "ถ้าปิด แอปจะเห็นเงินเข้าแต่ไม่แจ้งเตือนให้ผู้ใช้ทราบ",
                    severity = if (postNotifOk) CheckSeverity.OK else CheckSeverity.WARN,
                    actionLabel = if (!postNotifOk) "ตั้งค่า" else null,
                    action = if (!postNotifOk) ({
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) else null
                )
            )
        }

        // 5. RealtimeSyncService alive
        val serviceAlive = isServiceRunning(context, "com.thaiprompt.smschecker.service.RealtimeSyncService")
        add(
            HealthCheck(
                icon = Icons.Default.CheckCircle,
                title = "Service ทำงานอยู่",
                description = if (serviceAlive)
                    "SMS Payment Checker กำลังทำงานในพื้นหลัง"
                else
                    "Service ไม่ทำงาน — กดรีสตาร์ทด้านล่าง หรือรอ Watchdog (15 นาที)",
                severity = if (serviceAlive) CheckSeverity.OK else CheckSeverity.FAIL,
                actionLabel = if (!serviceAlive) "เริ่ม" else null,
                action = if (!serviceAlive) ({
                    try { RealtimeSyncService.start(context.applicationContext) } catch (_: Exception) {}
                }) else null
            )
        )

        // 6. Last heartbeat (how recently did the service update its alive timestamp?)
        val hbPrefs = context.getSharedPreferences("smschecker_heartbeat", Context.MODE_PRIVATE)
        val lastAlive = hbPrefs.getLong("last_alive_at", 0L)
        val aliveAge = if (lastAlive == 0L) Long.MAX_VALUE else (now - lastAlive)
        val aliveSeverity = when {
            aliveAge <= 10 * 60_000L -> CheckSeverity.OK       // within 10 min
            aliveAge <= 30 * 60_000L -> CheckSeverity.WARN     // 10-30 min
            else -> CheckSeverity.FAIL                           // >30 min or never
        }
        add(
            HealthCheck(
                icon = Icons.Default.CheckCircle,
                title = "สัญญาณชีพล่าสุด",
                description = if (lastAlive == 0L)
                    "ยังไม่เคยได้รับสัญญาณจาก Service"
                else
                    "อัพเดตล่าสุด: ${formatRelativeTime(aliveAge)}",
                severity = aliveSeverity,
            )
        )

        // 7. Last transaction seen
        val lastTx = hbPrefs.getLong("last_transaction_at", 0L)
        add(
            HealthCheck(
                icon = Icons.Default.CheckCircle,
                title = "รายการล่าสุด",
                description = if (lastTx == 0L)
                    "ยังไม่เคยเจอ SMS/แจ้งเตือนธนาคารบนเครื่องนี้"
                else
                    "${formatRelativeTime(now - lastTx)}",
                severity = CheckSeverity.OK, // informational only
            )
        )

        // 8. FCM token registered
        val fcmPrefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val fcmToken = fcmPrefs.getString("fcm_token", null)
        val fcmNeedsSync = fcmPrefs.getBoolean("fcm_token_needs_sync", false)
        val fcmSeverity = when {
            fcmToken == null -> CheckSeverity.FAIL
            fcmNeedsSync -> CheckSeverity.WARN
            else -> CheckSeverity.OK
        }
        add(
            HealthCheck(
                icon = Icons.Default.CheckCircle,
                title = "FCM Push Token",
                description = when {
                    fcmToken == null -> "ยังไม่ได้ token จาก Firebase — บิลใหม่จากเซิร์ฟเวอร์จะไม่เข้าทันที"
                    fcmNeedsSync -> "ส่ง token ไปเซิร์ฟเวอร์แล้วแต่ยังไม่ครบ — จะ retry ทุก 15 นาที"
                    else -> "รับ push ได้ทันที (token length: ${fcmToken.length})"
                },
                severity = fcmSeverity,
            )
        )
    }
}

private fun formatRelativeTime(diffMs: Long): String {
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "เมื่อสักครู่"
        minutes < 60 -> "${minutes} นาทีที่แล้ว"
        hours < 24 -> "${hours} ชั่วโมงที่แล้ว"
        days < 7 -> "${days} วันที่แล้ว"
        else -> "นานกว่า 1 สัปดาห์"
    }
}

@Suppress("DEPRECATION")
private fun isServiceRunning(context: Context, className: String): Boolean {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getRunningServices(Int.MAX_VALUE).any { it.service.className == className }
    } catch (_: Exception) {
        false
    }
}

/**
 * Resolve TtsManager from the Activity's Hilt component (NOT via hiltViewModel to avoid
 * creating a new VM just for one action).
 *
 * Safely walks the ContextWrapper chain to find an Activity — handles both plain Activity
 * and wrapped contexts (Compose's LocalContext gives us a wrapper). Returns null if the
 * context isn't rooted in an Activity, and any Hilt access failure is caught.
 */
private fun resolveTtsManager(context: Context): TtsManager? {
    return try {
        val activity = findActivity(context) ?: return null
        val entryPoint = EntryPointAccessors.fromActivity(activity, TtsEntryPoint::class.java)
        entryPoint.ttsManager()
    } catch (_: Exception) {
        null
    }
}

private fun findActivity(context: Context): android.app.Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(ActivityComponent::class)
interface TtsEntryPoint {
    fun ttsManager(): TtsManager
}
