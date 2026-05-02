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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.thaiprompt.smschecker.ui.components.InteractiveIncomeExpenseLineChart
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings

@Composable
fun RevenueDetailScreen(
    onBack: () -> Unit,
    viewModel: RevenueDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current

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

        // Period selector
        item(key = "period_selector") {
            PeriodSelector(
                selected = state.period,
                onSelected = { viewModel.setPeriod(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "spacer2") { Spacer(modifier = Modifier.height(16.dp)) }

        // Chart card
        item(key = "chart_card") {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.GoldAccent)
                    }
                } else if (state.data.isEmpty() ||
                    (state.totalIncome == 0.0 && state.totalExpense == 0.0)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            strings.noTransactions,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Text(
                        strings.tapChartHint,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = state.period,
                        transitionSpec = {
                            (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(220)))
                        },
                        label = "chartTransition"
                    ) { targetPeriod ->
                        // re-key on period เพื่อให้ chart instance ใหม่และ trigger animation 0→1
                        key(targetPeriod) {
                            InteractiveIncomeExpenseLineChart(
                                data = state.data,
                                modifier = Modifier.fillMaxWidth(),
                                height = 260.dp,
                                bahtSymbol = strings.bahtSymbol,
                                incomeLabel = strings.todayIncome,
                                expenseLabel = strings.todayExpense
                            )
                        }
                    }
                }
            }
        }

        item(key = "spacer3") { Spacer(modifier = Modifier.height(16.dp)) }

        // Summary stats grid
        item(key = "summary") {
            SummaryGrid(
                state = state,
                bahtSymbol = strings.bahtSymbol,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "bottom_pad") { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

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
            RevenuePeriod.YEAR to strings.periodYear
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
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) AppColors.GoldAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryGrid(
    state: RevenueDetailState,
    bahtSymbol: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val net = state.totalIncome - state.totalExpense
    val netColor = if (net >= 0) AppColors.CreditGreen else AppColors.DebitRed

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Net balance — big card on top
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    strings.netBalance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${if (net >= 0) "+" else ""}$bahtSymbol${"%,.2f".format(kotlin.math.abs(net))}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = netColor
                )
            }
        }

        // Two-column: Income / Expense totals
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = strings.totalIncomeLabel,
                value = "+$bahtSymbol${"%,.2f".format(state.totalIncome)}",
                subtitle = "${strings.avgPerBucket} $bahtSymbol${"%,.0f".format(state.avgIncomePerBucket)}",
                color = AppColors.CreditGreen
            )
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                label = strings.totalExpenseLabel,
                value = "-$bahtSymbol${"%,.2f".format(state.totalExpense)}",
                subtitle = "${strings.avgPerBucket} $bahtSymbol${"%,.0f".format(state.avgExpensePerBucket)}",
                color = AppColors.DebitRed
            )
        }

        // Max amount row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = strings.maxIncome,
                value = "$bahtSymbol${"%,.0f".format(state.maxIncome)}",
                subtitle = null,
                color = AppColors.CreditGreen
            )
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                label = strings.maxExpense,
                value = "$bahtSymbol${"%,.0f".format(state.maxExpense)}",
                subtitle = null,
                color = AppColors.DebitRed
            )
        }
    }
}

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
