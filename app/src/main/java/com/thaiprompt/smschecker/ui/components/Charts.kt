package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.data.model.DailyStats
import com.thaiprompt.smschecker.ui.theme.AppColors

@Composable
fun OrderBarChart(
    dailyStats: List<DailyStats>,
    modifier: Modifier = Modifier
) {
    if (dailyStats.isEmpty()) return

    val maxCount = dailyStats.maxOfOrNull { it.count } ?: 1
    val barMax = maxOf(maxCount, 1)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val barWidth = size.width / (dailyStats.size * 2f)
            val spacing = barWidth

            dailyStats.forEachIndexed { index, stat ->
                val x = index * (barWidth + spacing) + spacing / 2

                // Approved bar (green)
                val approvedHeight = if (barMax > 0) (stat.approved.toFloat() / barMax) * size.height else 0f
                drawRect(
                    color = AppColors.CreditGreen,
                    topLeft = Offset(x, size.height - approvedHeight),
                    size = Size(barWidth * 0.45f, approvedHeight)
                )

                // Rejected bar (red)
                val rejectedHeight = if (barMax > 0) (stat.rejected.toFloat() / barMax) * size.height else 0f
                drawRect(
                    color = AppColors.DebitRed,
                    topLeft = Offset(x + barWidth * 0.5f, size.height - rejectedHeight),
                    size = Size(barWidth * 0.45f, rejectedHeight)
                )
            }
        }

        // Day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dailyStats.forEach { stat ->
                Text(
                    text = stat.date.takeLast(5), // MM-DD
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ApprovalDonutChart(
    autoApproved: Int,
    manuallyApproved: Int,
    pending: Int,
    rejected: Int,
    modifier: Modifier = Modifier
) {
    val total = (autoApproved + manuallyApproved + pending + rejected).toFloat()

    Canvas(modifier = modifier) {
        val strokeWidth = 20f
        val radius = (size.minDimension - strokeWidth) / 2
        val topLeft = Offset(
            (size.width - radius * 2) / 2,
            (size.height - radius * 2) / 2
        )
        val arcSize = Size(radius * 2, radius * 2)

        if (total == 0f) {
            // Empty state — draw gray ring
            drawArc(
                color = Color.Gray.copy(alpha = 0.3f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            return@Canvas
        }

        val segments = listOf(
            autoApproved to AppColors.CreditGreen,
            manuallyApproved to AppColors.InfoBlue,
            pending to AppColors.WarningOrange,
            rejected to AppColors.DebitRed
        )

        var startAngle = -90f
        segments.forEach { (value, color) ->
            if (value > 0) {
                val sweep = (value / total) * 360f
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
    }
}

@Composable
fun ChartLegendItem(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun BankLogoCircle(
    bankCode: String,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val bankColor = when (bankCode.uppercase()) {
        "KBANK" -> Color(0xFF138F2D)
        "SCB" -> Color(0xFF4E2A84)
        "KTB" -> Color(0xFF00A4E4)
        "BBL" -> Color(0xFF1E3A8A)
        "GSB" -> Color(0xFFE91E9A)
        "BAY" -> Color(0xFFFFC107)
        "TTB" -> Color(0xFF0066FF)
        "PROMPTPAY" -> Color(0xFF003B71)
        else -> Color.Gray
    }

    val initials = when (bankCode.uppercase()) {
        "KBANK" -> "KB"
        "SCB" -> "SC"
        "KTB" -> "KT"
        "BBL" -> "BB"
        "GSB" -> "GS"
        "BAY" -> "AY"
        "TTB" -> "TB"
        "PROMPTPAY" -> "PP"
        else -> bankCode.take(2).uppercase()
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bankColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.35f).sp
        )
    }
}
