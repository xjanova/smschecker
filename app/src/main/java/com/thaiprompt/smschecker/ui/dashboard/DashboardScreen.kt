@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.ui.components.AeroChip
import com.thaiprompt.smschecker.ui.components.AeroGlass
import com.thaiprompt.smschecker.ui.components.AeroHeader
import com.thaiprompt.smschecker.ui.components.AeroSectionHeader
import com.thaiprompt.smschecker.ui.components.AeroSparkline
import com.thaiprompt.smschecker.ui.components.AeroStatTile
import com.thaiprompt.smschecker.ui.components.BankBar
import com.thaiprompt.smschecker.ui.components.BankCoin
import com.thaiprompt.smschecker.ui.components.ChipStyle
import com.thaiprompt.smschecker.ui.components.HeaderTone
import com.thaiprompt.smschecker.ui.components.MisclassificationReportDialog
import com.thaiprompt.smschecker.ui.components.OrbGradients
import com.thaiprompt.smschecker.ui.components.StatusBarTone
import com.thaiprompt.smschecker.ui.components.aeroHeaderBleed
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.AppStrings
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard — Millennium 3D / Frutiger Aero (design 01).
 * Green header bleed, "เงินเข้าวันนี้" hero with sparkline, 2x2 stat grid with
 * corner orbs, popular-bank bars and a recent-transactions glass card.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onChartTap: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current

    StatusBarTone(HeaderTone.Green)

    // Show Report Dialog
    if (state.showReportDialog && state.selectedTransaction != null) {
        MisclassificationReportDialog(
            onDismiss = { viewModel.hideReportDialog() },
            onConfirm = { issueType ->
                viewModel.submitReport(issueType)
            },
            bankName = state.selectedTransaction!!.bank,
            currentType = state.selectedTransaction!!.type.name,
            currentAmount = state.selectedTransaction!!.amount
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aeroHeaderBleed(HeaderTone.Green)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // ── header: logo coin + title + connection chip ──
            item(key = "header") {
                AeroHeader {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(6.dp, RoundedCornerShape(30), spotColor = AeroPalette.NavyDeep)
                                    .clip(RoundedCornerShape(30))
                                    .background(Color.White)
                                    .border(1.5.dp, Color(0xE6FFFFFF), RoundedCornerShape(30)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(0.86f)
                                )
                            }
                            Column {
                                Text(
                                    strings.aeroDashboardTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    if (state.isMonitoring) strings.monitoringActive else strings.monitoringPaused,
                                    fontSize = 12.5.sp,
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 1
                                )
                            }
                        }
                        // connection chip — tap toggles SMS monitoring (replaces the old Switch)
                        Box(modifier = Modifier.clickable { viewModel.toggleMonitoring() }) {
                            if (state.isMonitoring) {
                                AeroChip(
                                    strings.aeroConnected,
                                    style = ChipStyle.Green,
                                    leadingIcon = Icons.Default.Bolt
                                )
                            } else {
                                AeroChip(strings.stopped, style = ChipStyle.Glass)
                            }
                        }
                    }
                }
            }

            // ── hero: เงินเข้าวันนี้ + sparkline (tap → revenue detail) ──
            item(key = "hero") {
                val incomeSeries = state.dailyIncomeExpense.map { it.credit.toFloat() }
                val dayLabels = remember(state.dailyIncomeExpense) {
                    state.dailyIncomeExpense.map { thaiDayAbbrev(it.date) }
                }
                AeroGlass(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onChartTap),
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            strings.aeroIncomeToday,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AeroPalette.InkSoft
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        HeroAmount(amount = state.todayCredit, strings = strings)
                        if (incomeSeries.size >= 2) {
                            Spacer(modifier = Modifier.height(6.dp))
                            AeroSparkline(
                                points = incomeSeries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                dayLabels.forEach {
                                    Text(it, fontSize = 11.sp, color = AeroPalette.InkFaint)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "spacer_hero") { Spacer(modifier = Modifier.height(13.dp)) }

            // ── 2×2 stat grid ──
            item(key = "stat_grid") {
                val successRate = if (state.totalTransactionCount > 0) {
                    (state.syncedCount * 100) / state.totalTransactionCount
                } else 0
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        AeroStatTile(
                            label = strings.todayCount,
                            value = "${state.todayTransactionCount}",
                            orbIcon = Icons.Default.ChatBubbleOutline,
                            orbGradient = OrbGradients.Aqua,
                            valueColor = AeroPalette.NavyDeep,
                            modifier = Modifier.weight(1f)
                        )
                        AeroStatTile(
                            label = strings.synced,
                            value = "${state.syncedCount}",
                            orbIcon = Icons.Default.Check,
                            orbGradient = OrbGradients.Green,
                            valueColor = AeroPalette.GreenDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        AeroStatTile(
                            label = strings.pendingReview,
                            value = "${state.pendingApprovalCount}",
                            orbIcon = Icons.Default.Schedule,
                            orbGradient = OrbGradients.Gold,
                            valueColor = AeroPalette.GoldText,
                            modifier = Modifier.weight(1f)
                        )
                        AeroStatTile(
                            label = strings.successRate,
                            value = "$successRate%",
                            orbIcon = Icons.Default.Shield,
                            orbGradient = OrbGradients.Navy,
                            valueColor = AeroPalette.NavyDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── popular banks ──
            val bankStats = topBanks(state.recentTransactions)
            if (bankStats.isNotEmpty()) {
                item(key = "banks_header") {
                    Spacer(modifier = Modifier.height(18.dp))
                    AeroSectionHeader(
                        title = strings.aeroPopularBanks,
                        leadingIcon = Icons.Default.GridView,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                item(key = "banks_card") {
                    val topCount = bankStats.first().second
                    AeroGlass(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp)
                    ) {
                        Column {
                            bankStats.forEachIndexed { index, (bank, count) ->
                                BankBar(
                                    bankCode = bank,
                                    bankLabel = thaiBankLabel(bank, strings),
                                    fraction = if (topCount > 0) count.toFloat() / topCount else 0f,
                                    valueText = "$count",
                                    showDivider = index > 0
                                )
                            }
                        }
                    }
                }
            }

            // ── recent transactions ──
            item(key = "tx_header") {
                Spacer(modifier = Modifier.height(18.dp))
                AeroSectionHeader(
                    title = strings.recentTransactions,
                    leadingIcon = Icons.Default.Schedule,
                    actionText = if (state.unsyncedCount > 0) "${strings.syncButton} (${state.unsyncedCount})" else null,
                    onAction = if (state.unsyncedCount > 0) ({ viewModel.syncAll() }) else null,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(9.dp))
            }

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
            } else if (state.recentTransactions.isNotEmpty()) {
                item(key = "tx_card") {
                    AeroGlass(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column {
                            state.recentTransactions.forEachIndexed { index, transaction ->
                                if (index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0x4D9FB8C8))
                                    )
                                }
                                RecentTxnRow(
                                    transaction = transaction,
                                    strings = strings,
                                    onLongPress = { viewModel.showReportDialog(transaction) }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing for nav bar
            item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════

/** ฿48,250 (40sp Black navy) + .37 (24sp faint) — reference hero amount. */
@Composable
private fun HeroAmount(amount: Double, strings: AppStrings) {
    // 🐞 (2026-06-11) คิดจากสตางค์ที่ round แล้ว — เดิม ((amount - long) * 100).toInt()
    // ตัดเศษ float ทิ้ง ทำให้ เช่น 500.29 แสดงเป็น ฿500.28 (0.29*100 = 28.999...)
    val totalSatang = Math.round(amount * 100)
    val whole = String.format(Locale.US, "%,d", totalSatang / 100)
    val decimals = String.format(Locale.US, "%02d", totalSatang % 100)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${strings.bahtSymbol}$whole",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = AeroPalette.NavyDeep,
            letterSpacing = (-1).sp,
            lineHeight = 44.sp
        )
        Text(
            ".$decimals",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AeroPalette.InkFaint,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

/** One recent-transaction glass row (.txn): coin + meta + arrow-coded amount. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTxnRow(
    transaction: BankTransaction,
    strings: AppStrings,
    onLongPress: (() -> Unit)? = null
) {
    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) AeroPalette.GreenDeep else AeroPalette.Red
    val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onLongPress?.invoke() })
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        BankCoin(bankCode = transaction.bank, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    transaction.bank,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                if (transaction.isSynced) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = AeroPalette.GreenDeep,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(13.dp)
                    )
                }
            }
            Text(
                "${strings.aeroAccountPrefix} ${transaction.getMaskedAccount()} · " +
                    if (isCredit) strings.aeroIncomingLabel else strings.aeroOutgoingLabel,
                fontSize = 11.sp,
                color = AeroPalette.InkFaint,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    "฿${String.format(Locale.US, "%,.2f", transaction.getAmountAsBigDecimal())}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = amountColor,
                    maxLines = 1
                )
            }
            Text(
                timeFormat.format(Date(transaction.timestamp)),
                fontSize = 10.5.sp,
                color = AeroPalette.InkFaint
            )
        }
    }
}

// ═══════════════════════════════════════
// DATA HELPERS
// ═══════════════════════════════════════

/** Top 4 banks by transaction count from recent activity. */
private fun topBanks(transactions: List<BankTransaction>): List<Pair<String, Int>> =
    transactions
        .groupingBy { it.bank }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(4)
        .map { it.key to it.value }

/** Thai weekday abbreviation (จ อ พ พฤ ศ ส อา) from a yyyy-MM-dd key. */
private fun thaiDayAbbrev(dateKey: String): String = try {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
    when (java.util.Calendar.getInstance().apply { time = date!! }.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "จ"
        java.util.Calendar.TUESDAY -> "อ"
        java.util.Calendar.WEDNESDAY -> "พ"
        java.util.Calendar.THURSDAY -> "พฤ"
        java.util.Calendar.FRIDAY -> "ศ"
        java.util.Calendar.SATURDAY -> "ส"
        else -> "อา"
    }
} catch (e: Exception) {
    ""
}

/** Thai display name for a bank code (from AppStrings). */
private fun thaiBankLabel(code: String, strings: AppStrings): String = when (code.uppercase()) {
    "KBANK" -> strings.bankKbank
    "SCB" -> strings.bankScb
    "KTB" -> strings.bankKtb
    "BBL" -> strings.bankBbl
    "GSB" -> strings.bankGsb
    "BAY" -> strings.bankBay
    "TTB" -> strings.bankTtb
    "PROMPTPAY" -> strings.bankPromptPay
    "CIMB" -> strings.bankCimb
    "KKP" -> strings.bankKkp
    "LH" -> strings.bankLh
    "TISCO" -> strings.bankTisco
    "UOB" -> strings.bankUob
    "ICBC" -> strings.bankIcbc
    "BAAC" -> strings.bankBaac
    else -> ""
}
