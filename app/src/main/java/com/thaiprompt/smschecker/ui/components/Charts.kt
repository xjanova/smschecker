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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
    size: androidx.compose.ui.unit.Dp = 44.dp,
    grayscale: Boolean = false
) {
    val colorFilter = if (grayscale) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    } else {
        null
    }
    val drawableRes = when (bankCode.uppercase()) {
        "KBANK" -> com.thaiprompt.smschecker.R.drawable.bank_kbank
        "SCB" -> com.thaiprompt.smschecker.R.drawable.bank_scb
        "KTB" -> com.thaiprompt.smschecker.R.drawable.bank_ktb
        "BBL" -> com.thaiprompt.smschecker.R.drawable.bank_bbl
        "GSB" -> com.thaiprompt.smschecker.R.drawable.bank_gsb
        "BAY" -> com.thaiprompt.smschecker.R.drawable.bank_bay
        "TTB" -> com.thaiprompt.smschecker.R.drawable.bank_ttb
        "PROMPTPAY" -> com.thaiprompt.smschecker.R.drawable.bank_promptpay
        "CIMB" -> com.thaiprompt.smschecker.R.drawable.bank_cimb
        "KKP" -> com.thaiprompt.smschecker.R.drawable.bank_kkp
        "LH" -> com.thaiprompt.smschecker.R.drawable.bank_lh
        "TISCO" -> com.thaiprompt.smschecker.R.drawable.bank_tisco
        "UOB" -> com.thaiprompt.smschecker.R.drawable.bank_uob
        "ICBC" -> com.thaiprompt.smschecker.R.drawable.bank_icbc
        "BAAC" -> com.thaiprompt.smschecker.R.drawable.bank_baac
        else -> null
    }

    val initials = when (bankCode.uppercase()) {
        "KBANK" -> "K"
        "SCB" -> "SCB"
        "KTB" -> "KTB"
        "BBL" -> "BBL"
        "GSB" -> "GSB"
        "BAY" -> "BAY"
        "TTB" -> "ttb"
        "PROMPTPAY" -> "PP"
        "CIMB" -> "CIMB"
        "KKP" -> "KKP"
        "LH" -> "LH"
        "TISCO" -> "TISCO"
        "UOB" -> "UOB"
        "ICBC" -> "ICBC"
        "BAAC" -> "ธกส"
        else -> bankCode.take(3).uppercase()
    }

    val textColor = when (bankCode.uppercase()) {
        "BAY" -> Color(0xFF333333) // Dark text on yellow
        else -> Color.White
    }

    val fontSize = when {
        initials.length <= 1 -> (size.value * 0.5f).sp
        initials.length <= 2 -> (size.value * 0.38f).sp
        initials.length <= 3 -> (size.value * 0.3f).sp
        else -> (size.value * 0.22f).sp
    }

    if (drawableRes != null) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = drawableRes),
                contentDescription = bankCode,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                colorFilter = colorFilter
            )
            Text(
                text = initials,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        }
    } else {
        // Fallback for unknown banks
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        }
    }
}
