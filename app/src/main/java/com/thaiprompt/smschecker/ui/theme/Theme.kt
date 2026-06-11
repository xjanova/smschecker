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

/* =========================================================================
   "Millennium 3D" / Frutiger Aero design system
   Anchored to the logo: chrome shield, navy depth, money-green, gold trim.
   HEX values are the sRGB approximations of the oklch() tokens in the handoff.
   ========================================================================= */

object AeroPalette {
    // --- money green (primary) ---
    val Green = Color(0xFF22B34A)       // oklch(0.74 0.20 146) — primary / success
    val GreenHi = Color(0xFF5CE98A)     // oklch(0.86 0.17 148) — highlight, icon on shield
    val GreenLo = Color(0xFF1A8A3C)     // oklch(0.58 0.17 147) — dark green / chart line
    val GreenDeep = Color(0xFF0F6E30)   // oklch(0.46 0.13 150) — credit amount text

    // --- aqua / sky ---
    val Aqua = Color(0xFF5EC8D6)        // oklch(0.80 0.10 200) — accent
    val AquaHi = Color(0xFFC1E8EF)      // oklch(0.92 0.06 205)
    val Sky = Color(0xFF9FCFE6)         // oklch(0.86 0.07 220) — background

    // --- navy (depth) ---
    val Navy = Color(0xFF2A3A63)        // oklch(0.30 0.075 258)
    val NavyHi = Color(0xFF46587F)      // oklch(0.42 0.085 260)
    val NavyDeep = Color(0xFF16223F)    // oklch(0.22 0.06 260)

    // --- gold (pending / trim) ---
    val Gold = Color(0xFFD8A93F)        // oklch(0.80 0.125 86)
    val GoldHi = Color(0xFFF2DD9A)      // oklch(0.92 0.09 92)
    val GoldText = Color(0xFF8A6A1E)    // readable gold-ink for "pending" labels

    // --- orange (force approve / caution action) ---
    val Orange = Color(0xFFE8821E)      // oklch(0.72 0.16 60)
    val OrangeHi = Color(0xFFF6A94F)    // oklch(0.81 0.14 68)
    val OrangeLo = Color(0xFFC2660A)    // oklch(0.60 0.14 58)

    // --- red (debit / reject) ---
    val Red = Color(0xFFD6452E)         // oklch(0.62 0.20 26)
    val RedHi = Color(0xFFEF6A4D)       // oklch(0.72 0.18 30)
    val RedLo = Color(0xFFA8331F)

    // --- ink (3 text levels, light theme) ---
    val Ink = Color(0xFF36404E)         // oklch(0.30 0.04 258)
    val InkSoft = Color(0xFF5D6775)     // oklch(0.46 0.03 256)
    val InkFaint = Color(0xFF8B95A1)    // oklch(0.62 0.02 250)

    // --- ink (dark theme) ---
    val InkDark = Color(0xFFDCE6F2)
    val InkSoftDark = Color(0xFFA8B4C6)
    val InkFaintDark = Color(0xFF7A8699)

    // --- chrome bezel metal ---
    val Chrome1 = Color(0xFFFDFEFE)
    val Chrome2 = Color(0xFFD6DDE4)
    val Chrome3 = Color(0xFFA7B2BD)
    val Chrome4 = Color(0xFF7D8997)

    // --- surfaces ---
    val ScreenLight = Color(0xFFEDF4F4)   // .screen bg
    val GlassWhiteHi = Color(0xFFFFFFFF)
    val GlassWhiteLo = Color(0xFFF3F8FA)
}

private val LightColorScheme = lightColorScheme(
    primary = AeroPalette.Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3D9),
    onPrimaryContainer = AeroPalette.GreenDeep,
    secondary = AeroPalette.Aqua,
    onSecondary = Color.White,
    secondaryContainer = AeroPalette.AquaHi,
    onSecondaryContainer = Color(0xFF114A55),
    tertiary = AeroPalette.Gold,
    onTertiary = Color.White,
    background = AeroPalette.ScreenLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFEAF2F5),
    onBackground = AeroPalette.Ink,
    onSurface = AeroPalette.Ink,
    onSurfaceVariant = AeroPalette.InkSoft,
    error = AeroPalette.Red,
    onError = Color.White,
    outline = Color(0xFFC9D6DE),
    outlineVariant = Color(0xFFDCE6EC),
)

private val DarkColorScheme = darkColorScheme(
    primary = AeroPalette.GreenHi,
    onPrimary = Color(0xFF06301A),
    primaryContainer = AeroPalette.GreenLo,
    onPrimaryContainer = Color(0xFFCFF3D9),
    secondary = AeroPalette.Aqua,
    onSecondary = Color(0xFF06222A),
    tertiary = AeroPalette.GoldHi,
    onTertiary = Color(0xFF3A2A06),
    background = Color(0xFF0E1626),
    surface = AeroPalette.NavyDeep,
    surfaceVariant = Color(0xFF1E2C4A),
    onBackground = AeroPalette.InkDark,
    onSurface = AeroPalette.InkDark,
    onSurfaceVariant = AeroPalette.InkSoftDark,
    error = AeroPalette.RedHi,
    onError = Color.White,
    outline = Color(0xFF2E3E5E),
    outlineVariant = Color(0xFF243450),
)

/**
 * AppColors — kept as the app-wide palette accessor. Names are preserved for
 * backward-compatibility (referenced 270+ times); values now map to the
 * Millennium 3D / Aero palette. New Aero tokens are appended below.
 */
object AppColors {
    // ----- legacy names (repointed to Aero) -----
    val SuccessGreen = AeroPalette.Green
    val CreditGreen = AeroPalette.Green
    val DebitRed = AeroPalette.Red
    val InfoBlue = AeroPalette.Aqua
    val WarningOrange = AeroPalette.Gold
    val GoldAccent = AeroPalette.Gold
    val GoldLight = AeroPalette.GoldHi
    val GoldDark = Color(0xFFB8860B)

    val CardGradientStart = AeroPalette.Green
    val CardGradientEnd = AeroPalette.GreenLo
    val DarkCardGradientStart = Color(0xFF1E2C4A)
    val DarkCardGradientEnd = AeroPalette.NavyDeep

    val HeaderGradientDark = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A2540), AeroPalette.NavyDeep)
    )
    val GoldShimmer = Brush.horizontalGradient(
        colors = listOf(AeroPalette.Gold, AeroPalette.GoldHi, AeroPalette.Gold)
    )
    val NavBarGradientDark = Brush.verticalGradient(
        colors = listOf(AeroPalette.NavyDeep, Color(0xFF0A1020))
    )
    val GlassCardBorder = Color(0xFF2E3E5E)
    val GlassCardBorderLight = Color(0x99FFFFFF)
    val HeaderGradientLight = Brush.verticalGradient(
        colors = listOf(AeroPalette.GreenLo, AeroPalette.Green, AeroPalette.GreenHi)
    )
    val NavBarGradientLight = Brush.verticalGradient(
        colors = listOf(Color.White, Color(0xFFEAF2F5))
    )

    // ----- Aero tokens (canonical) -----
    val Green = AeroPalette.Green
    val GreenHi = AeroPalette.GreenHi
    val GreenLo = AeroPalette.GreenLo
    val GreenDeep = AeroPalette.GreenDeep
    val Aqua = AeroPalette.Aqua
    val AquaHi = AeroPalette.AquaHi
    val Sky = AeroPalette.Sky
    val Navy = AeroPalette.Navy
    val NavyHi = AeroPalette.NavyHi
    val NavyDeep = AeroPalette.NavyDeep
    val Gold = AeroPalette.Gold
    val GoldHi = AeroPalette.GoldHi
    val Red = AeroPalette.Red
    val RedHi = AeroPalette.RedHi
    val Ink = AeroPalette.Ink
    val InkSoft = AeroPalette.InkSoft
    val InkFaint = AeroPalette.InkFaint
    val Chrome1 = AeroPalette.Chrome1
    val Chrome2 = AeroPalette.Chrome2
    val Chrome3 = AeroPalette.Chrome3
    val Chrome4 = AeroPalette.Chrome4
}

@Composable
fun SmsCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // disabled: Aero brand palette is intentional
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Status bar theming — match the dominant Aero header tone.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor =
                    (if (darkTheme) AeroPalette.NavyDeep else AeroPalette.GreenLo).toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    val typography = Typography(
        headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, letterSpacing = (-0.25).sp),
        headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.1.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = 0.2.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.15.sp),
        bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 0.3.sp),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
