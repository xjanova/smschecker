@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.LineSeries
import com.thaiprompt.smschecker.ui.components.MultiLineChart
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Palette สำหรับเส้นกราฟแยกตาม server
 * เริ่มที่เฉดเขียว 4 เฉด แล้ว fallback เป็นสีอื่นเมื่อ server > 4
 */
private val ServerColorPalette = listOf(
    Color(0xFF1B5E20),   // dark green
    Color(0xFF2E7D32),   // forest green
    Color(0xFF43A047),   // medium green
    Color(0xFF66BB6A),   // light green
    Color(0xFF1976D2),   // blue
    Color(0xFFFB8C00),   // orange
    Color(0xFF8E24AA),   // purple
    Color(0xFF00ACC1),   // teal
    Color(0xFFD81B60),   // pink
)

private fun colorForServerIndex(idx: Int): Color =
    ServerColorPalette[idx % ServerColorPalette.size]

@Composable
fun RevenueDetailScreen(
    onBack: () -> Unit,
    viewModel: RevenueDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    var showDatePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "header") {
            GradientHeader {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            strings.revenueDetailTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            strings.revenueDetailSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF66BB6A)
                        )
                    }
                }
            }
        }

        item(key = "spacer1") { Spacer(modifier = Modifier.height(16.dp)) }

        // Period selector (4 ปุ่ม)
        item(key = "period_selector") {
            PeriodSelector(
                selected = state.period,
                onSelected = { p ->
                    if (p == RevenuePeriod.CUSTOM) {
                        showDatePicker = true
                    } else {
                        viewModel.setPeriod(p)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Custom range bar — แสดงเมื่อ period = CUSTOM
        if (state.period == RevenuePeriod.CUSTOM) {
            item(key = "custom_range_bar") {
                Spacer(modifier = Modifier.height(8.dp))
                CustomRangeBar(
                    startMs = state.customStartMs,
                    endMs = state.customEndMs,
                    onPick = { showDatePicker = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item(key = "spacer2") { Spacer(modifier = Modifier.height(16.dp)) }

        // === กราฟบิล (รายได้แยกตาม server) ===
        item(key = "bill_chart") {
            BillChartCard(
                state = state,
                bahtSymbol = strings.bahtSymbol,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "bill_summary") {
            Spacer(modifier = Modifier.height(12.dp))
            BillSummary(
                state = state,
                bahtSymbol = strings.bahtSymbol,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "spacer_section") { Spacer(modifier = Modifier.height(20.dp)) }

        // === กราฟ SMS ธนาคาร ===
        item(key = "bank_chart") {
            BankChartCard(
                state = state,
                bahtSymbol = strings.bahtSymbol,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "bank_summary") {
            Spacer(modifier = Modifier.height(12.dp))
            BankSummary(
                state = state,
                bahtSymbol = strings.bahtSymbol,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "bottom_pad") { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showDatePicker) {
        RangePickerDialog(
            initialStart = state.customStartMs,
            initialEnd = state.customEndMs,
            onConfirm = { startMs, endMs ->
                showDatePicker = false
                viewModel.setCustomRange(startMs, endMs)
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Period selector — 4 ปุ่ม
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PeriodSelector(
    selected: RevenuePeriod,
    onSelected: (RevenuePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            RevenuePeriod.WEEK to strings.periodWeek,
            RevenuePeriod.MONTH to strings.periodMonth,
            RevenuePeriod.YEAR to strings.periodYear,
            RevenuePeriod.CUSTOM to strings.periodCustom
        ).forEach { (period, label) ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) AppColors.GoldAccent.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
                    .clickable { onSelected(period) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) AppColors.GoldAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Custom range bar
// ═══════════════════════════════════════════════════════════════
@Composable
private fun CustomRangeBar(
    startMs: Long?,
    endMs: Long?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val sdf = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }
    val labelText = if (startMs != null && endMs != null) {
        "${sdf.format(java.util.Date(startMs))}  →  ${sdf.format(java.util.Date(endMs))}"
    } else {
        strings.applyRange
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                AppColors.GoldAccent.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onPick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = AppColors.GoldAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = labelText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// DateRangePicker dialog (Material3)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun RangePickerDialog(
    initialStart: Long?,
    initialEnd: Long?,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart,
        initialSelectedEndDateMillis = initialEnd
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: return@TextButton
                    onConfirm(s, e)
                }
            ) { Text(strings.applyRange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelButton) }
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Bill chart card
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BillChartCard(
    state: RevenueDetailState,
    bahtSymbol: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    GlassCard(modifier = modifier) {
        Text(
            text = strings.billChartTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.CreditGreen
        )
        Text(
            text = strings.billChartSubtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            state.isLoading -> ChartLoadingBox()
            state.billServerSeries.isEmpty() && state.billRefundSeries == null -> {
                EmptyChartBox(strings.noBillsInRange)
            }
            else -> {
                Text(
                    strings.tapChartHint,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(
                    targetState = state.period to state.buckets.size,
                    transitionSpec = {
                        (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(220)))
                    },
                    label = "billChartTransition"
                ) { _ ->
                    key(state.period, state.buckets.size, state.customStartMs, state.customEndMs) {
                        val series = buildList {
                            state.billServerSeries.forEachIndexed { idx, s ->
                                add(LineSeries(
                                    key = s.key,
                                    name = s.name,
                                    color = colorForServerIndex(idx),
                                    data = s.data
                                ))
                            }
                            state.billRefundSeries?.let { r ->
                                add(LineSeries(
                                    key = r.key,
                                    name = strings.billRefund,
                                    color = AppColors.DebitRed,
                                    data = r.data
                                ))
                            }
                        }
                        MultiLineChart(
                            buckets = state.buckets,
                            series = series,
                            modifier = Modifier.fillMaxWidth(),
                            height = 240.dp,
                            bahtSymbol = bahtSymbol
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Bank chart card
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BankChartCard(
    state: RevenueDetailState,
    bahtSymbol: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    GlassCard(modifier = modifier) {
        Text(
            text = strings.bankChartTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = strings.bankChartSubtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            state.isLoading -> ChartLoadingBox()
            state.bankCreditSeries == null && state.bankDebitSeries == null -> {
                EmptyChartBox(strings.noBankActivity)
            }
            (state.bankCreditTotal == 0.0 && state.bankDebitTotal == 0.0) -> {
                EmptyChartBox(strings.noBankActivity)
            }
            else -> {
                AnimatedContent(
                    targetState = state.period to state.buckets.size,
                    transitionSpec = {
                        (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(220)))
                    },
                    label = "bankChartTransition"
                ) { _ ->
                    key(state.period, state.buckets.size, state.customStartMs, state.customEndMs) {
                        val series = buildList {
                            state.bankCreditSeries?.let {
                                add(LineSeries(
                                    key = it.key,
                                    name = it.name,
                                    color = AppColors.CreditGreen,
                                    data = it.data
                                ))
                            }
                            state.bankDebitSeries?.let {
                                add(LineSeries(
                                    key = it.key,
                                    name = it.name,
                                    color = AppColors.DebitRed,
                                    data = it.data
                                ))
                            }
                        }
                        MultiLineChart(
                            buckets = state.buckets,
                            series = series,
                            modifier = Modifier.fillMaxWidth(),
                            height = 220.dp,
                            bahtSymbol = bahtSymbol,
                            showFill = true   // กราฟ 2 เส้นใส่ fill ได้
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLoadingBox() {
    Box(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AppColors.GoldAccent)
    }
}

@Composable
private fun EmptyChartBox(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 13.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Bill summary
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BillSummary(
    state: RevenueDetailState,
    bahtSymbol: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // รวม
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = strings.billIncomeTotal,
                value = "+$bahtSymbol${"%,.2f".format(state.billIncomeTotal)}",
                subtitle = null,
                color = AppColors.CreditGreen
            )
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                label = strings.billRefund,
                value = "-$bahtSymbol${"%,.2f".format(state.billRefundTotal)}",
                subtitle = null,
                color = AppColors.DebitRed
            )
        }

        // Per-server breakdown
        if (state.billServerSeries.isNotEmpty()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.billServerSeries.forEachIndexed { idx, s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colorForServerIndex(idx))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.name,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                text = "$bahtSymbol${"%,.2f".format(s.total)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.CreditGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Bank summary
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BankSummary(
    state: RevenueDetailState,
    bahtSymbol: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val net = state.bankCreditTotal - state.bankDebitTotal
    val netColor = if (net >= 0) AppColors.CreditGreen else AppColors.DebitRed

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Net
        GlassCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    strings.netBalance,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${if (net >= 0) "+" else ""}$bahtSymbol${"%,.2f".format(kotlin.math.abs(net))}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = netColor
                )
            }
        }

        // Credit / Debit
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = strings.bankCreditTotal,
                value = "+$bahtSymbol${"%,.2f".format(state.bankCreditTotal)}",
                subtitle = null,
                color = AppColors.CreditGreen
            )
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                label = strings.bankDebitTotal,
                value = "-$bahtSymbol${"%,.2f".format(state.bankDebitTotal)}",
                subtitle = null,
                color = AppColors.DebitRed
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SummaryStatCard — เหมือนเดิม
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SummaryStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String?,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = 0.10f),
                        color.copy(alpha = 0.03f)
                    )
                )
            )
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
