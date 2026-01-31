package com.thaiprompt.smschecker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 * A gradient header banner with green gradient shades.
 * When isMonitoring = true, shows a rainbow RGB animated accent line.
 */
@Composable
fun GradientHeader(
    modifier: Modifier = Modifier,
    isMonitoring: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isEffectivelyDark()
    // Green gradient shades
    val gradientColors = if (isDark) {
        listOf(Color(0xFF0A2E1A), Color(0xFF0D3B22), Color(0xFF062013))
    } else {
        listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047))
    }

    // Rainbow animation for monitoring mode
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

    // Full rainbow colors
    val rainbowColors = listOf(
        Color(0xFFFF0000), // Red
        Color(0xFFFF7700), // Orange
        Color(0xFFFFFF00), // Yellow
        Color(0xFF00FF00), // Green
        Color(0xFF0077FF), // Blue
        Color(0xFF8B00FF), // Violet
        Color(0xFFFF00FF), // Magenta
        Color(0xFFFF0000)  // Red (loop back)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brush.verticalGradient(colors = gradientColors))
    ) {
        // Accent line at bottom — rainbow when monitoring, green when idle
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
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = shiftedColors.map { it.second }
                                )
                            )
                        }
                    } else {
                        Modifier.background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AppColors.CreditGreen.copy(alpha = 0.6f),
                                    Color(0xFF81C784).copy(alpha = 0.8f),
                                    AppColors.CreditGreen.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                )
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            content = content
        )
    }
}

/**
 * A premium glass-effect card with subtle border and gradient background.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/**
 * A card with a gold accent left border line.
 */
@Composable
fun GoldAccentCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        // Gold accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColors.GoldAccent,
                            AppColors.GoldDark
                        )
                    )
                )
        )
        content()
    }
}

/**
 * Section title with optional gold divider line.
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
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                AppColors.GoldAccent,
                                AppColors.GoldAccent.copy(alpha = 0.3f)
                            )
                        ),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

/**
 * Gradient background brush for the entire screen - adapts to theme.
 */
@Composable
fun premiumBackgroundBrush(): Brush {
    val bg = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(
        colors = listOf(bg, bg.copy(alpha = 0.97f), bg)
    )
}

/**
 * Gradient background modifier - for theme-aware usage, use background(premiumBackgroundBrush()).
 */
fun Modifier.premiumBackground(): Modifier = this.background(
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D1117),
            Color(0xFF0F1318),
            Color(0xFF0D1117)
        )
    )
)
