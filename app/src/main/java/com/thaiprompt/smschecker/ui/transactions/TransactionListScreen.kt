package com.thaiprompt.smschecker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.ui.components.AeroGlass
import com.thaiprompt.smschecker.ui.components.AeroHeader
import com.thaiprompt.smschecker.ui.components.BankCoin
import com.thaiprompt.smschecker.ui.components.ChromeSegmented
import com.thaiprompt.smschecker.ui.components.GlossButton
import com.thaiprompt.smschecker.ui.components.GlossStyle
import com.thaiprompt.smschecker.ui.components.HeaderTone
import com.thaiprompt.smschecker.ui.components.StatusBarTone
import com.thaiprompt.smschecker.ui.components.aeroHeaderBleed
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TransactionFilter {
    ALL, CREDIT, DEBIT
}

/**
 * Transactions screen — Millennium 3D / Frutiger Aero (design 03).
 * Green header bleed + 3 stat boxes + chrome segmented + day-grouped glass cards.
 */
@Composable
fun TransactionListScreen(viewModel: TransactionListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var selectedFilter by remember { mutableStateOf(TransactionFilter.ALL) }
    val strings = LocalAppStrings.current

    StatusBarTone(HeaderTone.Green)

    val filteredTransactions = when (selectedFilter) {
        TransactionFilter.ALL -> state.transactions
        TransactionFilter.CREDIT -> state.transactions.filter { it.type == TransactionType.CREDIT }
        TransactionFilter.DEBIT -> state.transactions.filter { it.type == TransactionType.DEBIT }
    }
    val dayGroups = remember(filteredTransactions) {
        groupByDay(filteredTransactions, strings.aeroToday, strings.aeroYesterday)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aeroHeaderBleed(HeaderTone.Green)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                AeroHeader {
                    Text(
                        strings.aeroTransactionsTitle,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(13.dp))
                    // credit / debit / total stat boxes (counts over the full list)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        TxnStatBox(
                            label = strings.aeroCreditCount,
                            value = state.transactions.count { it.type == TransactionType.CREDIT },
                            valueColor = AeroPalette.GreenDeep,
                            modifier = Modifier.weight(1f)
                        )
                        TxnStatBox(
                            label = strings.aeroDebitCount,
                            value = state.transactions.count { it.type == TransactionType.DEBIT },
                            valueColor = AeroPalette.Red,
                            modifier = Modifier.weight(1f)
                        )
                        TxnStatBox(
                            label = strings.aeroTotalCount,
                            value = state.transactions.size,
                            valueColor = AeroPalette.NavyDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(11.dp))
                    ChromeSegmented(
                        options = listOf(strings.filterAll, strings.filterCredit, strings.filterDebit),
                        selectedIndex = selectedFilter.ordinal,
                        onSelect = { selectedFilter = TransactionFilter.entries[it] }
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
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
                                strings.noTransactionsFound,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            } else {
                dayGroups.forEach { group ->
                    item(key = "day_${group.dayKey}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                group.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AeroPalette.InkSoft
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(Color(0x99BFD4DE))
                            )
                        }
                    }
                    item(key = "card_${group.dayKey}") {
                        AeroGlass(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            cornerRadius = 18.dp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column {
                                group.transactions.forEachIndexed { index, tx ->
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color(0x8CBFD4DE))
                                        )
                                    }
                                    AeroTxnRow(transaction = tx, strings = strings)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // pagination — the ViewModel supports paging; surface it
                if (state.hasMorePages) {
                    item(key = "load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            } else {
                                GlossButton(
                                    text = strings.loadingMore,
                                    onClick = { viewModel.loadMore() },
                                    style = GlossStyle.Ghost,
                                    fontSize = 13,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/** Small glass stat box in the header (.glass padding 9/11, 10.5px label + 18px number). */
@Composable
private fun TxnStatBox(
    label: String,
    value: Int,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    AeroGlass(
        modifier = modifier,
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Column {
            Text(label, fontSize = 10.5.sp, color = AeroPalette.InkFaint, maxLines = 1)
            Text(
                "$value",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

/** One .txn row: coin + name/account meta + arrow-coded amount + time. */
@Composable
private fun AeroTxnRow(
    transaction: BankTransaction,
    strings: com.thaiprompt.smschecker.ui.theme.AppStrings,
) {
    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) AeroPalette.GreenDeep else AeroPalette.Red
    val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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

private data class DayGroup(
    val dayKey: String,
    val label: String,
    val transactions: List<BankTransaction>,
)

/** Groups transactions by calendar day, labelled today/yesterday/d MMM (Thai). */
private fun groupByDay(
    transactions: List<BankTransaction>,
    todayLabel: String,
    yesterdayLabel: String,
): List<DayGroup> {
    if (transactions.isEmpty()) return emptyList()
    val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayLabelFormat = SimpleDateFormat("d MMM", Locale("th", "TH"))
    val todayKey = dayKeyFormat.format(Date())
    val yesterdayKey = dayKeyFormat.format(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
    )
    return transactions
        .sortedByDescending { it.timestamp }
        .groupBy { dayKeyFormat.format(Date(it.timestamp)) }
        .map { (key, txns) ->
            val dateText = dayLabelFormat.format(Date(txns.first().timestamp))
            val label = when (key) {
                todayKey -> "$todayLabel · $dateText"
                yesterdayKey -> "$yesterdayLabel · $dateText"
                else -> dateText
            }
            DayGroup(dayKey = key, label = label, transactions = txns)
        }
}
