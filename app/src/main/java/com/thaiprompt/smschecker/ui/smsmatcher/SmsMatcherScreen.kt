package com.thaiprompt.smschecker.ui.smsmatcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import com.thaiprompt.smschecker.ui.components.BankLogoCircle
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.SectionTitle
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsMatcherScreen(
    onBack: () -> Unit,
    viewModel: SmsMatcherViewModel = hiltViewModel()
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
        item {
            GradientHeader {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.backButton, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            strings.smsMatcherTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            strings.smsMatcherSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.GoldAccent
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Active Rules Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(strings.activeRules, showDivider = false)
                FilledTonalButton(
                    onClick = { viewModel.showAddRuleDialog("", "") },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                        contentColor = AppColors.GoldAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.addRule, fontSize = 12.sp)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        if (state.rules.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Sms,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            strings.noRules,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            strings.noRulesDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(state.rules) { rule ->
            RuleCard(
                rule = rule,
                onToggle = { viewModel.toggleRule(rule) },
                onDelete = { viewModel.deleteRule(rule) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Recent SMS Section
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            SectionTitle(
                strings.recentSmsTitle,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }

        if (state.uniqueSenders.isEmpty() && !state.isLoading) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "No SMS messages found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(state.uniqueSenders) { (sender, sample) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .clickable { viewModel.showAddRuleDialog(sender, sample) },
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppColors.GoldAccent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = null,
                            tint = AppColors.GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            sender,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            sample,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = strings.tapToAssign,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // Add Rule Dialog
    if (state.showAddDialog) {
        AddRuleDialog(
            initialSender = state.selectedSender,
            initialSample = state.selectedSample,
            onDismiss = { viewModel.hideAddRuleDialog() },
            onConfirm = { sender, bankCode, sample ->
                viewModel.addRule(sender, bankCode, sample)
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: SmsSenderRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
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
            BankLogoCircle(bankCode = rule.bankCode, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.senderAddress,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    rule.bankCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.GoldAccent,
                    fontSize = 11.sp
                )
                if (rule.sampleMessage.isNotBlank()) {
                    Text(
                        rule.sampleMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }
            }
            Switch(
                checked = rule.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AppColors.CreditGreen,
                    checkedTrackColor = AppColors.CreditGreen.copy(alpha = 0.3f)
                )
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.deleteRule,
                    tint = AppColors.DebitRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.deleteRule) },
            text = { Text(strings.deleteRuleConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(strings.removeButton, color = AppColors.DebitRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancelButton)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    initialSender: String,
    initialSample: String,
    onDismiss: () -> Unit,
    onConfirm: (sender: String, bankCode: String, sampleMessage: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var sender by remember { mutableStateOf(initialSender) }
    var sampleMessage by remember { mutableStateOf(initialSample) }
    var selectedBank by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val banks = listOf(
        "KBANK" to strings.bankKbank,
        "SCB" to strings.bankScb,
        "KTB" to strings.bankKtb,
        "BBL" to strings.bankBbl,
        "GSB" to strings.bankGsb,
        "BAY" to strings.bankBay,
        "TTB" to strings.bankTtb,
        "PROMPTPAY" to strings.bankPromptPay
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    strings.assignBank,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GoldAccent
                )

                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text(strings.senderAddressLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null) }
                )

                // Bank selector dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = banks.find { it.first == selectedBank }?.let { "${it.first} - ${it.second}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.selectBankLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        leadingIcon = {
                            if (selectedBank.isNotEmpty()) {
                                BankLogoCircle(bankCode = selectedBank, size = 24.dp)
                            } else {
                                Icon(Icons.Default.AccountBalance, contentDescription = null)
                            }
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        banks.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BankLogoCircle(bankCode = code, size = 24.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("$code - $name")
                                    }
                                },
                                onClick = {
                                    selectedBank = code
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = sampleMessage,
                    onValueChange = { sampleMessage = it },
                    label = { Text(strings.sampleMessageLabel) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancelButton)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(sender, selectedBank, sampleMessage) },
                        enabled = sender.isNotBlank() && selectedBank.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GoldAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.saveButton)
                    }
                }
            }
        }
    }
}
