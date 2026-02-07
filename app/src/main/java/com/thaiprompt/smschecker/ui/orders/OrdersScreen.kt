@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.MatchConfidence
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.ui.components.BankLogoCircle
import com.thaiprompt.smschecker.ui.components.DateRangePickerDialog
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val strings = LocalAppStrings.current

    // Error handling - show error state if there's an error
    if (state.error != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(premiumBackgroundBrush()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = AppColors.WarningOrange
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "เกิดข้อผิดพลาด",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    state.error ?: "Unknown error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ลองอีกครั้ง")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Gradient Header
        item {
            GradientHeader {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            strings.ordersTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            strings.approvalManagement,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF66BB6A) // Light green accent
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.pendingCount > 0) {
                            FilledTonalButton(
                                onClick = { viewModel.bulkApproveAll() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = AppColors.CreditGreen.copy(alpha = 0.2f),
                                    contentColor = AppColors.CreditGreen
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.approveAll, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF66BB6A)
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = strings.refresh, tint = Color(0xFF66BB6A))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Status Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                val filters = listOf<Pair<String, ApprovalStatus?>>(
                    strings.filterAll to null,
                    strings.filterPendingReview to ApprovalStatus.PENDING_REVIEW,
                    strings.filterAutoApproved to ApprovalStatus.AUTO_APPROVED,
                    strings.filterApproved to ApprovalStatus.MANUALLY_APPROVED,
                    strings.filterRejected to ApprovalStatus.REJECTED,
                    strings.statusCancelled to ApprovalStatus.CANCELLED
                )
                items(filters) { (label, status) ->
                    val isSelected = state.statusFilter == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setStatusFilter(status) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.GoldAccent.copy(alpha = 0.25f),
                            selectedLabelColor = AppColors.GoldAccent,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AppColors.GoldAccent.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Server Filter + Date Range
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.servers.size > 1) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = state.serverFilter == null,
                                onClick = { viewModel.setServerFilter(null) },
                                label = { Text(strings.filterAll, fontSize = 12.sp) }
                            )
                        }
                        items(state.servers) { server ->
                            FilterChip(
                                selected = state.serverFilter == server.id,
                                onClick = { viewModel.setServerFilter(server.id) },
                                label = { Text(server.name, fontSize = 12.sp) }
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    border = ButtonDefaults.outlinedButtonBorder,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (state.dateFrom != null) strings.filtered else strings.filterDate,
                        fontSize = 12.sp
                    )
                }

                if (state.dateFrom != null) {
                    IconButton(
                        onClick = { viewModel.clearDateRange() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = strings.clearFilter, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Stats summary card
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(strings.totalLabel, state.orders.size, MaterialTheme.colorScheme.onBackground)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    StatItem(strings.pendingCheck, state.pendingCount, AppColors.WarningOrange)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    StatItem(strings.offlineQueue, state.offlineQueueCount, AppColors.InfoBlue)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Empty state
        if (state.orders.isEmpty() && !state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
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
                                Icons.Default.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            strings.noOrders,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            strings.matchedOrdersWillShow,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Order list
        items(
            items = state.orders,
            key = { "order_${it.id}" }
        ) { order ->
            OrderCard(
                order = order,
                onApprove = { viewModel.approveOrder(order) },
                onReject = { viewModel.rejectOrder(order) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    // Date picker dialog
    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { start, end ->
                viewModel.setDateRange(start, end)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
fun OrderCard(
    order: OrderApproval,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val statusColor = when (order.approvalStatus) {
        ApprovalStatus.AUTO_APPROVED, ApprovalStatus.MANUALLY_APPROVED -> AppColors.CreditGreen
        ApprovalStatus.PENDING_REVIEW -> AppColors.WarningOrange
        ApprovalStatus.REJECTED -> AppColors.DebitRed
        ApprovalStatus.EXPIRED -> Color.Gray
        ApprovalStatus.CANCELLED -> Color(0xFFFF6F00)
        ApprovalStatus.DELETED -> Color.Gray
    }

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
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Status color bar with gradient
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(statusColor, statusColor.copy(alpha = 0.4f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Top row: website + server name + status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.websiteName ?: strings.unknown,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // แสดงชื่อเซิร์ฟเวอร์ที่บิลมาจาก
                        if (order.serverName != null) {
                            Text(
                                text = order.serverName,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                    StatusBadge(order.approvalStatus)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Order number + product
                if (order.orderNumber != null) {
                    Text(
                        text = "#${order.orderNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.GoldAccent.copy(alpha = 0.8f)
                    )
                }
                if (order.productName != null) {
                    Text(
                        text = order.productName + (order.quantity?.let { " x$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom row: bank + amount + date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (order.bank != null) {
                            BankLogoCircle(bankCode = order.bank, size = 28.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = formatAmount(order.amount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.CreditGreen
                        )
                    }
                    Text(
                        text = formatDate(order.paymentTimestamp ?: order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                // Ambiguous warning
                if (order.confidence == MatchConfidence.AMBIGUOUS) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.WarningOrange.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = AppColors.WarningOrange
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            strings.ambiguousMatch,
                            fontSize = 10.sp,
                            color = AppColors.WarningOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Offline queue indicator
                if (order.pendingAction != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AppColors.InfoBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${strings.queuedLabel}: ${order.pendingAction.name}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = AppColors.InfoBlue
                        )
                    }
                }

                // Action buttons for pending orders
                if (order.approvalStatus == ApprovalStatus.PENDING_REVIEW && order.pendingAction == null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.DebitRed),
                            border = ButtonDefaults.outlinedButtonBorder,
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text(strings.rejectButton, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.CreditGreen
                            ),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.approveButton, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ApprovalStatus) {
    val strings = LocalAppStrings.current
    val (color, text) = when (status) {
        ApprovalStatus.AUTO_APPROVED -> AppColors.CreditGreen to strings.statusAutoApproved
        ApprovalStatus.MANUALLY_APPROVED -> AppColors.InfoBlue to strings.statusApproved
        ApprovalStatus.PENDING_REVIEW -> AppColors.WarningOrange to strings.statusPendingReview
        ApprovalStatus.REJECTED -> AppColors.DebitRed to strings.statusRejected
        ApprovalStatus.EXPIRED -> Color.Gray to strings.statusExpired
        ApprovalStatus.CANCELLED -> Color(0xFFFF6F00) to strings.statusCancelled
        ApprovalStatus.DELETED -> Color.Gray to strings.statusDeleted
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun formatAmount(amount: Double): String {
    return String.format(Locale.getDefault(), "+\u0E3F%,.2f", amount)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
