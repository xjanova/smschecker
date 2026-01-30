package com.thaiprompt.smschecker.ui.dashboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
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
        // Gradient Header
        item {
            GradientHeader {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "SMS Payment",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Checker",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GoldAccent
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.GoldAccent
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = strings.refresh,
                                    tint = AppColors.GoldAccent
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

        // Content area with padding
        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Summary Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = strings.todayIncome,
                    amount = state.todayCredit,
                    icon = Icons.Default.TrendingUp,
                    color = AppColors.CreditGreen,
                    gradientBrush = Brush.linearGradient(
                        colors = listOf(
                            AppColors.CreditGreen.copy(alpha = 0.15f),
                            AppColors.CreditGreen.copy(alpha = 0.05f)
                        )
                    )
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = strings.todayExpense,
                    amount = state.todayDebit,
                    icon = Icons.Default.TrendingDown,
                    color = AppColors.DebitRed,
                    gradientBrush = Brush.linearGradient(
                        colors = listOf(
                            AppColors.DebitRed.copy(alpha = 0.15f),
                            AppColors.DebitRed.copy(alpha = 0.05f)
                        )
                    )
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Sync Status Card
        item {
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
            }
        }

        // Server Health
        if (state.serverHealthList.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        strings.serverStatus,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.GoldAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    state.serverHealthList.forEach { health ->
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
                                        .background(if (health.isReachable) AppColors.CreditGreen else AppColors.DebitRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    health.serverName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                if (health.isReachable) "${health.latencyMs}ms" else strings.serverOffline,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (health.isReachable) AppColors.CreditGreen else AppColors.DebitRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Order Approvals Summary
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            OrderApprovalSummaryCard(
                stats = state.orderStats,
                pendingCount = state.pendingApprovalCount,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Recent Transactions Header
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            SectionTitle(
                strings.recentTransactions,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Transaction List
        if (state.recentTransactions.isEmpty() && !state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            strings.noTransactions,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            strings.smsAutoDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        items(state.recentTransactions) { transaction ->
            TransactionItem(
                transaction = transaction,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Bottom spacing for nav bar
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                Badge(
                    containerColor = AppColors.WarningOrange,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Text("$pendingCount ${strings.pending}")
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
