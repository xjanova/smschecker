package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.ui.theme.AeroPalette

/* =========================================================================
   Millennium 3D / Frutiger Aero — chart primitives.
   Self-contained Canvas drawings: smooth income sparkline, full area chart
   with dashed grid + node dots + peak-marker bubble, and a single-value
   gloss donut. Feed plain values; the screens map their ViewModel data in.
   ========================================================================= */

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
        drawPath(
            area,
            Brush.verticalGradient(listOf(fillColor.copy(alpha = 0.35f), fillColor.copy(alpha = 0f)))
        )
        drawPath(line, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
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
) {
    val measurer = rememberTextMeasurer()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
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

            // area + line
            val line = smoothPath(pts)
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(listOf(AeroPalette.Green.copy(alpha = 0.40f), AeroPalette.Green.copy(alpha = 0f)))
            )
            drawPath(
                line,
                brush = Brush.horizontalGradient(listOf(AeroPalette.GreenLo, AeroPalette.GreenHi)),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // node dots
            pts.forEach { p ->
                drawCircle(Color.White, radius = 3.dp.toPx(), center = p)
                drawCircle(AeroPalette.GreenLo, radius = 3.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
            }

            // peak marker
            val peakIdx = points.indices.maxByOrNull { points[it] } ?: 0
            val peak = pts[peakIdx]
            drawCircle(AeroPalette.GreenHi, radius = 5.5.dp.toPx(), center = peak)
            drawCircle(Color.White, radius = 5.5.dp.toPx(), center = peak, style = Stroke(2.5.dp.toPx()))
            if (peakLabel != null) {
                val layout = measurer.measure(
                    peakLabel,
                    style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
                val padX = 7.dp.toPx()
                val padBubbleY = 4.dp.toPx()
                val bw = layout.size.width + padX * 2
                val bh = layout.size.height + padBubbleY * 2
                val bx = (peak.x - bw / 2).coerceIn(0f, size.width - bw)
                val by = (peak.y - 10.dp.toPx() - bh).coerceAtLeast(0f)
                drawRoundRect(
                    color = AeroPalette.NavyDeep,
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(7.dp.toPx())
                )
                drawText(layout, topLeft = Offset(bx + padX, by + padBubbleY))
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
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
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
