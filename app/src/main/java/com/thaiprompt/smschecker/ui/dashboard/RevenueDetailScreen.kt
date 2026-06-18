package com.thaiprompt.smschecker.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
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
import com.thaiprompt.smschecker.ui.components.AeroDualLineChart
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

    // รายได้บิลที่อนุมัติต่อ bucket (สูตรเดียวกับ VM.billIncomeTotal) — เป็นแหล่งความจริงของ
    //  KPI รายได้/เฉลี่ย/สูงสุด เสมอ ไม่ว่าจะ fallback หรือไม่ (รายได้ต้องมาจากบิลเท่านั้น)
    val billIncomePoints: List<Float> = remember(state.billServerSeries, state.buckets) {
        val n = state.buckets.size
        val sums = FloatArray(n)
        state.billServerSeries.forEach { series ->
            series.data.forEachIndexed { i, v -> if (i < n) sums[i] += v.toFloat() }
        }
        sums.toList()
    }
    // hero + กราฟพระเอก: ใช้บิลถ้ามี, ไม่งั้น fallback ยอดเข้าธนาคารดิบ —
    //  หัวข้อ hero จะ relabel เป็น "ยอดเงินเข้าธนาคาร" ให้ตรงความจริง (ไม่เรียกว่ารายได้)
    val usingBillIncome = state.billIncomeTotal > 0.0
    val incomePoints: List<Float> = if (usingBillIncome) {
        billIncomePoints
    } else {
        state.bankCreditSeries?.data?.map { it.toFloat() } ?: emptyList()
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
                                    pointLabels = bucketPointLabels(state.buckets, state.isMonthly),
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
                                // รายได้/เฉลี่ย/สูงสุด = บิลที่อนุมัติเท่านั้น เสมอ (ไม่อิงยอดเข้าธนาคารดิบ
                                //  ซึ่งรวม refund / โอนเข้าตัวเอง) แม้โหมด fallback ที่ hero โชว์ยอดธนาคาร
                                KpiRow(
                                    label = strings.avgPerBucket,
                                    value = formatCompactBaht(state.billIncomeTotal / bucketCount)
                                )
                                KpiDivider()
                                KpiRow(
                                    label = strings.maxIncome,
                                    value = formatCompactBaht((billIncomePoints.maxOrNull() ?: 0f).toDouble())
                                )
                                KpiDivider()
                                KpiRow(
                                    label = strings.totalIncomeLabel,
                                    value = formatCompactBaht(state.billIncomeTotal)
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

                // ── bank SMS chart: เงินเข้า/เงินออก RAW (มีในเวอร์ชันก่อน redesign —
                //    owner สั่งนำกลับมาให้สอดคล้องดีไซน์ Millennium 3D) ──
                item(key = "bank_section") {
                    Spacer(modifier = Modifier.height(18.dp))
                    AeroSectionHeader(
                        title = strings.bankChartTitle,
                        leadingIcon = Icons.Default.AccountBalance,
                        actionText = periodChipLabel,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                item(key = "bank_chart") {
                    AeroGlass(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val creditPts = state.bankCreditSeries?.data?.map { it.toFloat() } ?: emptyList()
                            val debitPts = state.bankDebitSeries?.data?.map { it.toFloat() } ?: emptyList()
                            val hasData = (creditPts + debitPts).any { it > 0f } &&
                                maxOf(creditPts.size, debitPts.size) >= 2
                            // legend: เงินเข้า (เขียว) / เงินออก (แดง) + ยอดรวม
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AeroChip(
                                    "${state.bankCreditSeries?.name ?: strings.bankCreditTotal} ${formatCompactBaht(state.bankCreditTotal)}",
                                    style = ChipStyle.Green
                                )
                                AeroChip(
                                    "${state.bankDebitSeries?.name ?: strings.totalExpenseLabel} ${formatCompactBaht(state.bankDebitTotal)}",
                                    style = ChipStyle.Red
                                )
                            }
                            if (hasData) {
                                Spacer(modifier = Modifier.height(10.dp))
                                AeroDualLineChart(
                                    creditPoints = creditPts,
                                    debitPoints = debitPts,
                                    xLabels = bucketAxisLabels(state.buckets, state.isMonthly),
                                    pointLabels = bucketPointLabels(state.buckets, state.isMonthly),
                                    creditLabel = state.bankCreditSeries?.name ?: strings.bankCreditTotal,
                                    debitLabel = state.bankDebitSeries?.name ?: strings.totalExpenseLabel,
                                    chartHeight = 120.dp
                                )
                            } else {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    strings.noBankActivity,
                                    fontSize = 12.sp,
                                    color = AeroPalette.InkFaint,
                                    modifier = Modifier.padding(bottom = 10.dp)
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
    // แถบยาวขึ้นแบบ animate ทุกครั้งที่ค่าจริงเปลี่ยน (เปลี่ยนช่วงเวลา/refresh)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "serverBarFraction"
    )
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
                        .fillMaxWidth(animatedFraction)
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

/**
 * ป้ายเต็มหนึ่งอันต่อ bucket (align กับจุดในกราฟ) สำหรับ tooltip ตอนแตะจุด —
 * รายวัน → "อา 15 มิ.ย.", รายเดือน → "มิ.ย. 25". ไม่ thin เหมือน [bucketAxisLabels]
 * เพราะแต่ละจุดต้องมีป้ายของตัวเอง
 */
private fun bucketPointLabels(buckets: List<String>, isMonthly: Boolean): List<String> =
    buckets.map { key ->
        val parts = key.split("-")
        if (isMonthly) {
            if (parts.size == 2) "${thaiMonthAbbrev(parts[1])} ${parts[0].takeLast(2)}" else key
        } else {
            if (parts.size == 3) {
                "${thaiDayAbbrevOf(key)} ${parts[2].trimStart('0')} ${thaiMonthAbbrev(parts[1])}"
            } else key
        }
    }

private fun thaiMonthAbbrev(mm: String): String = when (mm.toIntOrNull()) {
    1 -> "ม.ค."; 2 -> "ก.พ."; 3 -> "มี.ค."; 4 -> "เม.ย."
    5 -> "พ.ค."; 6 -> "มิ.ย."; 7 -> "ก.ค."; 8 -> "ส.ค."
    9 -> "ก.ย."; 10 -> "ต.ค."; 11 -> "พ.ย."; 12 -> "ธ.ค."
    else -> mm
}
