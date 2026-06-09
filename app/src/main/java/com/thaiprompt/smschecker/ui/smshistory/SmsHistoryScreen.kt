@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.thaiprompt.smschecker.ui.smshistory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.ui.components.AeroChip
import com.thaiprompt.smschecker.ui.components.AeroGlass
import com.thaiprompt.smschecker.ui.components.BankCoin
import com.thaiprompt.smschecker.ui.components.ChipStyle
import com.thaiprompt.smschecker.ui.components.ChromeSegmented
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.MisclassificationReportDialog
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.AppStrings
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings

/**
 * SMS History screen with manual scan button and improved status display.
 */
@Composable
fun SmsHistoryScreen(
    viewModel: SmsHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header
        item(key = "header") {
            GradientHeader {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            strings.smsMatcherTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            strings.smsMatcherSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF66BB6A)
                        )
                    }
                }
            }
        }

        item(key = "spacer_top") { Spacer(modifier = Modifier.height(12.dp)) }

        // Stats Summary
        item(key = "stats") {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        icon = Icons.Default.Email,
                        value = "${state.totalDetected}",
                        label = strings.totalDetected,
                        color = AppColors.InfoBlue
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    StatItem(
                        icon = Icons.Default.CloudDone,
                        value = "${state.totalSynced}",
                        label = strings.totalSynced,
                        color = AppColors.CreditGreen
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    StatItem(
                        icon = Icons.Default.Pending,
                        value = "${maxOf(0, state.totalDetected - state.totalSynced)}",
                        label = strings.pendingLabel,
                        color = AppColors.WarningOrange
                    )
                }
            }
        }

        item(key = "spacer_mid") { Spacer(modifier = Modifier.height(16.dp)) }

        // Filter tabs
        if (state.transactions.isNotEmpty() || state.isLoading) {
            item(key = "filter_tabs") {
                ChromeSegmented(
                    options = listOf(strings.allTypes, strings.creditOnly, strings.debitOnly),
                    selectedIndex = state.filter.ordinal,
                    onSelect = { viewModel.setFilter(HistoryFilter.entries[it]) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "spacer_filter") { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Transaction list
        if (state.transactions.isNotEmpty()) {
            items(state.transactions.size, key = { state.transactions[it].id }) { index ->
                val tx = state.transactions[index]
                TransactionItem(
                    transaction = tx,
                    strings = strings,
                    onEdit = { viewModel.startEditing(tx) },
                    onLongPress = { viewModel.showReportDialog(tx) }
                )
                if (index < state.transactions.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Empty state
        if (state.transactions.isEmpty() && !state.isLoading) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Textsms,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            strings.noTransactionsYet,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            strings.smsAutoDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Bottom spacer for nav bar
        item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TransactionItem(
    transaction: com.thaiprompt.smschecker.data.model.BankTransaction,
    strings: AppStrings,
    onEdit: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val isCredit = transaction.type == com.thaiprompt.smschecker.data.model.TransactionType.CREDIT
    val typeColor = if (isCredit) AppColors.CreditGreen else AppColors.DebitRed
    val stripeColor = if (isCredit) AeroPalette.GreenLo else AeroPalette.Chrome3
    val subLine = listOf(
        transaction.getFormattedTimestamp(),
        transaction.senderAddress.takeIf { it.isNotBlank() } ?: transaction.accountNumber
    ).filter { it.isNotBlank() }.joinToString("  ·  ")

    AeroGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(onClick = onEdit, onLongClick = { onLongPress?.invoke() }),
        cornerRadius = 16.dp
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // status stripe (green = credit/matched, gray = debit/other)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )
            Column(modifier = Modifier.padding(start = 13.dp, top = 12.dp, end = 14.dp, bottom = 13.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BankCoin(bankCode = transaction.bank, size = 34.dp)
                    Spacer(modifier = Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            transaction.bank,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            subLine,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        when {
                            isCredit && transaction.isSynced ->
                                AeroChip(strings.syncedLabel, style = ChipStyle.Green, leadingIcon = Icons.Default.Check)
                            isCredit ->
                                AeroChip(strings.creditOnly, style = ChipStyle.Green, leadingIcon = Icons.Default.ArrowDownward)
                            else ->
                                AeroChip(strings.debitOnly, style = ChipStyle.Red, leadingIcon = Icons.Default.ArrowUpward)
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            transaction.getFormattedAmount(),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = typeColor
                        )
                    }
                }
                if (transaction.rawMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        transaction.rawMessage,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
