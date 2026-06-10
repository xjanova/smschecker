package com.thaiprompt.smschecker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalThemeMode
import com.thaiprompt.smschecker.ui.theme.ThemeMode

/**
 * Returns true if the current theme is effectively dark.
 */
@Composable
fun isEffectivelyDark(): Boolean {
    val themeMode = LocalThemeMode.current
    return when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

/**
 * Millennium 3D header — money-green gloss banner with a rounded base. The
 * content (title, controls) renders in white over the gradient. When
 * isMonitoring = true, an animated rainbow accent line sweeps along the base;
 * otherwise a soft green gloss line.
 */
@Composable
fun GradientHeader(
    modifier: Modifier = Modifier,
    isMonitoring: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isEffectivelyDark()
    val gradientColors = if (dark) {
        listOf(Color(0xFF16223F), Color(0xFF1A2A4A), Color(0xFF12203A))
    } else {
        listOf(AeroPalette.GreenLo, AeroPalette.Green, Color(0xFF2BA94E))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    val rainbowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbowShift"
    )

    val rainbowColors = listOf(
        Color(0xFFFF0000), Color(0xFFFF7700), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF0077FF), Color(0xFF8B00FF), Color(0xFFFF00FF), Color(0xFFFF0000)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(Brush.verticalGradient(colors = gradientColors))
    ) {
        // top gloss sweep
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0x40FFFFFF),
                            0.5f to Color(0x00FFFFFF),
                            1f to Color(0x00FFFFFF),
                        )
                    )
                )
        )
        // accent line at base
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .then(
                    if (isMonitoring) {
                        Modifier.drawBehind {
                            val shiftedColors = rainbowColors.mapIndexed { index, color ->
                                val shift = (rainbowOffset + index.toFloat() / rainbowColors.size) % 1f
                                shift to color
                            }.sortedBy { it.first }
                            drawRect(brush = Brush.horizontalGradient(colors = shiftedColors.map { it.second }))
                        }
                    } else {
                        Modifier.background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AeroPalette.GreenHi.copy(alpha = 0.7f),
                                    Color.White.copy(alpha = 0.85f),
                                    AeroPalette.GreenHi.copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                )
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 18.dp),
            content = content
        )
    }
}

/**
 * Premium glass card — translucent panel with a white rim, soft deep shadow and
 * a top gloss sweep. Content lives in a padded Column (16dp), matching the
 * previous API so existing call-sites are unchanged.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    AeroGlass(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = cornerRadius,
        contentPadding = contentPadding
    ) {
        Column(content = content)
    }
}

/**
 * Glass card with a money-green accent bar down the left edge.
 */
@Composable
fun GoldAccentCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    AeroGlass(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(listOf(AeroPalette.GreenHi, AeroPalette.GreenLo))
                    )
            )
            content()
        }
    }
}

/**
 * Section title with a money-green gloss underline.
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (showDivider) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(AeroPalette.GreenHi, AeroPalette.GreenLo.copy(alpha = 0.3f))
                        )
                    )
            )
        }
    }
}

/**
 * Sky-canvas background brush for full screens — adapts to light/dark theme.
 */
@Composable
fun premiumBackgroundBrush(): Brush = aeroBackgroundBrush()

/**
 * Deep navy Aero background modifier (used by dark, immersive screens).
 */
fun Modifier.premiumBackground(): Modifier = this.background(
    Brush.verticalGradient(
        colors = listOf(Color(0xFF12203A), Color(0xFF0E1626), Color(0xFF0A1322))
    )
)
