package com.thaiprompt.smschecker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.data.db.DailyIncomeExpense
import com.thaiprompt.smschecker.data.model.DailyStats
import com.thaiprompt.smschecker.ui.theme.AppColors
import kotlin.math.max

// ═══════════════════════════════════════════════════════════════════════════
// IncomeExpenseLineChart — กราฟเส้นรายได้ (เขียว) vs รายจ่าย (แดง) ในกราฟเดียว
// ใช้ smooth bezier curve + gradient fill ด้านล่าง + dot ที่จุดข้อมูล
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun IncomeExpenseLineChart(
    data: List<DailyIncomeExpense>,
    modifier: Modifier = Modifier,
    incomeColor: Color = AppColors.CreditGreen,
    expenseColor: Color = AppColors.DebitRed,
    gridColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (data.isEmpty()) return

    // ค่ามากสุดเพื่อ scale แกน Y — เผื่อขั้นต่ำเล็กน้อยกันเส้นเรียบ
    val maxValue = remember(data) {
        max(
            data.maxOf { it.credit },
            data.maxOf { it.debit }
        ).let { if (it <= 0.0) 1.0 else it * 1.1 }
    }

    // อนิเมชั่น expand จาก 0 → 1 ตอนเข้าหน้าครั้งแรก (และตอน data เปลี่ยน)
    // ใช้ Animatable + LaunchedEffect เพราะ animateFloatAsState ไม่ trigger animation
    // เมื่อ initial == target (จะเริ่มที่ 1f ทันทีโดยไม่ animate)
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatable.snapTo(0f)
        animatable.animateTo(1f, tween(durationMillis = 900))
    }
    val animProgress = animatable.value

    Column(modifier = modifier.fillMaxWidth()) {
        // Y-axis labels (max value) บนซ้าย
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatCompact(maxValue),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                color = labelColor.copy(alpha = 0.6f)
            )
            Text(
                text = "฿",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                color = labelColor.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            val w = size.width
            val h = size.height

            // === Grid lines แนวนอน 4 เส้น (รวมขอบบน/ล่าง) ===
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = h * (i.toFloat() / gridLines)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = if (i in 1 until gridLines) {
                        PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                    } else null
                )
            }

            if (data.size < 2) {
                // Single data point — แค่วาดจุด
                val cx = w / 2f
                val cyIncome = h - (data[0].credit / maxValue * h * animProgress).toFloat()
                val cyExpense = h - (data[0].debit / maxValue * h * animProgress).toFloat()
                drawCircle(incomeColor, radius = 6f, center = Offset(cx, cyIncome))
                drawCircle(expenseColor, radius = 6f, center = Offset(cx, cyExpense))
                return@Canvas
            }

            val stepX = w / (data.size - 1).toFloat()

            // คำนวณจุดสำหรับเส้นรายได้ + รายจ่าย
            val incomePoints = data.mapIndexed { i, d ->
                Offset(
                    x = i * stepX,
                    y = h - (d.credit / maxValue * h * animProgress).toFloat()
                )
            }
            val expensePoints = data.mapIndexed { i, d ->
                Offset(
                    x = i * stepX,
                    y = h - (d.debit / maxValue * h * animProgress).toFloat()
                )
            }

            // === วาด gradient fill ใต้เส้น (รายจ่ายก่อน เพราะปกติน้อยกว่า) ===
            drawSmoothFill(expensePoints, expenseColor, h)
            drawSmoothFill(incomePoints, incomeColor, h)

            // === วาดเส้น smooth ===
            drawSmoothLine(expensePoints, expenseColor, strokeWidth = 3.5f)
            drawSmoothLine(incomePoints, incomeColor, strokeWidth = 3.5f)

            // === วาดจุดข้อมูล (dots) — outer ring + inner solid ===
            for (p in expensePoints) {
                drawCircle(Color.White, radius = 5f, center = p)
                drawCircle(expenseColor, radius = 4f, center = p)
            }
            for (p in incomePoints) {
                drawCircle(Color.White, radius = 5f, center = p)
                drawCircle(incomeColor, radius = 4f, center = p)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // === X-axis labels (วันที่) ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            data.forEachIndexed { _, d ->
                Text(
                    text = formatDayLabel(d.date),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Smooth bezier path ผ่าน points ทั้งหมด — ใช้ control point แบบ midpoint
 */
private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val midX = (prev.x + curr.x) / 2f
        // control points: ครึ่งทาง ใช้ y ของจุดต้น/ปลายเพื่อให้โค้งนุ่ม
        path.cubicTo(
            midX, prev.y,
            midX, curr.y,
            curr.x, curr.y
        )
    }
    return path
}

private fun DrawScope.drawSmoothLine(points: List<Offset>, color: Color, strokeWidth: Float) {
    if (points.size < 2) return
    drawPath(
        path = buildSmoothPath(points),
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawSmoothFill(points: List<Offset>, color: Color, baselineY: Float) {
    if (points.size < 2) return
    val fillPath = buildSmoothPath(points).apply {
        // ปิด path ลงไปแกนล่างเพื่อ fill
        lineTo(points.last().x, baselineY)
        lineTo(points.first().x, baselineY)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.28f),
                color.copy(alpha = 0.04f)
            ),
            startY = 0f,
            endY = baselineY
        )
    )
}

/**
 * แปลงตัวเลขให้สั้น: 1234 → 1.2K, 1234567 → 1.2M
 */
private fun formatCompact(value: Double): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_000_000 -> "${"%.1f".format(value / 1_000_000)}M"
        abs >= 1_000 -> "${"%.1f".format(value / 1_000)}K"
        else -> "%.0f".format(value)
    }
}

/**
 * แปลง YYYY-MM-DD → "DD/MM" สำหรับ x-axis
 */
private fun formatDayLabel(date: String): String {
    // YYYY-MM-DD → DD/MM
    val parts = date.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}" else date
}

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
