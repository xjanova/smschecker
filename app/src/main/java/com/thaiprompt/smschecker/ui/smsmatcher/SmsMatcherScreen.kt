package com.thaiprompt.smschecker.ui.smsmatcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionSource
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.ui.components.BankLogoCircle
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.AppStrings
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsMatcherScreen(
    onBack: () -> Unit,
    viewModel: SmsMatcherViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            strings.smsMatcherTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            strings.smsMatcherSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === Stats Summary ===
            item(key = "stats") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            icon = Icons.Default.Email,
                            value = "${state.totalDetected}",
                            label = strings.totalDetected,
                            color = AppColors.InfoBlue
                        )
                        StatItem(
                            icon = Icons.Default.CloudDone,
                            value = "${state.totalSynced}",
                            label = strings.totalSynced,
                            color = AppColors.CreditGreen
                        )
                        StatItem(
                            icon = Icons.Default.Pending,
                            value = "${state.totalDetected - state.totalSynced}",
                            label = strings.pendingLabel,
                            color = AppColors.WarningOrange
                        )
                    }
                }
            }

            // === Filter Chips ===
            item(key = "filters") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filter == TransactionFilter.ALL,
                        onClick = { viewModel.setFilter(TransactionFilter.ALL) },
                        label = { Text(strings.allTypes) },
                        leadingIcon = if (state.filter == TransactionFilter.ALL) {
                            { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = state.filter == TransactionFilter.CREDIT,
                        onClick = { viewModel.setFilter(TransactionFilter.CREDIT) },
                        label = { Text(strings.creditOnly) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.CreditGreen.copy(alpha = 0.2f)
                        ),
                        leadingIcon = if (state.filter == TransactionFilter.CREDIT) {
                            { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = state.filter == TransactionFilter.DEBIT,
                        onClick = { viewModel.setFilter(TransactionFilter.DEBIT) },
                        label = { Text(strings.debitOnly) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.DebitRed.copy(alpha = 0.2f)
                        ),
                        leadingIcon = if (state.filter == TransactionFilter.DEBIT) {
                            { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }

            // === Loading State ===
            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.GoldAccent)
                    }
                }
            }

            // === Empty State ===
            if (!state.isLoading && state.transactions.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                strings.noTransactionsYet,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // === Transaction List ===
            items(
                items = state.transactions,
                key = { "tx_${it.id}" }
            ) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    dateFormat = dateFormat,
                    strings = strings
                )
            }

            // Bottom spacer
            item(key = "spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionCard(
    transaction: BankTransaction,
    dateFormat: SimpleDateFormat,
    strings: AppStrings
) {
    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) AppColors.CreditGreen else AppColors.DebitRed
    val typeIcon = if (isCredit) Icons.Default.CallReceived else Icons.Default.CallMade

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bank Logo
            BankLogoCircle(
                bankCode = transaction.bank,
                size = 40.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction Info
            Column(modifier = Modifier.weight(1f)) {
                // Bank + Type row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        transaction.bank,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        typeIcon,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Raw message preview
                Text(
                    transaction.rawMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags row
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source badge
                    val sourceText = if (transaction.sourceType == TransactionSource.NOTIFICATION) "NOTIF" else "SMS"
                    val sourceColor = if (transaction.sourceType == TransactionSource.NOTIFICATION)
                        Color(0xFF7C4DFF) else AppColors.InfoBlue
                    Badge(
                        containerColor = sourceColor.copy(alpha = 0.15f),
                        contentColor = sourceColor
                    ) {
                        Text(
                            sourceText,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Sync badge
                    val syncText = if (transaction.isSynced) strings.syncedLabel else strings.pendingLabel
                    val syncColor = if (transaction.isSynced) AppColors.CreditGreen else AppColors.WarningOrange
                    Badge(
                        containerColor = syncColor.copy(alpha = 0.15f),
                        contentColor = syncColor
                    ) {
                        Text(
                            syncText,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Time
                    Text(
                        dateFormat.format(Date(transaction.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            Text(
                transaction.getFormattedAmount(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = amountColor
            )
        }
    }
}
