@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.DashboardStats
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.ui.components.ApprovalDonutChart
import com.thaiprompt.smschecker.ui.components.BankLogoCircle
import com.thaiprompt.smschecker.ui.components.ChartLegendItem
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.OrderBarChart
import com.thaiprompt.smschecker.ui.components.SectionTitle
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val strings = LocalAppStrings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════
        // GRADIENT HEADER with logo & controls
        // ═══════════════════════════════════════
        item(key = "header") {
            GradientHeader(isMonitoring = state.isMonitoring) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column {
                            Text(
                                "SMS Payment",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Checker",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF66BB6A)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF66BB6A)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = strings.refresh,
                                    tint = Color(0xFF66BB6A)
                                )
                            }
                        }
                        Text(
                            if (state.isMonitoring) strings.running else strings.stopped,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isMonitoring) AppColors.CreditGreen else AppColors.DebitRed
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = state.isMonitoring,
                            onCheckedChange = { viewModel.toggleMonitoring() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppColors.CreditGreen,
                                checkedTrackColor = AppColors.CreditGreen.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }

        item(key = "spacer_1") { Spacer(modifier = Modifier.height(16.dp)) }

        // ═══════════════════════════════════════
        // NET BALANCE CARD (big prominent card)
        // ═══════════════════════════════════════
        item(key = "net_balance") {
            val netBalance = state.todayCredit - state.todayDebit
            val netColor = if (netBalance >= 0) AppColors.CreditGreen else AppColors.DebitRed
            val netPrefix = if (netBalance >= 0) "+" else ""

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                netColor.copy(alpha = 0.5f),
                                AppColors.GoldAccent.copy(alpha = 0.3f),
                                netColor.copy(alpha = 0.5f)
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    netColor.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strings.netBalance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$netPrefix${strings.bahtSymbol}${String.format("%,.2f", kotlin.math.abs(netBalance))}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = netColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(
                            label = strings.todayIncome,
                            value = "+${strings.bahtSymbol}${String.format("%,.0f", state.todayCredit)}",
                            color = AppColors.CreditGreen
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )
                        MiniStat(
                            label = strings.todayExpense,
                            value = "-${strings.bahtSymbol}${String.format("%,.0f", state.todayDebit)}",
                            color = AppColors.DebitRed
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )
                        MiniStat(
                            label = strings.todayCount,
                            value = "${state.todayTransactionCount}",
                            color = AppColors.InfoBlue
                        )
                    }
                }
            }
        }

        item(key = "spacer_2") { Spacer(modifier = Modifier.height(12.dp)) }

        // ═══════════════════════════════════════
        // SYSTEM OVERVIEW - 4 stat boxes in grid
        // ═══════════════════════════════════════
        item(key = "system_overview") {
            SectionTitle(
                strings.systemOverview,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item(key = "spacer_overview") { Spacer(modifier = Modifier.height(8.dp)) }

        item(key = "stat_grid") {
            val syncRate = if (state.totalTransactionCount > 0) {
                (state.syncedCount * 100) / state.totalTransactionCount
            } else 0

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Sms,
                        label = strings.totalMessages,
                        value = "${state.totalTransactionCount}",
                        color = AppColors.InfoBlue
                    )
                    StatBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CloudDone,
                        label = strings.syncRate,
                        value = "$syncRate%",
                        color = AppColors.CreditGreen
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Dns,
                        label = strings.connectedServers,
                        value = "${state.serverHealthList.size}",
                        color = AppColors.GoldAccent
                    )
                    StatBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PendingActions,
                        label = strings.offlineQueueLabel,
                        value = "${state.offlineQueueCount}",
                        color = if (state.offlineQueueCount > 0) AppColors.WarningOrange else AppColors.CreditGreen
                    )
                }
            }
        }

        item(key = "spacer_3") { Spacer(modifier = Modifier.height(12.dp)) }

        // ═══════════════════════════════════════
        // SYNC STATUS CARD
        // ═══════════════════════════════════════
        item(key = "sync_status") {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.unsyncedCount > 0)
                                        AppColors.WarningOrange.copy(alpha = 0.15f)
                                    else
                                        AppColors.CreditGreen.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (state.unsyncedCount > 0) AppColors.WarningOrange else AppColors.CreditGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                strings.syncStatus,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                if (state.unsyncedCount > 0) "${state.unsyncedCount} ${strings.syncPending}"
                                else strings.syncComplete,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.unsyncedCount > 0) {
                        FilledTonalButton(
                            onClick = { viewModel.syncAll() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                                contentColor = AppColors.GoldAccent
                            )
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.syncButton)
                        }
                    }
                }

                // Sync progress bar
                if (state.totalTransactionCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val progress = if (state.totalTransactionCount > 0) {
                        state.syncedCount.toFloat() / state.totalTransactionCount.toFloat()
                    } else 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AppColors.CreditGreen,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        Text(
                            "${state.syncedCount}/${state.totalTransactionCount}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // SERVER HEALTH
        // ═══════════════════════════════════════
        if (state.serverHealthList.isNotEmpty()) {
            item(key = "spacer_server") { Spacer(modifier = Modifier.height(12.dp)) }
            item(key = "server_health") {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            strings.serverStatus,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.GoldAccent
                        )
                        // Server count badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.GoldAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${state.serverHealthList.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GoldAccent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    state.serverHealthList.forEach { health ->
                        val statusColor = when {
                            health.neverSynced -> AppColors.GoldAccent
                            health.isReachable -> AppColors.CreditGreen
                            else -> AppColors.DebitRed
                        }
                        val statusText = when {
                            health.neverSynced -> strings.serverWaiting
                            health.isReachable -> {
                                val elapsed = health.lastSyncAt?.let {
                                    val mins = (System.currentTimeMillis() - it) / 60000
                                    if (mins < 1) strings.serverJustNow
                                    else if (mins < 60) "${mins}m ago"
                                    else "${mins / 60}h ago"
                                } ?: strings.serverConnected
                                elapsed
                            }
                            else -> strings.serverOffline
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    health.serverName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // ORDER APPROVALS SUMMARY
        // ═══════════════════════════════════════
        item(key = "spacer_order") { Spacer(modifier = Modifier.height(12.dp)) }
        item(key = "order_approval") {
            OrderApprovalSummaryCard(
                stats = state.orderStats,
                pendingCount = state.pendingApprovalCount,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ═══════════════════════════════════════
        // RECENT TRANSACTIONS
        // ═══════════════════════════════════════
        item(key = "spacer_tx_header") { Spacer(modifier = Modifier.height(16.dp)) }
        item(key = "tx_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    strings.recentTransactions,
                    modifier = Modifier.weight(1f)
                )
                if (state.recentTransactions.isNotEmpty()) {
                    Text(
                        "${state.recentTransactions.size} ${strings.totalLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
        item(key = "spacer_tx") { Spacer(modifier = Modifier.height(8.dp)) }

        // Transaction List
        if (state.recentTransactions.isEmpty() && !state.isLoading) {
            item(key = "empty_tx") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            strings.noTransactions,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            strings.smsAutoDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        items(
            items = state.recentTransactions,
            key = { "dash_${it.id}" }
        ) { transaction ->
            TransactionItem(
                transaction = transaction,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Bottom spacing for nav bar
        item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════

@Composable
private fun MiniStat(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun StatBox(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            color.copy(alpha = 0.1f),
                            color.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OrderApprovalSummaryCard(
    stats: DashboardStats,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                strings.orderApproval,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.GoldAccent
            )
            if (pendingCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.WarningOrange)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "$pendingCount ${strings.pending}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ApprovalDonutChart(
                autoApproved = stats.autoApproved,
                manuallyApproved = stats.manuallyApproved,
                pending = stats.pendingReview,
                rejected = stats.rejected,
                modifier = Modifier.size(100.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                ChartLegendItem(label = strings.autoApproved, count = stats.autoApproved, color = AppColors.CreditGreen)
                ChartLegendItem(label = strings.manuallyApproved, count = stats.manuallyApproved, color = AppColors.GoldAccent)
                ChartLegendItem(label = strings.pendingReview, count = stats.pendingReview, color = AppColors.WarningOrange)
                ChartLegendItem(label = strings.rejected, count = stats.rejected, color = AppColors.DebitRed)
            }
        }

        if (stats.dailyBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                strings.last7Days,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OrderBarChart(
                dailyStats = stats.dailyBreakdown,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
        }
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color,
    gradientBrush: Brush = Brush.linearGradient(
        colors = listOf(color.copy(alpha = 0.15f), color.copy(alpha = 0.05f))
    )
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${strings.bahtSymbol}${String.format("%,.2f", amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun TransactionItem(
    transaction: BankTransaction,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) AppColors.CreditGreen else AppColors.DebitRed
    val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                BankLogoCircle(bankCode = transaction.bank, size = 44.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        transaction.bank,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        dateFormat.format(Date(transaction.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (transaction.senderOrReceiver.isNotBlank()) {
                        Text(
                            transaction.senderOrReceiver.take(30),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    transaction.getFormattedAmount(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (transaction.isSynced) AppColors.CreditGreen else AppColors.WarningOrange)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (transaction.isSynced) strings.synced else strings.pending,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
