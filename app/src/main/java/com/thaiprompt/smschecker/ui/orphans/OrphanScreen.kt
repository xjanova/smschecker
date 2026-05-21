@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.orphans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.OrphanStatus
import com.thaiprompt.smschecker.data.model.OrphanTransaction
import com.thaiprompt.smschecker.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OrphanScreen(viewModel: OrphanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<OrphanTransaction?>(null) }

    // 🔍 (2026-05-21) Bill candidates dialog
    BillCandidatesDialog(
        state = state.candidatesDialog,
        onDismiss = { viewModel.closeCandidatesDialog() },
        onConfirm = { orphan, candidate, serverId ->
            viewModel.confirmBillMatch(orphan, candidate, serverId)
        }
    )

    // Match result snackbar (after confirm success)
    state.matchResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMatchResult() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.SuccessGreen) },
            title = { Text("สำเร็จ") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMatchResult() }) { Text("ตกลง") }
            }
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { orphan ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("ยกเลิกยอดรอจับคู่?") },
            text = {
                Text(
                    "ยอด ฿${String.format("%.2f", orphan.amount)} จาก ${orphan.bank}\n\n" +
                    "ยอดนี้จะถูกลบออกจากรายการรอจับคู่"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissOrphan(orphan)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("ยกเลิก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("ไม่ใช่")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    "ยอดรอจับคู่",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "ยอดเงินที่ตรวจพบแต่ยังไม่มีบิลรองรับ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Filter toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.pendingCount} รายการรอจับคู่",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.WarningOrange
                    )

                    FilterChip(
                        selected = state.showPendingOnly,
                        onClick = { viewModel.toggleFilter() },
                        label = {
                            Text(if (state.showPendingOnly) "เฉพาะรอจับคู่" else "ทั้งหมด")
                        },
                        leadingIcon = {
                            Icon(
                                if (state.showPendingOnly) Icons.Default.FilterList else Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // Loading state
        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Empty state
        if (!state.isLoading && state.orphans.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = AppColors.SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "ไม่มียอดรอจับคู่",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "ยอดทุกรายการจับคู่สำเร็จแล้ว",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Orphan list
        items(state.orphans, key = { it.id }) { orphan ->
            OrphanCard(
                orphan = orphan,
                onDismiss = { showDeleteDialog = orphan },
                onFindBill = { viewModel.findBillCandidates(orphan) }
            )
        }
    }
}

@Composable
fun OrphanCard(
    orphan: OrphanTransaction,
    onDismiss: () -> Unit,
    onFindBill: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val statusColor = when (orphan.status) {
        OrphanStatus.PENDING -> AppColors.WarningOrange
        OrphanStatus.MATCHED -> AppColors.SuccessGreen
        OrphanStatus.EXPIRED -> Color.Gray
        OrphanStatus.IGNORED -> Color.Gray
        OrphanStatus.MANUALLY_RESOLVED -> AppColors.InfoBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (orphan.status == OrphanStatus.PENDING) statusColor.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Amount
                Text(
                    "฿${String.format("%,.2f", orphan.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.SuccessGreen
                )

                // Bank
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        orphan.bank,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        dateFormat.format(Date(orphan.transactionTimestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Status badge
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        orphan.status.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            // Action buttons (only for pending)
            if (orphan.status == OrphanStatus.PENDING) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 🔍 (2026-05-21) Find matching bill (fuzzy name+time+amount>=base)
                    IconButton(
                        onClick = onFindBill,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = AppColors.InfoBlue
                        )
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "หาบิลตรงกัน",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = Color.Red.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "ยกเลิก",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// ================================================================
// 🔍 (2026-05-21) Bill Candidates Dialog
// แสดง list บิลที่อาจตรงกับ orphan SMS (name+time fuzzy + amount >= base)
// ================================================================
@Composable
fun BillCandidatesDialog(
    state: CandidatesDialogState,
    onDismiss: () -> Unit,
    onConfirm: (OrphanTransaction, com.thaiprompt.smschecker.data.api.BillCandidate, Long) -> Unit
) {
    if (state is CandidatesDialogState.Hidden) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.InfoBlue) },
        title = {
            val orphan = when (state) {
                is CandidatesDialogState.Loading -> state.orphan
                is CandidatesDialogState.Loaded -> state.orphan
                is CandidatesDialogState.Error -> state.orphan
                is CandidatesDialogState.Confirming -> state.orphan
                CandidatesDialogState.Hidden -> null
            }
            Column {
                Text("🔍 หาบิลที่ตรงกัน", fontWeight = FontWeight.Bold)
                orphan?.let {
                    Text(
                        "SMS ฿${String.format("%,.2f", it.amount)} จาก \"${it.senderOrReceiver}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            when (state) {
                is CandidatesDialogState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("กำลังค้นหาบิล...")
                    }
                }
                is CandidatesDialogState.Confirming -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("กำลังผูก SMS เข้าบิล ${state.billRef}...")
                    }
                }
                is CandidatesDialogState.Error -> {
                    Text(
                        "❌ ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is CandidatesDialogState.Loaded -> {
                    if (state.candidates.isEmpty()) {
                        Text(
                            "ไม่พบบิลที่ตรงกัน — ลูกค้าอาจยังไม่ได้สร้างบิล หรือ ยอดน้อยกว่าราคาบิล",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "พบ ${state.candidates.size} บิล (เรียงตามความตรงของชื่อ)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.candidates.take(5).forEach { (cand, serverId) ->
                                BillCandidateItem(
                                    candidate = cand,
                                    onClick = { onConfirm(state.orphan, cand, serverId) }
                                )
                            }
                        }
                    }
                }
                CandidatesDialogState.Hidden -> {}
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ปิด") }
        }
    )
}

@Composable
private fun BillCandidateItem(
    candidate: com.thaiprompt.smschecker.data.api.BillCandidate,
    onClick: () -> Unit
) {
    val scoreColor = when {
        candidate.name_score >= 70 -> AppColors.SuccessGreen
        candidate.name_score >= 40 -> AppColors.WarningOrange
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    candidate.bill_reference,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = scoreColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "ชื่อตรง ${candidate.name_score}%",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = scoreColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "👤 ${candidate.customer_name ?: "-"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "💰 ราคา ฿${String.format("%,.2f", candidate.base_price)} (เกิน ฿${String.format("%,.2f", candidate.amount_delta)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "⏰ บิลสร้างก่อน SMS ${candidate.time_delta_minutes} นาที",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
