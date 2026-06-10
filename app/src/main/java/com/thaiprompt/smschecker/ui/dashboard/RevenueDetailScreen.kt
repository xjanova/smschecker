package com.thaiprompt.smschecker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.ui.components.AeroAreaChart
import com.thaiprompt.smschecker.ui.components.AeroChip
import com.thaiprompt.smschecker.ui.components.AeroGlass
import com.thaiprompt.smschecker.ui.components.AeroHeader
import com.thaiprompt.smschecker.ui.components.AeroSectionHeader
import com.thaiprompt.smschecker.ui.components.BankCoin
import com.thaiprompt.smschecker.ui.components.ChipStyle
import com.thaiprompt.smschecker.ui.components.ChromeSegmented
import com.thaiprompt.smschecker.ui.components.DateRangePickerDialog
import com.thaiprompt.smschecker.ui.components.GlossIconButton
import com.thaiprompt.smschecker.ui.components.GlossStyle
import com.thaiprompt.smschecker.ui.components.HeaderTone
import com.thaiprompt.smschecker.ui.components.MatchRateDonut
import com.thaiprompt.smschecker.ui.components.StatusBarTone
import com.thaiprompt.smschecker.ui.components.aeroHeaderBleed
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Stats / Revenue detail — Millennium 3D / Frutiger Aero (design 07).
 * Green header bleed + chrome segmented periods + income area chart with peak
 * bubble + donut/KPI row + per-server revenue bars.
 *
 * Data honesty notes (vs the mock): the donut shows the credit-vs-debit share
 * of bank money flow (match-rate data is not available in this ViewModel), and
 * the bottom comparison bars are per SERVER (per-bank totals don't exist here).
 */
@Composable
fun RevenueDetailScreen(
    viewModel: RevenueDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    var showDatePicker by remember { mutableStateOf(false) }

    StatusBarTone(HeaderTone.Green)

    // income per bucket = sum of approved-bill series; fallback to bank credit
    val usingBillIncome = state.billIncomeTotal > 0.0
    val incomePoints: List<Float> = remember(state.billServerSeries, state.bankCreditSeries, usingBillIncome) {
        if (usingBillIncome) {
            val n = state.buckets.size
            val sums = FloatArray(n)
            state.billServerSeries.forEach { series ->
                series.data.forEachIndexed { i, v -> if (i < n) sums[i] += v.toFloat() }
            }
            sums.toList()
        } else {
            state.bankCreditSeries?.data?.map { it.toFloat() } ?: emptyList()
        }
    }
    val incomeTotal = if (usingBillIncome) state.billIncomeTotal else state.bankCreditTotal

    val periodChipLabel = when (state.period) {
        RevenuePeriod.WEEK -> strings.periodWeek
        RevenuePeriod.MONTH -> strings.periodMonth
        RevenuePeriod.YEAR -> strings.periodYear
        RevenuePeriod.CUSTOM -> strings.periodCustom
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aeroHeaderBleed(HeaderTone.Green)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // ── header: back orb + title + period chip + segmented ──
            item(key = "header") {
                AeroHeader {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlossIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onBack,
                            style = GlossStyle.Ghost,
                            size = 38.dp,
                            contentDescription = strings.back
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.aeroStatsTitle,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                strings.aeroOverview7Days,
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 1
                            )
                        }
                        // period chip — opens the custom date-range picker
                        Box(modifier = Modifier.clickable { showDatePicker = true }) {
                            AeroChip(periodChipLabel, style = ChipStyle.GlassOnDark)
                        }
                    }
                    Spacer(modifier = Modifier.height(13.dp))
                    ChromeSegmented(
                        options = listOf(strings.periodWeek, strings.periodMonth, strings.periodYear),
                        selectedIndex = when (state.period) {
                            RevenuePeriod.WEEK -> 0
                            RevenuePeriod.MONTH -> 1
                            RevenuePeriod.YEAR -> 2
                            RevenuePeriod.CUSTOM -> -1
                        },
                        onSelect = { index ->
                            viewModel.setPeriod(
                                when (index) {
                                    0 -> RevenuePeriod.WEEK
                                    1 -> RevenuePeriod.MONTH
                                    else -> RevenuePeriod.YEAR
                                }
                            )
                        }
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AeroPalette.GreenLo)
                    }
                }
            } else {
                // ── hero income area chart ──
                item(key = "hero_chart") {
                    AeroGlass(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        cornerRadius = 26.dp,
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                when {
                                    !usingBillIncome -> strings.bankCreditTotal
                                    state.period == RevenuePeriod.WEEK -> strings.aeroIncomeThisWeek
                                    else -> strings.billIncomeTotal
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AeroPalette.InkSoft
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "฿${String.format(Locale.US, "%,.0f", incomeTotal)}",
                                fontSize = 33.sp,
                                fontWeight = FontWeight.Black,
                                color = AeroPalette.NavyDeep,
                                letterSpacing = (-0.5).sp
                            )
                            if (incomePoints.size >= 2 && incomePoints.any { it > 0f }) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AeroAreaChart(
                                    points = incomePoints,
                                    peakLabel = formatCompactBaht(incomePoints.max().toDouble()),
                                    xLabels = bucketAxisLabels(state.buckets, state.isMonthly),
                                    chartHeight = 120.dp
                                )
                            } else {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    strings.noBankActivity,
                                    fontSize = 12.sp,
                                    color = AeroPalette.InkFaint,
                                    modifier = Modifier.padding(bottom = 20.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "spacer_donut") { Spacer(modifier = Modifier.height(13.dp)) }

                // ── donut (credit vs debit share) + KPI list ──
                item(key = "donut_kpi") {
                    val flowTotal = state.bankCreditTotal + state.bankDebitTotal
                    val creditFraction = if (flowTotal > 0) (state.bankCreditTotal / flowTotal).toFloat() else 0f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        // donut card
                        AeroGlass(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight(),
                            cornerRadius = 18.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 15.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                MatchRateDonut(
                                    fraction = creditFraction,
                                    centerValue = "${(creditFraction * 100).toInt()}",
                                    centerLabel = strings.aeroIncomingLabel
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AeroChip(
                                        formatCompactBaht(state.bankCreditTotal),
                                        style = ChipStyle.Green
                                    )
                                    AeroChip(
                                        formatCompactBaht(state.bankDebitTotal),
                                        style = ChipStyle.Red
                                    )
                                }
                            }
                        }
                        // KPI card
                        AeroGlass(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 18.dp,
                            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                val bucketCount = state.buckets.size.coerceAtLeast(1)
                                KpiRow(
                                    label = strings.avgPerBucket,
                                    value = formatCompactBaht(incomeTotal / bucketCount)
                                )
                                KpiDivider()
                                KpiRow(
                                    label = strings.maxIncome,
                                    value = formatCompactBaht((incomePoints.maxOrNull() ?: 0f).toDouble())
                                )
                                KpiDivider()
                                KpiRow(
                                    label = strings.totalIncomeLabel,
                                    value = formatCompactBaht(state.bankCreditTotal)
                                )
                                KpiDivider()
                                KpiRow(
                                    label = strings.totalExpenseLabel,
                                    value = formatCompactBaht(state.bankDebitTotal)
                                )
                            }
                        }
                    }
                }

                // ── per-server revenue comparison bars ──
                if (state.billServerSeries.isNotEmpty()) {
                    item(key = "server_section") {
                        Spacer(modifier = Modifier.height(18.dp))
                        AeroSectionHeader(
                            title = strings.billChartSubtitle,
                            leadingIcon = Icons.Default.GridView,
                            actionText = periodChipLabel,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(9.dp))
                    }
                    item(key = "server_bars") {
                        val maxTotal = state.billServerSeries.maxOf { it.total }.coerceAtLeast(1.0)
                        AeroGlass(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            cornerRadius = 18.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Column {
                                state.billServerSeries.forEachIndexed { index, series ->
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color(0x4D9FB8C8))
                                        )
                                    }
                                    ServerRevenueRow(
                                        name = series.name,
                                        fraction = (series.total / maxTotal).toFloat(),
                                        valueText = "฿${String.format(Locale.US, "%,.0f", series.total)}"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // custom range picker (opened from the header period chip)
    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { start, end ->
                viewModel.setCustomRange(start, end)
                showDatePicker = false
            }
        )
    }
}

/** label-left / bold-value-right KPI row (.kpi). */
@Composable
private fun KpiRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = AeroPalette.InkSoft, maxLines = 1)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AeroPalette.NavyDeep,
            maxLines = 1
        )
    }
}

@Composable
private fun KpiDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x4D9FB8C8))
    )
}

/** Server comparison row (.srow): coin-initials + name + ฿total + progress track. */
@Composable
private fun ServerRevenueRow(name: String, fraction: Float, valueText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        BankCoin(bankCode = name.take(4), size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    valueText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AeroPalette.NavyDeep,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x809FB8C8))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AeroPalette.GreenLo, AeroPalette.GreenHi)
                            )
                        )
                )
            }
        }
    }
}

// ════════════════════════════════════════
// formatting helpers
// ════════════════════════════════════════

/** ฿61.2k-style compact format for peak bubbles / chips. */
private fun formatCompactBaht(value: Double): String = when {
    value >= 1_000_000 -> "฿${String.format(Locale.US, "%.1f", value / 1_000_000)}M"
    value >= 1_000 -> "฿${String.format(Locale.US, "%.1f", value / 1_000)}k"
    else -> "฿${String.format(Locale.US, "%.0f", value)}"
}

/**
 * Axis labels from VM buckets: 7-day → Thai weekday abbrevs (จ อ พ ...),
 * longer daily ranges → d/M thinned to ≤7, monthly → M/yy thinned to ≤7.
 */
private fun bucketAxisLabels(buckets: List<String>, isMonthly: Boolean): List<String> {
    if (buckets.isEmpty()) return emptyList()
    if (!isMonthly && buckets.size <= 7) {
        return buckets.map { thaiDayAbbrevOf(it) }
    }
    val step = ((buckets.size + 6) / 7).coerceAtLeast(1)
    val thinned = buckets.filterIndexed { i, _ -> i % step == 0 }
    return thinned.map { key ->
        if (isMonthly) {
            // yyyy-MM → M/yy
            val parts = key.split("-")
            if (parts.size == 2) "${parts[1].trimStart('0')}/${parts[0].takeLast(2)}" else key
        } else {
            // yyyy-MM-dd → d/M
            val parts = key.split("-")
            if (parts.size == 3) "${parts[2].trimStart('0')}/${parts[1].trimStart('0')}" else key
        }
    }
}

private fun thaiDayAbbrevOf(dateKey: String): String = try {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
    when (Calendar.getInstance().apply { time = date!! }.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "จ"
        Calendar.TUESDAY -> "อ"
        Calendar.WEDNESDAY -> "พ"
        Calendar.THURSDAY -> "พฤ"
        Calendar.FRIDAY -> "ศ"
        Calendar.SATURDAY -> "ส"
        else -> "อา"
    }
} catch (e: Exception) {
    ""
}
