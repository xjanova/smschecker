@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.orders

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Show Snackbar when approve/reject action completes
    LaunchedEffect(state.actionResult) {
        state.actionResult?.let { result ->
            val label = result.orderNumber?.let { "#$it" } ?: ""
            val msg = if (result.success) {
                "${result.message} $label"
            } else {
                "Error: ${result.message} $label"
            }
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearActionResult()
        }
    }

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
                    textAlign = TextAlign.Center
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

    Box(modifier = Modifier.fillMaxSize()) {
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

    // Snackbar for approve/reject feedback
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    } // Box
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

// ============================================================================
// OrderCard - การ์ดแสดงบิล พร้อมไอคอน สถานะ สี และอนิเมชั่นที่ชัดเจน
// ============================================================================

/**
 * ข้อมูลการแสดงผลสถานะของบิล
 */
private data class StatusVisuals(
    val icon: ImageVector,
    val color: Color,
    val bgColor: Color,
    val label: String,
    val borderColor: Color
)

/**
 * กำหนด visuals ตามสถานะบิล
 */
@Composable
private fun getStatusVisuals(status: ApprovalStatus): StatusVisuals {
    val strings = LocalAppStrings.current
    return when (status) {
        ApprovalStatus.AUTO_APPROVED -> StatusVisuals(
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF2E7D32),           // เขียวเข้ม
            bgColor = Color(0xFF2E7D32).copy(alpha = 0.08f),
            label = strings.statusAutoApproved,
            borderColor = Color(0xFF2E7D32).copy(alpha = 0.3f)
        )
        ApprovalStatus.MANUALLY_APPROVED -> StatusVisuals(
            icon = Icons.Default.Verified,
            color = Color(0xFF1565C0),            // น้ำเงิน
            bgColor = Color(0xFF1565C0).copy(alpha = 0.08f),
            label = strings.statusApproved,
            borderColor = Color(0xFF1565C0).copy(alpha = 0.3f)
        )
        ApprovalStatus.PENDING_REVIEW -> StatusVisuals(
            icon = Icons.Default.Schedule,
            color = Color(0xFFE65100),            // ส้มเข้ม
            bgColor = Color(0xFFE65100).copy(alpha = 0.06f),
            label = strings.statusPendingReview,
            borderColor = Color(0xFFE65100).copy(alpha = 0.3f)
        )
        ApprovalStatus.REJECTED -> StatusVisuals(
            icon = Icons.Default.Cancel,
            color = Color(0xFFC62828),            // แดงเข้ม
            bgColor = Color(0xFFC62828).copy(alpha = 0.06f),
            label = strings.statusRejected,
            borderColor = Color(0xFFC62828).copy(alpha = 0.3f)
        )
        ApprovalStatus.EXPIRED -> StatusVisuals(
            icon = Icons.Default.TimerOff,
            color = Color(0xFF757575),            // เทา
            bgColor = Color(0xFF757575).copy(alpha = 0.06f),
            label = strings.statusExpired,
            borderColor = Color(0xFF757575).copy(alpha = 0.2f)
        )
        ApprovalStatus.CANCELLED -> StatusVisuals(
            icon = Icons.Default.RemoveCircle,
            color = Color(0xFFBF360C),            // ส้มแดง
            bgColor = Color(0xFFBF360C).copy(alpha = 0.06f),
            label = strings.statusCancelled,
            borderColor = Color(0xFFBF360C).copy(alpha = 0.3f)
        )
        ApprovalStatus.DELETED -> StatusVisuals(
            icon = Icons.Default.Delete,
            color = Color(0xFF9E9E9E),            // เทาอ่อน
            bgColor = Color(0xFF9E9E9E).copy(alpha = 0.04f),
            label = strings.statusDeleted,
            borderColor = Color(0xFF9E9E9E).copy(alpha = 0.15f)
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
    val visuals = getStatusVisuals(order.approvalStatus)

    // อนิเมชั่น pulse สำหรับ PENDING_REVIEW (กะพริบเพื่อดึงดูดความสนใจ)
    val isPending = order.approvalStatus == ApprovalStatus.PENDING_REVIEW
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (isPending) 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // อนิเมชั่นเด้ง สำหรับ AUTO_APPROVED (เพิ่งจับคู่สำเร็จ)
    val isAutoApproved = order.approvalStatus == ApprovalStatus.AUTO_APPROVED
    val bounceScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (isAutoApproved) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceScale"
    )

    // สี border แบบ animate
    val animatedBorderColor by animateColorAsState(
        targetValue = visuals.borderColor,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, animatedBorderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = visuals.bgColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPending) 2.dp else 0.dp
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // แถบสีสถานะด้านซ้าย (gradient)
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(visuals.color, visuals.color.copy(alpha = 0.3f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // === แถวบน: ไอคอนสถานะ + ชื่อเว็บ + เซิร์ฟเวอร์ + Badge สถานะ ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ไอคอนสถานะ พร้อมอนิเมชั่น
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .scale(if (isAutoApproved) bounceScale else 1f)
                                .clip(CircleShape)
                                .background(visuals.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                visuals.icon,
                                contentDescription = visuals.label,
                                modifier = Modifier
                                    .size(20.dp)
                                    .alpha(if (isPending) pulseAlpha else 1f),
                                tint = visuals.color
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = order.websiteName ?: strings.unknown,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            // แสดงชื่อเซิร์ฟเวอร์
                            if (order.serverName != null) {
                                Text(
                                    text = order.serverName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    // Badge สถานะ
                    StatusBadge(order.approvalStatus, visuals)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // === แถวกลาง: เลขบิล + สินค้า ===
                if (order.orderNumber != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AppColors.GoldAccent.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "#${order.orderNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.GoldAccent.copy(alpha = 0.9f)
                        )
                    }
                }
                if (order.productName != null) {
                    Text(
                        text = order.productName + (order.quantity?.let { " x$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // === แถวล่าง: ธนาคาร + ยอดเงิน + เวลา ===
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
                            color = visuals.color
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = formatDate(order.paymentTimestamp ?: order.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // === Ambiguous match warning ===
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

                // === Offline queue indicator ===
                if (order.pendingAction != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = AppColors.InfoBlue
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

                // === ปุ่ม Action สำหรับ PENDING_REVIEW ===
                if (order.approvalStatus == ApprovalStatus.PENDING_REVIEW && order.pendingAction == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFC62828)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFC62828).copy(alpha = 0.4f)),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.rejectButton, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32)
                            ),
                            modifier = Modifier.height(36.dp),
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

// ============================================================================
// StatusBadge - แสดงสถานะเป็น badge พร้อมไอคอนขนาดเล็ก
// ============================================================================

@Composable
private fun StatusBadge(status: ApprovalStatus, visuals: StatusVisuals) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(visuals.color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            visuals.icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = visuals.color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            visuals.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = visuals.color
        )
    }
}

// ============================================================================
// Utility functions
// ============================================================================

private fun formatAmount(amount: Double): String {
    return String.format(Locale.getDefault(), "+\u0E3F%,.2f", amount)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
