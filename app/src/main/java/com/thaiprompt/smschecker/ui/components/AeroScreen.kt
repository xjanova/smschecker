package com.thaiprompt.smschecker.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.thaiprompt.smschecker.ui.theme.AeroPalette

/* =========================================================================
   Millennium 3D / Frutiger Aero — per-screen header tone.
   The reference design gives each screen a coloured gradient that bleeds from
   the very top (behind the status bar) and fades to transparent into the sky
   canvas: linear-gradient(180deg, <top> 0%, <mid> ~24%, transparent ~46%).
   ========================================================================= */
enum class HeaderTone(val top: Color, val mid: Color, val statusBar: Color) {
    // Dashboard / Transactions / Stats — money green
    Green(top = AeroPalette.GreenLo, mid = AeroPalette.Green, statusBar = AeroPalette.GreenLo),
    // Orders — navy depth
    Navy(top = AeroPalette.Navy, mid = AeroPalette.NavyDeep, statusBar = AeroPalette.Navy),
    // SMS History — aqua / sea
    Aqua(top = AeroPalette.Aqua, mid = Color(0xFF3FA6BC), statusBar = Color(0xFF3FA6BC)),
}

/**
 * Full-bleed tone scrim. Apply to a screen's root Box(Modifier.fillMaxSize()).
 * Draws the coloured header gradient behind everything, fading to transparent so
 * the sky background shows through the rest of the screen.
 */
fun Modifier.aeroHeaderBleed(tone: HeaderTone): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to tone.top,
                0.16f to tone.mid,
                0.42f to Color.Transparent,
            ),
            startY = 0f,
            endY = size.height,
        )
    )
}

/** Sets the Android status-bar colour to match the screen's header tone (white icons). */
@Composable
fun StatusBarTone(tone: HeaderTone) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = tone.statusBar.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
}

/**
 * Standard Aero app-header block: white content over the tone scrim, padded below
 * the status bar. Matches `.appbar` in the handoff (padding 6/18/16, white text).
 * The coloured background comes from [aeroHeaderBleed] on the screen root, so this
 * block itself is transparent.
 */
@Composable
fun AeroHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Content already sits below the status bar (MainApp's Scaffold insets it),
    // so we only apply the .appbar inner padding here (6/18/16).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
        content = content,
    )
}

/**
 * Radial dark-navy background for the immersive QR Setup screen (design 05):
 * radial-gradient(120% 80% at 50% 0%, navy, navy-deep 60%, #06090e 100%).
 * Size-aware so the centre/radius scale to the screen.
 */
fun Modifier.darkNavyRadial(): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(AeroPalette.Navy, AeroPalette.NavyDeep, Color(0xFF06090E)),
            center = Offset(size.width * 0.5f, 0f),
            radius = size.height * 0.95f,
        )
    )
}
