package com.thaiprompt.smschecker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import java.util.Locale
import kotlin.math.roundToInt

/* =========================================================================
   Millennium 3D / Frutiger Aero — chart primitives.
   Self-contained Canvas drawings: smooth income sparkline, full area chart
   with dashed grid + node dots + peak-marker bubble, a dual-line bank
   credit/debit chart, and a single-value gloss donut. Feed plain values;
   the screens map their ViewModel data in. All charts animate in (reveal /
   sweep) and re-animate when their data changes.
   ========================================================================= */

/**
 * Animation progress 0→1 ทุกครั้งที่ [key] (เช่น data ของกราฟ) เปลี่ยน —
 * ใช้ทำ reveal ซ้าย→ขวา ของเส้นกราฟ และ sweep ของโดนัท
 */
@Composable
private fun chartRevealProgress(key: Any?): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    return anim.value
}

/** Catmull-Rom → cubic Bézier so the line reads as a smooth aero curve. */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts[if (i - 1 >= 0) i - 1 else i]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 < pts.size) i + 2 else i + 1]
        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    return path
}

/**
 * Compact income sparkline (Dashboard hero). Area gradient + smooth line, no
 * axis. Falls back to a flat line if fewer than 2 points.
 */
@Composable
fun AeroSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth().height(48.dp),
    lineColor: Color = AeroPalette.GreenLo,
    fillColor: Color = AeroPalette.Green,
) {
    val progress = chartRevealProgress(points)
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val padY = size.height * 0.12f
        val usableH = size.height - padY * 2
        val n = points.size
        val pts = points.mapIndexed { i, v ->
            val x = size.width * i / (n - 1)
            val y = padY + (1f - (v - min) / span) * usableH
            Offset(x, y)
        }
        val line = smoothPath(pts)
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        // reveal ซ้าย→ขวา ตาม progress
        clipRect(right = size.width * progress) {
            drawPath(
                area,
                Brush.verticalGradient(listOf(fillColor.copy(alpha = 0.35f), fillColor.copy(alpha = 0f)))
            )
            drawPath(line, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/**
 * Full income area chart (Stats screen). Dashed gridlines, gradient area + line,
 * white-ringed node dots, and a navy peak-marker bubble over the max point.
 * Pass [xLabels] to render an axis row beneath the chart.
 */
@Composable
fun AeroAreaChart(
    points: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    peakLabel: String? = null,
    xLabels: List<String> = emptyList(),
    chartHeight: Dp = 120.dp,
    pointLabels: List<String> = emptyList(),                       // ป้ายเต็มต่อจุด (หัวข้อ tooltip) — align กับ points
    valueFormatter: (Float) -> String = ::defaultBahtFull,         // จัดรูปยอดเงินใน tooltip
) {
    val measurer = rememberTextMeasurer()
    val progress = chartRevealProgress(points)
    // จุดที่ผู้ใช้แตะค้างไว้ (-1 = ไม่เลือก) — รีเซ็ตเมื่อ data หรือช่วงเวลา (pointLabels) เปลี่ยน
    var selectedIndex by remember(points, pointLabels) { mutableStateOf(-1) }
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val n = points.size
                        val w = size.width.toFloat()
                        if (n < 2 || w <= 0f) return@detectTapGestures
                        val idx = (tap.x / w * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        selectedIndex = if (idx == selectedIndex) -1 else idx   // แตะจุดเดิมซ้ำ = ปิด tooltip
                    }
                }
        ) {
            if (points.size < 2) return@Canvas
            val min = points.min()
            val max = points.max()
            val span = (max - min).takeIf { it > 0f } ?: 1f
            val padTop = 34.dp.toPx()       // room for the peak bubble
            val padBottom = 8.dp.toPx()
            val usableH = size.height - padTop - padBottom
            val n = points.size
            val pts = points.mapIndexed { i, v ->
                val x = size.width * i / (n - 1)
                val y = padTop + (1f - (v - min) / span) * usableH
                Offset(x, y)
            }

            // dashed gridlines
            val gridColor = Color(0x80B6C2CF)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
            for (f in listOf(0.25f, 0.5f, 0.75f)) {
                val y = padTop + f * usableH
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }

            // area + line + node dots — reveal ซ้าย→ขวา
            val line = smoothPath(pts)
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            clipRect(right = size.width * progress) {
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(AeroPalette.Green.copy(alpha = 0.40f), AeroPalette.Green.copy(alpha = 0f)))
                )
                drawPath(
                    line,
                    brush = Brush.horizontalGradient(listOf(AeroPalette.GreenLo, AeroPalette.GreenHi)),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                pts.forEach { p ->
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = p)
                    drawCircle(AeroPalette.GreenLo, radius = 3.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
                }
            }

            val sel = selectedIndex
            if (sel in pts.indices && progress > 0.999f) {
                // ── จุดที่เลือก: เส้นไกด์ตั้ง + จุดไฮไลต์ + ป้ายรายละเอียด (วันที่ + ยอดเงินวันนั้น) ──
                val p = pts[sel]
                drawLine(
                    color = Color(0x66486072),
                    start = Offset(p.x, 0f),
                    end = Offset(p.x, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
                drawCircle(AeroPalette.GreenHi, radius = 5.5.dp.toPx(), center = p)
                drawCircle(Color.White, radius = 5.5.dp.toPx(), center = p, style = Stroke(2.5.dp.toPx()))
                val title = pointLabels.getOrNull(sel) ?: xLabels.getOrNull(sel) ?: ""
                drawAeroTooltip(measurer, p, title, listOf(valueFormatter(points[sel]) to null))
            } else {
                // peak marker — fade in ช่วงท้ายอนิเมชั่น (ซ่อนเมื่อผู้ใช้เลือกจุด)
                val peakAlpha = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
                if (peakAlpha > 0f) {
                    val peakIdx = points.indices.maxByOrNull { points[it] } ?: 0
                    val peak = pts[peakIdx]
                    drawCircle(AeroPalette.GreenHi.copy(alpha = peakAlpha), radius = 5.5.dp.toPx(), center = peak)
                    drawCircle(Color.White.copy(alpha = peakAlpha), radius = 5.5.dp.toPx(), center = peak, style = Stroke(2.5.dp.toPx()))
                    if (peakLabel != null) {
                        val layout = measurer.measure(
                            peakLabel,
                            style = TextStyle(
                                color = Color.White.copy(alpha = peakAlpha),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val padX = 7.dp.toPx()
                        val padBubbleY = 4.dp.toPx()
                        val bw = layout.size.width + padX * 2
                        val bh = layout.size.height + padBubbleY * 2
                        val bx = (peak.x - bw / 2).coerceIn(0f, size.width - bw)
                        val by = (peak.y - 10.dp.toPx() - bh).coerceAtLeast(0f)
                        drawRoundRect(
                            color = AeroPalette.NavyDeep.copy(alpha = peakAlpha),
                            topLeft = Offset(bx, by),
                            size = Size(bw, bh),
                            cornerRadius = CornerRadius(7.dp.toPx())
                        )
                        drawText(layout, topLeft = Offset(bx + padX, by + padBubbleY))
                    }
                }
            }
        }
        if (xLabels.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                xLabels.forEach { Text(it, fontSize = 11.sp, color = AeroPalette.InkFaint) }
            }
        }
    }
}

/**
 * กราฟธนาคารสองเส้น (เงินเข้า/เงินออก RAW จาก SMS ธนาคาร) — สไตล์เดียวกับ
 * [AeroAreaChart]: dashed grid + smooth line + soft area fill ต่อเส้น,
 * animate reveal ซ้าย→ขวา. ทั้งสองเส้นแชร์สเกลแกน Y เดียวกันเพื่อเทียบกันได้
 */
@Composable
fun AeroDualLineChart(
    creditPoints: List<Float>,
    debitPoints: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    creditColor: Color = AeroPalette.GreenLo,
    debitColor: Color = AeroPalette.Red,
    xLabels: List<String> = emptyList(),
    chartHeight: Dp = 120.dp,
    pointLabels: List<String> = emptyList(),                       // ป้ายเต็มต่อจุด (หัวข้อ tooltip)
    creditLabel: String = "เงินเข้า",
    debitLabel: String = "เงินออก",
    valueFormatter: (Float) -> String = ::defaultBahtFull,
) {
    val measurer = rememberTextMeasurer()
    val progress = chartRevealProgress(creditPoints to debitPoints)
    var selectedIndex by remember(creditPoints, debitPoints, pointLabels) { mutableStateOf(-1) }
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(creditPoints to debitPoints) {
                    detectTapGestures { tap ->
                        val n = maxOf(creditPoints.size, debitPoints.size)
                        val w = size.width.toFloat()
                        if (n < 2 || w <= 0f) return@detectTapGestures
                        val idx = (tap.x / w * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        selectedIndex = if (idx == selectedIndex) -1 else idx
                    }
                }
        ) {
            val n = maxOf(creditPoints.size, debitPoints.size)
            if (n < 2) return@Canvas
            val allValues = creditPoints + debitPoints
            val min = 0f // ยอดเงินเริ่มที่ 0 เสมอ — สเกลตรงไปตรงมา ไม่หลอกตา
            val max = (allValues.maxOrNull() ?: 0f).takeIf { it > 0f } ?: 1f
            val span = max - min
            val padTop = 10.dp.toPx()
            val padBottom = 8.dp.toPx()
            val usableH = size.height - padTop - padBottom

            // dashed gridlines
            val gridColor = Color(0x80B6C2CF)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
            for (f in listOf(0.25f, 0.5f, 0.75f)) {
                val y = padTop + f * usableH
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }

            fun yOf(v: Float): Float = padTop + (1f - (v - min) / span) * usableH
            fun toOffsets(points: List<Float>): List<Offset> = points.mapIndexed { i, v ->
                Offset(size.width * i / (n - 1), yOf(v))
            }

            fun drawSeries(points: List<Float>, color: Color) {
                if (points.size < 2) return
                val pts = toOffsets(points)
                val line = smoothPath(pts)
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)))
                )
                drawPath(line, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                pts.forEach { p ->
                    drawCircle(Color.White, radius = 2.5.dp.toPx(), center = p)
                    drawCircle(color, radius = 2.5.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))
                }
            }

            clipRect(right = size.width * progress) {
                drawSeries(debitPoints, debitColor)   // วาดเส้นแดงก่อน ให้เส้นเขียว (พระเอก) อยู่บน
                drawSeries(creditPoints, creditColor)
            }

            val sel = selectedIndex
            if (sel in 0 until n && progress > 0.999f) {
                // ── จุดที่เลือก: เส้นไกด์ + จุดไฮไลต์ทั้งสองเส้น + ป้าย (เงินเข้า/ออก วันนั้น) ──
                val gx = size.width * sel / (n - 1)
                drawLine(
                    color = Color(0x66486072),
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
                val rows = mutableListOf<Pair<String, Color?>>()
                var anchorY = size.height
                creditPoints.getOrNull(sel)?.let { v ->
                    val y = yOf(v)
                    drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(gx, y))
                    drawCircle(creditColor, radius = 4.dp.toPx(), center = Offset(gx, y), style = Stroke(2.dp.toPx()))
                    rows += "$creditLabel ${valueFormatter(v)}" to creditColor
                    if (y < anchorY) anchorY = y
                }
                debitPoints.getOrNull(sel)?.let { v ->
                    val y = yOf(v)
                    drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(gx, y))
                    drawCircle(debitColor, radius = 4.dp.toPx(), center = Offset(gx, y), style = Stroke(2.dp.toPx()))
                    rows += "$debitLabel ${valueFormatter(v)}" to debitColor
                    if (y < anchorY) anchorY = y
                }
                val title = pointLabels.getOrNull(sel) ?: xLabels.getOrNull(sel) ?: ""
                if (rows.isNotEmpty()) drawAeroTooltip(measurer, Offset(gx, anchorY), title, rows)
            }
        }
        if (xLabels.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                xLabels.forEach { Text(it, fontSize = 11.sp, color = AeroPalette.InkFaint) }
            }
        }
    }
}

/**
 * Single-value gloss donut (Stats match-rate). Green ring on a light track with
 * a centred percentage and caption.
 */
@Composable
fun MatchRateDonut(
    fraction: Float,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 118.dp,
    strokeWidth: Dp = 13.dp,
    centerSuffix: String = "%",
) {
    val progress = chartRevealProgress(fraction)
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2 + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = Color(0x809FB8C8),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.linearGradient(listOf(AeroPalette.GreenHi, AeroPalette.GreenLo)),
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f) * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(centerValue, fontSize = 30.sp, fontWeight = FontWeight.Black, color = AeroPalette.GreenDeep)
                if (centerSuffix.isNotEmpty()) {
                    Text(centerSuffix, fontSize = 16.sp, fontWeight = FontWeight.Black, color = AeroPalette.GreenDeep)
                }
            }
            Text(centerLabel, fontSize = 10.5.sp, color = AeroPalette.InkFaint)
        }
    }
}

/** ฿1,234.00 — รูปแบบยอดเงินเต็ม (มีทศนิยม) สำหรับ tooltip จุดข้อมูล */
private fun defaultBahtFull(value: Float): String =
    "฿" + String.format(Locale.US, "%,.2f", value)

/**
 * ป้ายรายละเอียด (tooltip) สไตล์ Aero — กล่อง navy โค้งมน วางเหนือ [anchor]
 * (ถ้าชนขอบบนจะพลิกไปวางใต้จุดแทน). บรรทัดแรก = หัวข้อ (วันที่), บรรทัดถัดมา =
 * แต่ละค่า โดยมีจุดสีนำหน้าถ้า [rows] ระบุสีไว้ (ใช้กับกราฟหลายเส้น).
 */
private fun DrawScope.drawAeroTooltip(
    measurer: TextMeasurer,
    anchor: Offset,
    title: String,
    rows: List<Pair<String, Color?>>,
) {
    if (rows.isEmpty()) return
    val titleStyle = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val rowStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val titleLayout = if (title.isNotEmpty()) measurer.measure(title, titleStyle) else null
    val rowLayouts = rows.map { measurer.measure(it.first, rowStyle) }

    val dot = 5.dp.toPx()
    val dotGap = 5.dp.toPx()
    val padX = 9.dp.toPx()
    val padY = 7.dp.toPx()
    val lineGap = 3.dp.toPx()
    val bulletReserve = if (rows.any { it.second != null }) dot + dotGap else 0f

    val contentW = maxOf(
        titleLayout?.size?.width?.toFloat() ?: 0f,
        rowLayouts.maxOfOrNull { bulletReserve + it.size.width } ?: 0f
    )
    val titleH = titleLayout?.size?.height?.toFloat() ?: 0f
    val rowsH = rowLayouts.sumOf { it.size.height }.toFloat() +
        lineGap * (rowLayouts.size - 1).coerceAtLeast(0)
    val gapTitleRows = if (titleLayout != null) lineGap + 1.dp.toPx() else 0f

    val bw = contentW + padX * 2
    val bh = titleH + gapTitleRows + rowsH + padY * 2
    val bx = (anchor.x - bw / 2f).coerceIn(0f, (size.width - bw).coerceAtLeast(0f))
    val gapAnchor = 12.dp.toPx()
    // วางเหนือจุด ถ้าพื้นที่ด้านบนไม่พอค่อยพลิกลงล่าง แล้ว clamp ให้กล่องอยู่ในกรอบ Canvas เสมอ
    // (กัน tooltip ล้นทะลุขอบล่างไปทับแถวป้ายแกน X ที่อยู่ใต้กราฟ)
    val above = anchor.y - gapAnchor - bh
    val by = (if (above >= 0f) above else anchor.y + gapAnchor)
        .coerceIn(0f, (size.height - bh).coerceAtLeast(0f))

    drawRoundRect(
        color = AeroPalette.NavyDeep.copy(alpha = 0.95f),
        topLeft = Offset(bx, by),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(9.dp.toPx())
    )

    var cy = by + padY
    if (titleLayout != null) {
        drawText(titleLayout, topLeft = Offset(bx + padX, cy))
        cy += titleH + gapTitleRows
    }
    rowLayouts.forEachIndexed { i, layout ->
        val rowColor = rows[i].second
        var tx = bx + padX
        if (rowColor != null) {
            drawCircle(rowColor, radius = dot / 2f, center = Offset(tx + dot / 2f, cy + layout.size.height / 2f))
            tx += dot + dotGap
        }
        drawText(layout, topLeft = Offset(tx, cy))
        cy += layout.size.height + lineGap
    }
}
