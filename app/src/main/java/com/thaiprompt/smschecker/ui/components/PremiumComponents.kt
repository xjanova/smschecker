package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.ui.theme.AppColors

/**
 * A gradient header banner with gold accent line at the bottom.
 */
@Composable
fun GradientHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1F35),
                        Color(0xFF141824),
                        Color(0xFF0D1117)
                    )
                )
            )
    ) {
        // Subtle gold accent dots / glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AppColors.GoldAccent.copy(alpha = 0.6f),
                            AppColors.GoldLight.copy(alpha = 0.8f),
                            AppColors.GoldAccent.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
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
                color = AppColors.GlassCardBorder,
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
            .border(1.dp, AppColors.GlassCardBorder, RoundedCornerShape(16.dp))
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
 * Gradient background for the entire screen (use as root modifier).
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
