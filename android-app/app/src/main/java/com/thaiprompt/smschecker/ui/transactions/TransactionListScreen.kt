package com.thaiprompt.smschecker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.ui.components.BankLogoCircle
import com.thaiprompt.smschecker.ui.components.BankVisuals
import com.thaiprompt.smschecker.ui.dashboard.TransactionItem
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.util.CsvExporter

enum class TransactionFilter(val label: String) {
    ALL("All"),
    CREDIT("Income"),
    DEBIT("Expense")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(viewModel: TransactionListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(TransactionFilter.ALL) }
    var selectedBank by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = state.transactions.filter { tx ->
        val matchesType = when (selectedFilter) {
            TransactionFilter.ALL -> true
            TransactionFilter.CREDIT -> tx.type == TransactionType.CREDIT
            TransactionFilter.DEBIT -> tx.type == TransactionType.DEBIT
        }
        val matchesBank = selectedBank == null || tx.bank.equals(selectedBank, ignoreCase = true)
        val matchesSearch = searchQuery.isBlank() ||
                tx.bank.contains(searchQuery, ignoreCase = true) ||
                tx.amount.toString().contains(searchQuery) ||
                tx.senderOrReceiver.contains(searchQuery, ignoreCase = true) ||
                tx.referenceNumber.contains(searchQuery, ignoreCase = true)
        matchesType && matchesBank && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar with Export button
        TopAppBar(
            title = {
                Text(
                    "Transactions",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                if (state.transactions.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            CsvExporter.exportAndShare(context, filteredTransactions)
                        }
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Export CSV",
                            tint = AppColors.GoldAccent
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search") },
            placeholder = { Text("Amount, bank, reference...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Type Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            filter.label,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = if (selectedFilter == filter) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                        selectedLabelColor = AppColors.GoldAccent
                    )
                )
            }
        }

        // Bank Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedBank == null,
                    onClick = { selectedBank = null },
                    label = { Text("All Banks") },
                    leadingIcon = if (selectedBank == null) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                        selectedLabelColor = AppColors.GoldAccent
                    )
                )
            }
            items(BankVisuals.allBanks()) { bank ->
                FilterChip(
                    selected = selectedBank == bank.code,
                    onClick = {
                        selectedBank = if (selectedBank == bank.code) null else bank.code
                    },
                    label = { Text(bank.code) },
                    leadingIcon = {
                        BankLogoCircle(bankCode = bank.code, size = 20.dp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = bank.brandColor.copy(alpha = 0.2f),
                        selectedLabelColor = bank.brandColor
                    )
                )
            }
        }

        // Stats Row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Total",
                    value = "${filteredTransactions.size}",
                    icon = Icons.Default.Receipt
                )
                StatItem(
                    label = "Synced",
                    value = "${filteredTransactions.count { it.isSynced }}",
                    icon = Icons.Default.CloudDone
                )
                StatItem(
                    label = "Pending",
                    value = "${filteredTransactions.count { !it.isSynced }}",
                    icon = Icons.Default.CloudOff
                )
            }
        }

        // Transaction List
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank() || selectedBank != null) "No matching transactions"
                        else "No transactions found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransactions) { transaction ->
                    TransactionItem(transaction = transaction)
                }
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.GoldAccent
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
