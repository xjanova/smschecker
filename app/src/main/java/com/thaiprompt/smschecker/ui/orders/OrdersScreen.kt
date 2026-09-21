@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.data.model.ApprovalMethod
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.MatchConfidence
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.model.approvalMethod
import com.thaiprompt.smschecker.data.repository.SlipImageLoader
import com.thaiprompt.smschecker.ui.components.AeroChip
import com.thaiprompt.smschecker.ui.components.AeroEmptyState
import com.thaiprompt.smschecker.ui.components.AeroGlass
import com.thaiprompt.smschecker.ui.components.AeroHeader
import com.thaiprompt.smschecker.ui.components.AeroPillChip
import com.thaiprompt.smschecker.ui.components.BankCoin
import com.thaiprompt.smschecker.ui.components.ChipStyle
import com.thaiprompt.smschecker.ui.components.ChromeSegmented
import com.thaiprompt.smschecker.ui.components.DateRangePickerDialog
import com.thaiprompt.smschecker.ui.components.GlossButton
import com.thaiprompt.smschecker.ui.components.GlossIconButton
import com.thaiprompt.smschecker.ui.components.GlossStyle
import com.thaiprompt.smschecker.ui.components.GlossyOrb
import com.thaiprompt.smschecker.ui.components.HeaderTone
import com.thaiprompt.smschecker.ui.components.SiteNameChip
import com.thaiprompt.smschecker.ui.components.StatusBarTone
import com.thaiprompt.smschecker.ui.components.aeroHeaderBleed
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import com.thaiprompt.smschecker.util.ServerLabel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Orders — Millennium 3D / Frutiger Aero (design 02).
 * Navy header bleed, chrome segmented (รอ/อนุมัติ/ทั้งหมด), glass order cards
 * with the green-decimal unique amount, action strip, and a floating bulk bar.
 */
@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    // 🌐 เซิร์ฟเวอร์ที่ลงทะเบียนเครื่องไว้ → ป้าย "มาจากเว็บไหน" บนการ์ดบิล (จับคู่ด้วย serverId)
    val serversById = remember(state.servers) { state.servers.associateBy { it.id } }

    StatusBarTone(HeaderTone.Navy)

    // Show Snackbar when approve/reject action completes
    LaunchedEffect(state.actionResult) {
        state.actionResult?.let { result ->
            val label = result.orderNumber?.let { "#$it" } ?: ""
            val msg = "${result.message} $label"
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearActionResult()
        }
    }

    // Error handling - show error state if there's an error
    if (state.hasLoadError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aeroHeaderBleed(HeaderTone.Navy),
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
                    strings.ordersErrorTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    strings.ordersErrorBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                GlossButton(
                    text = strings.ordersErrorRetry,
                    onClick = { viewModel.refresh() },
                    style = GlossStyle.Green,
                    leadingIcon = Icons.Default.Refresh
                )
            }
        }
        return
    }

    val segIndex = when (state.statusFilter) {
        ApprovalStatus.PENDING_REVIEW -> 0
        ApprovalStatus.AUTO_APPROVED, ApprovalStatus.MANUALLY_APPROVED -> 1
        else -> 2
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aeroHeaderBleed(HeaderTone.Navy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            // ── navy app bar: title + search/refresh orbs + chrome segmented ──
            item(key = "header") {
                AeroHeader {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.aeroOrdersTitle,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "${state.pendingCount} ${strings.aeroPendingApprovalSuffix}",
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 1
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // refresh orb (kept feature; shows spinner while refreshing)
                            GlossyOrb(
                                gradient = listOf(Color(0x99FFFFFF), Color(0x22FFFFFF)),
                                size = 38.dp,
                                modifier = Modifier.clickable { viewModel.refresh() }
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(17.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2A3A52)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = strings.refresh,
                                        tint = Color(0xFF2A3A52),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            // search orb — toggles the search/filter panel
                            GlossyOrb(
                                gradient = listOf(Color(0x99FFFFFF), Color(0x22FFFFFF)),
                                size = 38.dp,
                                modifier = Modifier.clickable { showSearch = !showSearch }
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = strings.searchPlaceholder,
                                    tint = Color(0xFF2A3A52),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    ChromeSegmented(
                        options = listOf(
                            "${strings.aeroSegPending} ${state.pendingCount}",
                            strings.aeroSegApproved,
                            strings.aeroSegAll
                        ),
                        selectedIndex = segIndex,
                        onSelect = { index ->
                            when (index) {
                                0 -> viewModel.setStatusFilter(ApprovalStatus.PENDING_REVIEW)
                                1 -> viewModel.setStatusFilter(ApprovalStatus.AUTO_APPROVED)
                                else -> viewModel.setStatusFilter(null)
                            }
                        }
                    )
                }
            }

            // ── expandable search / extended filters panel ──
            if (showSearch) {
                item(key = "search_panel") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(strings.searchPlaceholder, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = AeroPalette.InkFaint
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = strings.clearFilter,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AeroPalette.Green,
                                unfocusedBorderColor = Color(0xB3FFFFFF),
                                focusedContainerColor = Color(0xB3FFFFFF),
                                unfocusedContainerColor = Color(0x8CFFFFFF)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // full status filters (รวมปฏิเสธ/ยกเลิก) — reachable when expanded
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val filters = listOf<Pair<String, ApprovalStatus?>>(
                                strings.filterAll to null,
                                strings.filterPendingReview to ApprovalStatus.PENDING_REVIEW,
                                strings.filterAutoApproved to ApprovalStatus.AUTO_APPROVED,
                                strings.filterApproved to ApprovalStatus.MANUALLY_APPROVED,
                                strings.filterRejected to ApprovalStatus.REJECTED,
                                strings.statusCancelled to ApprovalStatus.CANCELLED,
                                // 🗑 (2026-06-12) ถังขยะ — บิลที่ถูกย้ายมารอลบถาวร ยังกด Force อนุมัติได้
                                "🗑 ${strings.statusDeleted}" to ApprovalStatus.DELETED
                            )
                            items(filters) { (label, status) ->
                                AeroPillChip(
                                    text = label,
                                    selected = state.statusFilter == status,
                                    onClick = { viewModel.setStatusFilter(status) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.servers.size > 1) {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        AeroPillChip(
                                            text = strings.filterAll,
                                            selected = state.serverFilter == null,
                                            onClick = { viewModel.setServerFilter(null) }
                                        )
                                    }
                                    items(state.servers) { server ->
                                        AeroPillChip(
                                            text = server.displayName(),
                                            selected = state.serverFilter == server.id,
                                            onClick = { viewModel.setServerFilter(server.id) }
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            GlossButton(
                                text = if (state.dateFrom != null) strings.filtered else strings.filterDate,
                                onClick = { showDatePicker = true },
                                style = GlossStyle.Ghost,
                                leadingIcon = Icons.Default.DateRange,
                                fontSize = 12,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            )
                            if (state.dateFrom != null) {
                                GlossIconButton(
                                    icon = Icons.Default.Close,
                                    onClick = { viewModel.clearDateRange() },
                                    style = GlossStyle.Ghost,
                                    size = 32.dp,
                                    contentDescription = strings.clearFilter
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // ── empty state ──
            if (state.orders.isEmpty() && !state.isLoading) {
                item(key = "empty") {
                    AeroEmptyState(
                        art = R.drawable.art_empty_orders,
                        title = strings.noOrders,
                        subtitle = strings.matchedOrdersWillShow
                    )
                }
            }

            // ── order cards ──
            items(
                items = state.orders,
                key = { "order_${it.id}" }
            ) { order ->
                OrderCard(
                    order = order,
                    server = serversById[order.serverId],
                    onApprove = { viewModel.approveOrder(order) },
                    onForceApprove = { viewModel.forceApproveOrder(order) },
                    onReject = { viewModel.rejectOrder(order) },
                    onVoidApproval = { viewModel.voidApproval(order) },
                    onLoadSlip = { target, sizePx -> viewModel.loadSlipImage(target, sizePx) },
                    isVoiding = state.voidingOrderId == order.id,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── load more (manual paging) ──
            if (state.hasMorePages && !state.isLoading) {
                item(key = "load_more_button") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isLoadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = AeroPalette.GreenLo
                            )
                        } else {
                            GlossButton(
                                text = "โหลดต่อ (${state.orders.size}/${state.totalCount})",
                                onClick = { viewModel.loadMoreOrders() },
                                style = GlossStyle.Ghost,
                                fontSize = 13,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // (2026-06-11) แถบลอย "อนุมัติทั้งหมด" ถูกถอดออกตามคำสั่ง owner —
        // ไม่ได้ใช้งานจริงและเสี่ยงกดพลาดอนุมัติยกชุด ปุ่มรายการ์ดยังอยู่ครบ

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

        // 🚫 (2026-07-27) ยืนยันรอบสอง — backend ตอบว่าลูกค้าเปิดไพ่/ได้คำทำนายไปแล้ว
        state.voidConsumedConfirm?.let { pendingOrder ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissVoidConsumedConfirm() },
                icon = {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                },
                title = { Text(strings.voidConsumedTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "บิล ${pendingOrder.orderNumber ?: "#${pendingOrder.id}"}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(strings.voidConsumedBody, fontSize = 13.sp, color = Color(0xFF666666))
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.voidApproval(pendingOrder, force = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text(strings.voidConsumedConfirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissVoidConsumedConfirm() }) {
                        Text(strings.cancelButton)
                    }
                }
            )
        }

        // Snackbar for approve/reject feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ============================================================================
// OrderCard — AeroGlass card: header row, green-decimal amount, meta row,
// gloss action strip (design 02)
// ============================================================================

@Composable
fun OrderCard(
    order: OrderApproval,
    server: ServerConfig? = null,
    onApprove: () -> Unit,
    onForceApprove: () -> Unit,
    onReject: () -> Unit,
    onVoidApproval: () -> Unit = {},
    onLoadSlip: suspend (OrderApproval, Int) -> android.graphics.Bitmap? = { _, _ -> null },
    isVoiding: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    // 🧾 (2026-07-27) สลิป SlipOK ของบิลนี้ — ทัมบ์เนลเล็กบนการ์ด + แตะการ์ดดูรูปเต็ม
    val hasSlip = order.slipImagePath != null
    var slipThumb by remember(order.id, order.slipImagePath) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    var showSlipViewer by remember(order.id) { mutableStateOf(false) }

    LaunchedEffect(order.id, order.slipImagePath) {
        slipThumb = if (hasSlip) onLoadSlip(order, SlipImageLoader.THUMB_PX) else null
    }

    if (showSlipViewer && hasSlip) {
        SlipViewerDialog(
            order = order,
            thumbnail = slipThumb,
            onLoadSlip = onLoadSlip,
            onDismiss = { showSlipViewer = false }
        )
    }

    // 🚫 (2026-07-27) ยืนยันก่อนยกเลิกการอนุมัติ (destructive — ดึงเงิน/คอมมิชชั่นคืน)
    var showVoidDialog by remember(order.id) { mutableStateOf(false) }
    if (showVoidDialog) {
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            icon = {
                Icon(Icons.Default.Undo, contentDescription = null, tint = Color(0xFFC62828))
            },
            title = { Text(strings.voidApprovalTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "บิล ${order.orderNumber ?: "#${order.id}"} · ฿${String.format(Locale.US, "%,.2f", order.amount)}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.voidApprovalBody, fontSize = 13.sp, color = Color(0xFF666666))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(strings.voidApprovalHint, fontSize = 12.sp, color = Color(0xFF888888))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVoidDialog = false
                        onVoidApproval()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text(strings.voidApprovalConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidDialog = false }) {
                    Text(strings.cancelButton)
                }
            }
        )
    }

    // Force Approve confirmation dialog (destructive — keeps its confirm step)
    var showForceApproveDialog by remember { mutableStateOf(false) }
    if (showForceApproveDialog) {
        AlertDialog(
            onDismissRequest = { showForceApproveDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF6F00))
            },
            title = { Text("🚀 Force Approve", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "อนุมัติบิล ${order.orderNumber ?: "#${order.id}"} โดยไม่ผ่านการจับคู่ SMS หรือไม่?",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ใช้กรณี: ลูกค้าโอนยอดผิด / SMS หาย / UPA mismatch",
                        fontSize = 13.sp,
                        color = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "⚠️ ระบบจะ approve ทันที + log audit ทุกครั้ง — กลับคืนไม่ได้",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForceApproveDialog = false
                        onForceApprove()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("🚀 ยืนยัน Force Approve")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceApproveDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    val isPending = order.approvalStatus == ApprovalStatus.PENDING_REVIEW
    // 🚀 (2026-06-12) Force อนุมัติได้ตลอดที่บิลยังไม่ถูกลบถาวร — เจ้าของสั่ง
    //   บิลหมดอายุ/ยกเลิก/ปฏิเสธ/อยู่ถังขยะ (ก่อนลบจริง 7 วัน) → ลูกค้าโอนช้าก็กด Force เปิดไพ่ให้ได้
    //   ปุ่มเขียวอนุมัติปกติ + ปุ่มปฏิเสธ ยังแสดงเฉพาะบิล pending เหมือนเดิม
    val canForce = order.pendingAction == null && order.approvalStatus in setOf(
        ApprovalStatus.PENDING_REVIEW,
        ApprovalStatus.EXPIRED,
        ApprovalStatus.CANCELLED,
        ApprovalStatus.REJECTED,
        ApprovalStatus.DELETED
    )
    val showActions = canForce

    // 🚫 (2026-07-27) ปุ่มเล็ก "ยกเลิกการอนุมัติ" — เฉพาะบิลที่อนุมัติแล้วและ server อนุญาต
    val isApproved = order.approvalStatus == ApprovalStatus.AUTO_APPROVED ||
        order.approvalStatus == ApprovalStatus.MANUALLY_APPROVED
    val showVoidAction = isApproved && order.canVoid && order.pendingAction == null

    AeroGlass(
        modifier = modifier
            .fillMaxWidth()
            // แตะการ์ดที่มีสลิป = เปิดดูรูปเต็ม (ไม่มีสลิป = การ์ดไม่ clickable ไม่ให้ ripple หลอก)
            .then(if (hasSlip) Modifier.clickable { showSlipViewer = true } else Modifier),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Column(
                // ระยะขอบเท่ากันทุกด้าน + ทุกช่องไฟในการ์ดเป็นพหุคูณของ 4dp
                // (ของเดิม 15/13/4/9/12 ปนกัน ตาจับจังหวะไม่ได้ การ์ดเลยดู "ขยับ ๆ")
                modifier = Modifier.padding(16.dp)
            ) {
                // ── header: order# / customer · channel + status chip ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // เลขบิล = บรรทัดนำสายตา (เล็ก จาง) / ชื่อลูกค้า = พระเอกของแถวนี้
                        // ของเดิม 12 กับ 13.5sp ต่างกันแค่ 1.5 มองเผิน ๆ เป็นน้ำหนักเดียวกันหมด
                        Text(
                            "ออเดอร์ #${order.orderNumber ?: order.id}",
                            fontSize = 11.5.sp,
                            color = AeroPalette.InkFaint,
                            maxLines = 1
                        )
                        // ชื่อเว็บย้ายไปแถว ServerSourceRow ด้านล่าง — ของเดิมต่อท้ายชื่อลูกค้า
                        // ในบรรทัดเดียว (maxLines 1) ชื่อลูกค้ายาวเมื่อไหร่ ชื่อเว็บถูกตัดหายทุกครั้ง
                        Text(
                            order.customerName?.takeIf { it.isNotBlank() } ?: strings.unknown,
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AeroPalette.Ink,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusAeroChip(order = order, strings = strings)
                        // badge วิธีอนุมัติ — แสดงเฉพาะบิลที่อนุมัติแล้ว
                        order.approvalMethod()?.let { method ->
                            Spacer(modifier = Modifier.height(4.dp))
                            val (label, icon) = when (method) {
                                ApprovalMethod.SMS -> strings.approvedViaSms to Icons.Default.Sms
                                ApprovalMethod.SLIP -> strings.approvedViaSlip to Icons.Default.ReceiptLong
                                ApprovalMethod.ADMIN -> strings.approvedViaAdmin to Icons.Default.Person
                            }
                            AeroChip(label, style = ChipStyle.Aqua, leadingIcon = icon)
                        }
                    }
                }

                // 🌐 (2026-09-21) มาจากเว็บ/เซิร์ฟเวอร์ไหน — เครื่องเดียวลงทะเบียนหลายเว็บ
                //    (Thaiprompt + จันทรา.online ใช้บัญชีธนาคารเดียวกัน) ต้องรู้ทันทีว่าบิลนี้ของใคร
                ServerSourceRow(
                    order = order,
                    server = server,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // product line (+ Pay-Later privilege badge) — business info preserved
                if (order.productName != null) {
                    val isPayLater = order.productName.contains("💎ดูก่อนจ่าย")
                    val cleanProductName = if (isPayLater) {
                        order.productName.replace(" 💎ดูก่อนจ่าย", "")
                    } else order.productName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = cleanProductName + (order.quantity?.let { " x$it" } ?: ""),
                            fontSize = 11.5.sp,
                            color = AeroPalette.InkSoft,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPayLater) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.GoldAccent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AppColors.GoldAccent.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "💎 ดูก่อนจ่าย",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.GoldAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // ── amount row: label + green-decimal amount + bank coin ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isPending) strings.aeroUniqueDecimal
                            else "${strings.aeroReceivedAt} ${formatDate(order.paymentTimestamp ?: order.createdAt)}",
                            fontSize = 11.5.sp,
                            color = AeroPalette.InkFaint,
                            maxLines = 1
                        )
                        UniqueDecimalAmount(
                            amount = order.amount,
                            status = order.approvalStatus
                        )
                    }
                    // 🧾 ทัมบ์เนลสลิป — แตะการ์ดเพื่อดูเต็ม (โชว์เฉพาะบิลที่มีสลิปตรวจผ่าน)
                    //    เล็กกว่าเหรียญธนาคารเล็กน้อย เพื่อไม่เบียดยอดเงินบนจอแคบ
                    // ย่อเหรียญธนาคาร/ทัมบ์เนลลงเล็กน้อย — ของเดิม 46dp แย่งสายตากับยอดเงิน
                    // ซึ่งเป็นข้อมูลสำคัญที่สุดบนการ์ด (ร้านกวาดตาหายอดก่อนเสมอ)
                    if (hasSlip) {
                        SlipThumbnail(bitmap = slipThumb, size = 38.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (order.bank != null) {
                        BankCoin(bankCode = order.bank, size = 42.dp)
                    }
                }

                // ── meta row: time + platform badge + offline-queue flag ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = AeroPalette.InkFaint
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        formatDate(order.paymentTimestamp ?: order.createdAt),
                        fontSize = 11.5.sp,
                        color = AeroPalette.InkFaint
                    )
                    // 🏬 เพจ/สาขาที่บิลนี้มาจาก — ชิดขวา ยืดหดตามที่ว่าง (ชื่อยาวตัดเป็น …)
                    //    เพจหลัก = ชิปเทากลมกลืน / เพจสาขา = ชิปม่วงเด่น ให้กวาดตาแยกได้ทันที
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        order.branchName?.takeIf { it.isNotBlank() }?.let { name ->
                            BranchChip(
                                name = name,
                                isDefault = order.branchIsDefault != false,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    // 📱 ช่องทางที่ลูกค้าทักมา (Facebook / LINE) — โลโก้จริง
                    order.platform?.let { platform ->
                        Spacer(modifier = Modifier.width(6.dp))
                        PlatformChip(platform = platform)
                    }
                    if (order.pendingAction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = AppColors.InfoBlue
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${strings.queuedLabel}: ${order.pendingAction.name}",
                            fontSize = 10.sp,
                            color = AppColors.InfoBlue
                        )
                    }
                }

                // ── ambiguous match warning ──
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
            }

            // ── (2026-07-27) แถบบิลที่อนุมัติแล้ว: hint แตะดูสลิป + ปุ่มเล็กยกเลิกการอนุมัติ ──
            if (showVoidAction) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .height(1.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x99F0F6FA), Color(0x80E8F1F6))
                            )
                        )
                        // ขอบซ้ายต้องตรงกับเนื้อการ์ด (16dp) — ของเดิม 14dp ทำให้เส้นสายตาเยื้อง
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasSlip) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = AeroPalette.InkFaint
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            strings.slipTapHint,
                            fontSize = 11.sp,
                            color = AeroPalette.InkFaint,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (isVoiding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AeroPalette.Red
                        )
                    } else {
                        GlossButton(
                            text = strings.voidApprovalButton,
                            onClick = { showVoidDialog = true },
                            style = GlossStyle.Ghost,
                            leadingIcon = Icons.Default.Undo,
                            fontSize = 11,
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // ── action strip: tinted band, white top divider ──
            //   Force = ทุกบิลที่ยังไม่ถูกลบถาวร / เขียวอนุมัติ+ปฏิเสธ = pending เท่านั้น
            if (showActions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .height(1.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x99F0F6FA), Color(0x80E8F1F6))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Force Approve — primary action (used most often); confirm dialog above
                    GlossButton(
                        text = strings.forceApproveButton,
                        onClick = { showForceApproveDialog = true },
                        style = GlossStyle.Orange,
                        leadingIcon = Icons.Default.Bolt,
                        fontSize = 16,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        // 🚀 (2026-06-12) บิลไม่ pending (หมดอายุ/ยกเลิก/ถังขยะ) → hint บอกว่า Force กู้บิลได้
                        if (isPending) strings.forceApproveHint
                        else {
                            val statusLabel = when (order.approvalStatus) {
                                ApprovalStatus.EXPIRED -> "หมดอายุแล้ว"
                                ApprovalStatus.CANCELLED -> "ถูกยกเลิกแล้ว"
                                ApprovalStatus.REJECTED -> "ถูกปฏิเสธแล้ว"
                                ApprovalStatus.DELETED -> "อยู่ในถังขยะ"
                                else -> ""
                            }
                            "บิลนี้$statusLabel — กด Force อนุมัติได้ตลอดจนกว่าบิลจะถูกลบถาวร"
                        },
                        fontSize = 10.sp,
                        color = AeroPalette.InkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                    // ปุ่มเขียวอนุมัติปกติ + ปฏิเสธ — เฉพาะบิล pending (ของเดิม)
                    if (isPending) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlossButton(
                                text = strings.approveButton,
                                onClick = onApprove,
                                style = GlossStyle.Green,
                                leadingIcon = Icons.Default.Check,
                                fontSize = 13,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                modifier = Modifier.weight(1f)
                            )
                            GlossIconButton(
                                icon = Icons.Default.Close,
                                onClick = onReject,
                                style = GlossStyle.Ghost,
                                size = 40.dp,
                                contentDescription = strings.rejectButton
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🌐 แถว "มาจากเว็บไหน" — ชื่อเว็บ + โดเมนของเซิร์ฟเวอร์ที่ลงทะเบียนเครื่องไว้
 *
 * อ่านจาก ServerConfig ในเครื่อง (ผูกด้วย serverId) เป็นหลัก เพราะคือเว็บที่ "ลงทะเบียนไว้จริง"
 * ชื่อใช้ displayName() ตัวเดียวกับชิปบนแถวรายการเงิน (SiteAttributionChip) → เห็นชื่อตรงกันทั้งแอพ
 * ชื่อที่มากับบิล (website_name / server_name) ใช้แค่ตอนยังโหลดรายการเซิร์ฟเวอร์ไม่เสร็จ
 */
@Composable
private fun ServerSourceRow(order: OrderApproval, server: ServerConfig?, modifier: Modifier = Modifier) {
    val name = server?.displayName()?.trim()?.takeIf { it.isNotEmpty() }
        ?: order.websiteName?.trim()?.takeIf { it.isNotEmpty() }
        ?: order.serverName?.trim()?.takeIf { it.isNotEmpty() }
    val host = ServerLabel.host(server?.baseUrl)
    val site = name ?: host ?: return
    val domain = host?.takeIf { name != null && !ServerLabel.sameSite(name, it) }
    SiteNameChip(site = site, detail = domain, modifier = modifier)
}

/**
 * 🏬 Badge เพจ/สาขาที่บิลนี้เกิด (ระบบสาขา fortune_pages)
 * เพจหลัก = เทากลมกลืน (กรณีปกติ ไม่ต้องสะดุดตา) / เพจสาขา = ม่วงเด่น (ของแปลกที่ควรเห็นทันที)
 * ชื่อเพจยาวได้ → maxLines 1 + ellipsis, ปล่อยให้ parent (Box weight) เป็นตัวคุมความกว้าง
 */
@Composable
private fun BranchChip(name: String, isDefault: Boolean, modifier: Modifier = Modifier) {
    val brand = if (isDefault) Color(0xFF64748B) else Color(0xFF7C3AED)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(brand.copy(alpha = 0.10f))
            .border(1.dp, brand.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(
            Icons.Default.Storefront,
            contentDescription = "เพจ/สาขา",
            modifier = Modifier.size(12.dp),
            tint = brand
        )
        Text(
            name,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = brand,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Badge ช่องทางที่บิลมาจาก — โลโก้ทางการ Facebook / LINE (bundle ใน drawable-nodpi)
 * พื้น tint สีแบรนด์จางๆ + ขอบ ให้เข้ากับชิป Aero อื่นบนการ์ด
 */
@Composable
private fun PlatformChip(platform: String) {
    val (logoRes, label, brand) = when (platform.lowercase()) {
        "facebook" -> Triple(R.drawable.platform_facebook, "Facebook", Color(0xFF1877F2))
        "line" -> Triple(R.drawable.platform_line, "LINE", Color(0xFF06C755))
        else -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(brand.copy(alpha = 0.10f))
            .border(1.dp, brand.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = label,
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
        )
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = brand,
            maxLines = 1
        )
    }
}

/** Status chip per design 02: amber รอจับคู่ / green จับคู่แล้ว / red ปฏิเสธ / glass others. */
@Composable
private fun StatusAeroChip(order: OrderApproval, strings: com.thaiprompt.smschecker.ui.theme.AppStrings) {
    when (order.approvalStatus) {
        ApprovalStatus.PENDING_REVIEW -> AeroChip(
            strings.aeroWaitingMatch,
            style = ChipStyle.Amber,
            leadingIcon = Icons.Default.AccessTime
        )
        ApprovalStatus.AUTO_APPROVED -> AeroChip(
            strings.aeroMatchedDone,
            style = ChipStyle.Green,
            leadingIcon = Icons.Default.Check
        )
        ApprovalStatus.MANUALLY_APPROVED -> AeroChip(
            strings.statusApproved,
            style = ChipStyle.Green,
            leadingIcon = Icons.Default.Check
        )
        ApprovalStatus.REJECTED -> AeroChip(
            strings.statusRejected,
            style = ChipStyle.Red,
            leadingIcon = Icons.Default.Close
        )
        ApprovalStatus.EXPIRED -> AeroChip(strings.statusExpired, style = ChipStyle.Glass)
        ApprovalStatus.CANCELLED -> AeroChip(
            order.cancellationReasonLabel?.takeIf { it.isNotBlank() } ?: strings.statusCancelled,
            style = ChipStyle.Glass
        )
        ApprovalStatus.DELETED -> AeroChip(strings.statusDeleted, style = ChipStyle.Glass)
    }
}

/**
 * The headline concept of design 02 — the unique-decimal amount:
 * pending = ฿500 in navy 31sp Black with ".37" in GREEN-LO (the matcher key);
 * approved = whole amount in green-deep 26sp; others = ink 26sp.
 */
@Composable
private fun UniqueDecimalAmount(amount: Double, status: ApprovalStatus) {
    val isPending = status == ApprovalStatus.PENDING_REVIEW
    val isApproved = status == ApprovalStatus.AUTO_APPROVED || status == ApprovalStatus.MANUALLY_APPROVED
    val sizeSp = if (isPending) 31.sp else 26.sp
    val wholeColor = when {
        isPending -> AeroPalette.NavyDeep
        isApproved -> AeroPalette.GreenDeep
        status == ApprovalStatus.REJECTED -> AeroPalette.Red
        else -> AeroPalette.InkFaint
    }
    // 🐞 (2026-06-11) คิดจากสตางค์ที่ round แล้ว — เดิมตัดเศษ float ทำให้ทศนิยม unique
    // (หัวใจของการจับคู่ยอด) แสดงผิด เช่น 500.29 → ฿500.28 ร้านเทียบกับสลิปแล้วงง
    val totalSatang = Math.round(amount * 100)
    val whole = String.format(Locale.US, "%,d", totalSatang / 100)
    val decimals = String.format(Locale.US, "%02d", totalSatang % 100)

    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = wholeColor)) { append("฿$whole") }
            withStyle(
                SpanStyle(color = if (isPending) AeroPalette.GreenLo else wholeColor)
            ) { append(".$decimals") }
        },
        fontSize = sizeSp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp,
        maxLines = 1
    )
}

// ============================================================================
// Utility functions
// ============================================================================

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
