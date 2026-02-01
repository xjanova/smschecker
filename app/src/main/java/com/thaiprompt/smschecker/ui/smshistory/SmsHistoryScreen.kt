@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.smshistory

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionSource
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.ui.components.BankLogoCircle
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.SectionTitle
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "SmsHistoryScreen"

private val BANK_OPTIONS = listOf(
    "KBANK", "SCB", "KTB", "BBL", "GSB", "BAY", "TTB",
    "PROMPTPAY", "CIMB", "KKP", "LH", "TISCO", "UOB", "ICBC", "BAAC"
)

@Composable
fun SmsHistoryScreen(
    viewModel: SmsHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    // Edit dialog
    if (state.editingTransaction != null) {
        EditTransactionDialog(
            transaction = state.editingTransaction!!,
            isSaving = state.isSaving,
            onSave = { bank, type, amount ->
                viewModel.saveEdit(bank, type, amount)
            },
            onDismiss = { viewModel.cancelEditing() }
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
                    HistoryStatItem(
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
                    HistoryStatItem(
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
                    HistoryStatItem(
                        icon = Icons.Default.Pending,
                        value = "${maxOf(0, state.totalDetected - state.totalSynced)}",
                        label = strings.pendingLabel,
                        color = AppColors.WarningOrange
                    )
                }
            }
        }

        item(key = "spacer_filter") { Spacer(modifier = Modifier.height(12.dp)) }

        // Filter Chips
        item(key = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.filter == HistoryFilter.ALL,
                    onClick = { viewModel.setFilter(HistoryFilter.ALL) },
                    label = { Text(strings.allTypes) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.GoldAccent.copy(alpha = 0.25f),
                        selectedLabelColor = AppColors.GoldAccent
                    ),
                    leadingIcon = if (state.filter == HistoryFilter.ALL) {
                        { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = state.filter == HistoryFilter.CREDIT,
                    onClick = { viewModel.setFilter(HistoryFilter.CREDIT) },
                    label = { Text(strings.creditOnly) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.CreditGreen.copy(alpha = 0.2f),
                        selectedLabelColor = AppColors.CreditGreen
                    ),
                    leadingIcon = if (state.filter == HistoryFilter.CREDIT) {
                        { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = state.filter == HistoryFilter.DEBIT,
                    onClick = { viewModel.setFilter(HistoryFilter.DEBIT) },
                    label = { Text(strings.debitOnly) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.DebitRed.copy(alpha = 0.2f),
                        selectedLabelColor = AppColors.DebitRed
                    ),
                    leadingIcon = if (state.filter == HistoryFilter.DEBIT) {
                        { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }

        item(key = "spacer_list") { Spacer(modifier = Modifier.height(8.dp)) }

        // Section title with count
        item(key = "list_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(strings.recentSmsTitle)
                if (state.transactions.isNotEmpty()) {
                    Text(
                        "${state.transactions.size} ${strings.totalLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item(key = "spacer_list2") { Spacer(modifier = Modifier.height(8.dp)) }

        // Loading State
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

        // Empty State
        if (!state.isLoading && state.transactions.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
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
                            strings.noTransactionsYet,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Transaction List
        if (!state.isLoading && state.transactions.isNotEmpty()) {
            items(
                items = state.transactions,
                key = { "hist_${it.id}" }
            ) { transaction ->
                HistoryTransactionCard(
                    transaction = transaction,
                    dateFormat = dateFormat,
                    onEditClick = { viewModel.startEditing(transaction) }
                )
            }
        }

        // Bottom spacer for nav bar
        item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun HistoryStatItem(
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
private fun HistoryTransactionCard(
    transaction: BankTransaction,
    dateFormat: SimpleDateFormat,
    onEditClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) AppColors.CreditGreen else AppColors.DebitRed
    val typeIcon = if (isCredit) Icons.Default.CallReceived else Icons.Default.CallMade

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onEditClick() },
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bank Logo
            BankLogoCircle(bankCode = transaction.bank, size = 42.dp)

            Spacer(modifier = Modifier.width(12.dp))

            // Info
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
                if (transaction.rawMessage.isNotBlank()) {
                    Text(
                        transaction.rawMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                }

                // Tags row
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source badge
                    val sourceText = try {
                        if (transaction.sourceType == TransactionSource.NOTIFICATION) "NOTIF" else "SMS"
                    } catch (_: Exception) { "SMS" }
                    val sourceColor = try {
                        if (transaction.sourceType == TransactionSource.NOTIFICATION)
                            Color(0xFF7C4DFF) else AppColors.InfoBlue
                    } catch (_: Exception) { AppColors.InfoBlue }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(sourceColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(sourceText, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = sourceColor)
                    }

                    // Sync badge
                    val syncText = if (transaction.isSynced) strings.syncedLabel else strings.pendingLabel
                    val syncColor = if (transaction.isSynced) AppColors.CreditGreen else AppColors.WarningOrange
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(syncColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(syncText, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = syncColor)
                    }

                    // Time
                    val timeText = try {
                        dateFormat.format(Date(transaction.timestamp))
                    } catch (_: Exception) { "" }
                    if (timeText.isNotBlank()) {
                        Text(
                            timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Amount + Edit icon
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    try { transaction.getFormattedAmount() } catch (_: Exception) { transaction.amount },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = amountColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = strings.editTransaction,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun EditTransactionDialog(
    transaction: BankTransaction,
    isSaving: Boolean,
    onSave: (bank: String, type: TransactionType, amount: String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedBank by remember { mutableStateOf(transaction.bank) }
    var selectedType by remember { mutableStateOf(transaction.type) }
    var amount by remember { mutableStateOf(transaction.amount) }
    var showBankDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.editTransaction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.GoldAccent
                    )
                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.editCancel,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Subtitle
                Text(
                    strings.editIncorrectData,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Raw message (read-only)
                if (transaction.rawMessage.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                strings.editRawMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                transaction.rawMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Bank Selector
                Column {
                    Text(
                        strings.editBank,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = showBankDropdown,
                        onExpandedChange = { showBankDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedBank,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBankDropdown)
                            },
                            leadingIcon = {
                                BankLogoCircle(bankCode = selectedBank, size = 28.dp)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.GoldAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        ExposedDropdownMenu(
                            expanded = showBankDropdown,
                            onDismissRequest = { showBankDropdown = false }
                        ) {
                            BANK_OPTIONS.forEach { bank ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            BankLogoCircle(bankCode = bank, size = 24.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(bank)
                                        }
                                    },
                                    onClick = {
                                        selectedBank = bank
                                        showBankDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Type Selector
                Column {
                    Text(
                        strings.editType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == TransactionType.CREDIT,
                            onClick = { selectedType = TransactionType.CREDIT },
                            label = { Text(strings.creditOnly) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.CreditGreen.copy(alpha = 0.2f),
                                selectedLabelColor = AppColors.CreditGreen
                            ),
                            leadingIcon = if (selectedType == TransactionType.CREDIT) {
                                { Icon(Icons.Default.CallReceived, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedType == TransactionType.DEBIT,
                            onClick = { selectedType = TransactionType.DEBIT },
                            label = { Text(strings.debitOnly) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.DebitRed.copy(alpha = 0.2f),
                                selectedLabelColor = AppColors.DebitRed
                            ),
                            leadingIcon = if (selectedType == TransactionType.DEBIT) {
                                { Icon(Icons.Default.CallMade, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                // Amount
                Column {
                    Text(
                        strings.editAmount,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("฿ ") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.GoldAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (!isSaving) onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving
                    ) {
                        Text(strings.editCancel)
                    }
                    Button(
                        onClick = { onSave(selectedBank, selectedType, amount) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GoldAccent,
                            contentColor = Color.Black
                        ),
                        enabled = !isSaving && amount.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.editSave, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
