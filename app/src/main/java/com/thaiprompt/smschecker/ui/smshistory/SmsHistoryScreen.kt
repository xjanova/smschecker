@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.smshistory

import androidx.compose.foundation.background
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
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filter == HistoryFilter.ALL,
                        onClick = { viewModel.setFilter(HistoryFilter.ALL) },
                        label = { Text(strings.allTypes) }
                    )
                    FilterChip(
                        selected = state.filter == HistoryFilter.CREDIT,
                        onClick = { viewModel.setFilter(HistoryFilter.CREDIT) },
                        label = { Text(strings.creditOnly) }
                    )
                    FilterChip(
                        selected = state.filter == HistoryFilter.DEBIT,
                        onClick = { viewModel.setFilter(HistoryFilter.DEBIT) },
                        label = { Text(strings.debitOnly) }
                    )
                }
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
                    onEdit = { viewModel.startEditing(tx) }
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
    onEdit: () -> Unit
) {
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.bank,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    transaction.getFormattedTimestamp(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.synced) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = AppColors.CreditGreen
                        )
                        Text(
                            strings.syncedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = AppColors.CreditGreen
                        )
                    }
                }
            }

            val typeColor = if (transaction.type == com.thaiprompt.smschecker.data.model.TransactionType.CREDIT) {
                AppColors.CreditGreen
            } else {
                AppColors.DebitRed
            }

            Text(
                transaction.getFormattedAmount(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = typeColor
            )
        }
    }
}
