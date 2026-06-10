@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.thaiprompt.smschecker.ui.smshistory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.ui.components.AeroChip
import com.thaiprompt.smschecker.ui.components.AeroGlass
import com.thaiprompt.smschecker.ui.components.AeroHeader
import com.thaiprompt.smschecker.ui.components.BankCoin
import com.thaiprompt.smschecker.ui.components.ChipStyle
import com.thaiprompt.smschecker.ui.components.ChromeSegmented
import com.thaiprompt.smschecker.ui.components.HeaderTone
import com.thaiprompt.smschecker.ui.components.MisclassificationReportDialog
import com.thaiprompt.smschecker.ui.components.StatusBarTone
import com.thaiprompt.smschecker.ui.components.aeroHeaderBleed
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.AppStrings
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings

/**
 * SMS History screen — Millennium 3D / Frutiger Aero (design 04).
 * Aqua header bleed + raw SMS "chat bubble" glass cards with status stripe.
 */
@Composable
fun SmsHistoryScreen(
    viewModel: SmsHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current

    StatusBarTone(HeaderTone.Aqua)

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
            .aeroHeaderBleed(HeaderTone.Aqua)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Header
            item(key = "header") {
                AeroHeader {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.aeroSmsTitle,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "${state.totalDetected} ${strings.aeroLatestMessages} · ${strings.synced}",
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 1
                            )
                        }
                        AeroChip(
                            strings.aeroLive,
                            style = ChipStyle.GlassOnDark,
                            leadingIcon = Icons.Default.Bolt
                        )
                    }
                }
            }

            // Filter tabs (functional necessity — kept under the header)
            if (state.allTransactions.isNotEmpty() || state.isLoading) {
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
                        Spacer(modifier = Modifier.height(11.dp))
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
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Bottom spacer for nav bar
            item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
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
    val stripeColor = if (isCredit) AeroPalette.GreenLo else AeroPalette.Chrome3
    val subLine = listOf(
        transaction.getFormattedTimestamp(),
        transaction.senderAddress.takeIf { it.isNotBlank() } ?: transaction.accountNumber
    ).filter { it.isNotBlank() }.joinToString(" · ")

    AeroGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(onClick = onEdit, onLongClick = { onLongPress?.invoke() }),
        cornerRadius = 16.dp
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // status stripe (green = credit, chrome = debit)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )
            Column(modifier = Modifier.padding(start = 13.dp, top = 13.dp, end = 15.dp, bottom = 13.dp)) {
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
                            color = AeroPalette.InkFaint,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    if (isCredit) {
                        AeroChip(strings.aeroMatched, style = ChipStyle.Green, leadingIcon = Icons.Default.Check)
                    } else {
                        AeroChip(strings.debitOnly, style = ChipStyle.Red, leadingIcon = Icons.Default.ArrowDownward)
                    }
                }
                if (transaction.rawMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        transaction.rawMessage,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
