package com.thaiprompt.smschecker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Premium Gold & Dark Navy theme
private val GoldPrimary = Color(0xFFD4AF37)
private val GoldSecondary = Color(0xFFB8860B)
private val GoldTertiary = Color(0xFFF5DEB3)

// Dark palette — deep navy/charcoal
private val DarkNavy = Color(0xFF0D1117)
private val DarkSurface = Color(0xFF161B22)
private val DarkSurfaceVariant = Color(0xFF21262D)

// Accent colors
private val SuccessGreen = Color(0xFF3FB950)
private val ErrorRed = Color(0xFFE5534B)
private val WarningAmber = Color(0xFFE3B341)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    secondary = GoldSecondary,
    tertiary = GoldTertiary,
    background = DarkNavy,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF8B949E),
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
)

// Light palette - Green theme to match app branding
private val PrimaryGreen = Color(0xFF2E7D32)  // Main green
private val DarkGreen = Color(0xFF1B5E20)     // Darker green for contrast

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    secondary = GoldPrimary,
    tertiary = DarkGreen,
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F2F5),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    error = ErrorRed,
)

object AppColors {
    val CreditGreen = SuccessGreen
    val DebitRed = ErrorRed
    val InfoBlue = Color(0xFF58A6FF)
    val WarningOrange = WarningAmber
    val GoldAccent = GoldPrimary
    val GoldLight = Color(0xFFE8D48B)
    val GoldDark = GoldSecondary

    val CardGradientStart = Color(0xFF2E7D32)  // Green gradient
    val CardGradientEnd = Color(0xFF1B5E20)
    val DarkCardGradientStart = DarkSurfaceVariant
    val DarkCardGradientEnd = DarkSurface

    // Premium gradient brushes
    val HeaderGradientDark = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1F35), Color(0xFF0D1117))
    )

    val GoldShimmer = Brush.horizontalGradient(
        colors = listOf(
            GoldSecondary,
            GoldPrimary,
            GoldTertiary,
            GoldPrimary,
            GoldSecondary
        )
    )

    val NavBarGradientDark = Brush.verticalGradient(
        colors = listOf(DarkSurface, Color(0xFF0A0E14))
    )

    val GlassCardBorder = Color(0xFF30363D)
    val GlassCardBorderLight = Color(0xFFD0D7DE)

    val HeaderGradientLight = Brush.verticalGradient(
        colors = listOf(Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF43A047))  // Green gradient
    )

    val NavBarGradientLight = Brush.verticalGradient(
        colors = listOf(Color.White, Color(0xFFF6F8FA))
    )
}

@Composable
fun SmsCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Status bar theming
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = if (darkTheme) DarkNavy.toArgb() else Color(0xFF2E7D32).toArgb()  // Green status bar
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    val typography = Typography(
        headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.25).sp),
        headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.4.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
