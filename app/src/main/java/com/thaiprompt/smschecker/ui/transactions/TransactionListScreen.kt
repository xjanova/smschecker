package com.thaiprompt.smschecker.ui.transactions

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.premiumBackground
import com.thaiprompt.smschecker.ui.dashboard.TransactionItem
import com.thaiprompt.smschecker.ui.theme.AppColors

enum class TransactionFilter(val label: String) {
    ALL("\u0E17\u0E31\u0E49\u0E07\u0E2B\u0E21\u0E14"),
    CREDIT("\u0E23\u0E32\u0E22\u0E23\u0E31\u0E1A"),
    DEBIT("\u0E23\u0E32\u0E22\u0E08\u0E48\u0E32\u0E22")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(viewModel: TransactionListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var selectedFilter by remember { mutableStateOf(TransactionFilter.ALL) }

    val filteredTransactions = when (selectedFilter) {
        TransactionFilter.ALL -> state.transactions
        TransactionFilter.CREDIT -> state.transactions.filter { it.type == TransactionType.CREDIT }
        TransactionFilter.DEBIT -> state.transactions.filter { it.type == TransactionType.DEBIT }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground()
    ) {
        // Gradient Header
        GradientHeader {
            Text(
                "\u0E23\u0E32\u0E22\u0E01\u0E32\u0E23",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "\u0E1B\u0E23\u0E30\u0E27\u0E31\u0E15\u0E34\u0E01\u0E32\u0E23\u0E0A\u0E33\u0E23\u0E30\u0E40\u0E07\u0E34\u0E19",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.GoldAccent
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips
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
                        Text(filter.label, style = MaterialTheme.typography.bodySmall)
                    },
                    leadingIcon = if (selectedFilter == filter) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.GoldAccent.copy(alpha = 0.25f),
                        selectedLabelColor = AppColors.GoldAccent,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = AppColors.GlassCardBorder,
                        selectedBorderColor = AppColors.GoldAccent.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats Row
        GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "\u0E17\u0E31\u0E49\u0E07\u0E2B\u0E21\u0E14",
                    value = "${filteredTransactions.size}",
                    icon = Icons.Default.Receipt
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(AppColors.GlassCardBorder)
                )
                StatItem(
                    label = "\u0E0B\u0E34\u0E07\u0E04\u0E4C\u0E41\u0E25\u0E49\u0E27",
                    value = "${filteredTransactions.count { it.isSynced }}",
                    icon = Icons.Default.CloudDone
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(AppColors.GlassCardBorder)
                )
                StatItem(
                    label = "\u0E23\u0E2D\u0E14\u0E33\u0E40\u0E19\u0E34\u0E19\u0E01\u0E32\u0E23",
                    value = "${filteredTransactions.count { !it.isSynced }}",
                    icon = Icons.Default.CloudOff
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transaction List
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "\u0E44\u0E21\u0E48\u0E1E\u0E1A\u0E23\u0E32\u0E22\u0E01\u0E32\u0E23",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AppColors.GoldAccent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AppColors.GoldAccent
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
