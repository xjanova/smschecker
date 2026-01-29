package com.thaiprompt.smschecker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Premium Gold & Dark theme
private val GoldPrimary = Color(0xFFD4AF37)
private val GoldSecondary = Color(0xFFB8860B)
private val GoldTertiary = Color(0xFFF5DEB3)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E2E)
private val DarkSurfaceVariant = Color(0xFF2A2A3A)
private val SuccessGreen = Color(0xFF4CAF50)
private val ErrorRed = Color(0xFFE53935)
private val InfoBlue = Color(0xFF2196F3)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    secondary = GoldSecondary,
    tertiary = GoldTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCACACA),
    error = ErrorRed,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A237E),
    secondary = GoldPrimary,
    tertiary = Color(0xFF0D47A1),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F5),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    error = ErrorRed,
)

object AppColors {
    val CreditGreen = SuccessGreen
    val DebitRed = ErrorRed
    val InfoBlue = Color(0xFF2196F3)
    val WarningOrange = Color(0xFFFF9800)
    val GoldAccent = GoldPrimary
    val CardGradientStart = Color(0xFF1A237E)
    val CardGradientEnd = Color(0xFF0D47A1)
    val DarkCardGradientStart = Color(0xFF2A2A3A)
    val DarkCardGradientEnd = Color(0xFF1E1E2E)
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
