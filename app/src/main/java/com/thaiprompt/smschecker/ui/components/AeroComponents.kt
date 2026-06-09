package com.thaiprompt.smschecker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.ui.theme.AeroPalette

/* =========================================================================
   Millennium 3D / Frutiger Aero — reusable component kit.
   Glass panels, gloss buttons, chrome segmented controls, glossy orbs,
   bank "coins", chips, section headers, stat tiles, bank bars.
   ========================================================================= */

// ----------------------------------------------------------------------------
// Background — the sky canvas (light) / deep navy (dark)
// ----------------------------------------------------------------------------
@Composable
fun aeroBackgroundBrush(dark: Boolean = isEffectivelyDark()): Brush =
    if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF12203A),
                Color(0xFF0E1626),
                Color(0xFF0A1322),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEAF6F3),
                Color(0xFFE6F1F4),
                Color(0xFFE9F3EE),
            )
        )
    }

/** A full-bleed Aero background box. Children render above the sky gradient. */
@Composable
fun AeroBackground(
    modifier: Modifier = Modifier,
    dark: Boolean = isEffectivelyDark(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.background(aeroBackgroundBrush(dark)), content = content)
}

// ----------------------------------------------------------------------------
// Glass — translucent panel with white rim, soft deep shadow and a top gloss
// ----------------------------------------------------------------------------
@Composable
fun aeroGlassBrush(dark: Boolean = isEffectivelyDark()): Brush =
    if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x26FFFFFF),
                Color(0x14FFFFFF),
                Color(0x1A2A4066),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xD1FFFFFF),
                Color(0x8CFFFFFF),
                Color(0x9EF1F8FA),
            )
        )
    }

@Composable
fun glassBorder(dark: Boolean = isEffectivelyDark()): BorderStroke =
    BorderStroke(1.dp, if (dark) Color(0x33FFFFFF) else Color(0xE6FFFFFF))

/**
 * Aero glass surface. Includes the soft deep shadow, the translucent fill,
 * the white rim, and the top "gloss sweep" highlight.
 */
@Composable
fun AeroGlass(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    dark: Boolean = isEffectivelyDark(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (dark) 10.dp else 14.dp,
                shape = shape,
                clip = false,
                ambientColor = AeroPalette.NavyDeep,
                spotColor = AeroPalette.NavyDeep,
            )
            .clip(shape)
            .background(aeroGlassBrush(dark))
            .border(glassBorder(dark), shape)
    ) {
        // top gloss sweep (::before) — matchParentSize renders at any card height
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to (if (dark) Color(0x33FFFFFF) else Color(0xD9FFFFFF)),
                            0.42f to Color(0x00FFFFFF),
                            1f to Color(0x00FFFFFF),
                        )
                    )
                )
        )
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

// ----------------------------------------------------------------------------
// Chrome bevel — the silver metal strip used behind segmented controls
// ----------------------------------------------------------------------------
fun chromeBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        AeroPalette.Chrome1,
        AeroPalette.Chrome2,
        AeroPalette.Chrome3,
        AeroPalette.Chrome2,
    )
)

// ----------------------------------------------------------------------------
// Gloss button
// ----------------------------------------------------------------------------
enum class GlossStyle { Green, Navy, Red, Ghost }

@Composable
fun GlossButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlossStyle = GlossStyle.Green,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    fontSize: Int = 15,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "glossPress")

    val fillBrush = when (style) {
        GlossStyle.Green -> Brush.verticalGradient(listOf(AeroPalette.GreenHi, AeroPalette.Green, AeroPalette.GreenLo))
        GlossStyle.Navy -> Brush.verticalGradient(listOf(AeroPalette.NavyHi, AeroPalette.Navy, AeroPalette.NavyDeep))
        GlossStyle.Red -> Brush.verticalGradient(listOf(AeroPalette.RedHi, AeroPalette.Red, AeroPalette.RedLo))
        GlossStyle.Ghost -> Brush.verticalGradient(listOf(Color(0xEBFFFFFF), Color(0xCCF2F7FA)))
    }
    val contentColor = if (style == GlossStyle.Ghost) AeroPalette.Navy else Color.White
    val glow = when (style) {
        GlossStyle.Green -> AeroPalette.GreenLo
        GlossStyle.Navy -> AeroPalette.NavyDeep
        GlossStyle.Red -> AeroPalette.RedLo
        GlossStyle.Ghost -> AeroPalette.Navy
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(if (enabled) 10.dp else 0.dp, shape, clip = false, spotColor = glow, ambientColor = glow)
            .clip(shape)
            .background(if (enabled) fillBrush else Brush.verticalGradient(listOf(Color(0xFFB8C2CC), Color(0xFF98A4B0))))
            .border(
                1.dp,
                if (style == GlossStyle.Ghost) Color(0xFFFFFFFF) else Color(0x4DFFFFFF),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // top gloss half
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 3.dp, vertical = 2.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xCCFFFFFF),
                            0.5f to Color(0x00FFFFFF),
                            1f to Color(0x00FFFFFF),
                        )
                    )
                )
        )
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size((fontSize + 3).dp))
            }
            Text(
                text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/** Icon-only gloss button (e.g. the ✕ reject). */
@Composable
fun GlossIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlossStyle = GlossStyle.Ghost,
    size: Dp = 46.dp,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(50)
    val fillBrush = when (style) {
        GlossStyle.Green -> Brush.verticalGradient(listOf(AeroPalette.GreenHi, AeroPalette.Green, AeroPalette.GreenLo))
        GlossStyle.Navy -> Brush.verticalGradient(listOf(AeroPalette.NavyHi, AeroPalette.Navy, AeroPalette.NavyDeep))
        GlossStyle.Red -> Brush.verticalGradient(listOf(AeroPalette.RedHi, AeroPalette.Red, AeroPalette.RedLo))
        GlossStyle.Ghost -> Brush.verticalGradient(listOf(Color(0xEBFFFFFF), Color(0xCCF2F7FA)))
    }
    val contentColor = if (style == GlossStyle.Ghost) AeroPalette.Navy else Color.White
    Box(
        modifier = modifier
            .size(size)
            .shadow(7.dp, shape, clip = false, spotColor = AeroPalette.NavyDeep)
            .clip(shape)
            .background(fillBrush)
            .border(1.dp, Color(0x66FFFFFF), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xB3FFFFFF),
                            0.5f to Color(0x00FFFFFF),
                            1f to Color(0x00FFFFFF),
                        )
                    )
                )
        )
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(size * 0.42f))
    }
}

// ----------------------------------------------------------------------------
// Chrome segmented control
// ----------------------------------------------------------------------------
@Composable
fun ChromeSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(chromeBrush())
            .border(1.dp, Color(0x80FFFFFF), outerShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            val pillShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .then(
                        if (on) Modifier
                            .background(Brush.verticalGradient(listOf(AeroPalette.GreenHi, AeroPalette.GreenLo)))
                            .border(1.dp, Color(0x66FFFFFF), pillShape)
                        else Modifier
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) Color.White else AeroPalette.NavyDeep,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Glossy orb (icon avatar) — gradient sphere with a top water-drop highlight
// ----------------------------------------------------------------------------
@Composable
fun GlossyOrb(
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, shape, clip = false, spotColor = AeroPalette.NavyDeep)
            .clip(shape)
            .background(Brush.verticalGradient(gradient))
            .border(1.dp, Color(0x4DFFFFFF), shape),
        contentAlignment = Alignment.Center
    ) {
        // water-drop gloss top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.64f)
                .fillMaxHeight(0.42f)
                .padding(top = size * 0.10f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xD9FFFFFF), Color(0x00FFFFFF))))
        )
        content()
    }
}

/** Convenience: a glossy orb with an icon centered inside. */
@Composable
fun OrbIcon(
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconTint: Color = Color.White,
) {
    GlossyOrb(gradient = gradient, modifier = modifier, size = size) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.5f))
    }
}

// ----------------------------------------------------------------------------
// Bank coin — glossy squircle with the bank's brand colour + initials
// ----------------------------------------------------------------------------
fun bankBrandColor(code: String): Color = when (code.uppercase()) {
    "KBANK" -> Color(0xFF138F2D)
    "SCB" -> Color(0xFF4E2A84)
    "KTB" -> Color(0xFF00A4E4)
    "BBL" -> Color(0xFF1E4598)
    "GSB" -> Color(0xFFEB198D)
    "BAY" -> Color(0xFFFEC43B)
    "TTB" -> Color(0xFF1279BE)
    "CIMB" -> Color(0xFF7A1F2B)
    "UOB" -> Color(0xFF0B3D91)
    "PROMPTPAY" -> Color(0xFF003B71)
    "KKP" -> Color(0xFF00A0AF)
    "LH" -> Color(0xFF6E2B62)
    "TISCO" -> Color(0xFF00529C)
    "ICBC" -> Color(0xFFC8102E)
    "BAAC" -> Color(0xFF00A950)
    else -> Color(0xFF6B7682)
}

fun bankInitials(code: String): String = when (code.uppercase()) {
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
    "TISCO" -> "TIS"
    "UOB" -> "UOB"
    "ICBC" -> "ICBC"
    "BAAC" -> "ธกส"
    else -> code.take(3).uppercase()
}

private fun lighten(c: Color, f: Float = 0.45f): Color = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
    alpha = 1f
)

@Composable
fun BankCoin(
    bankCode: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    grayscale: Boolean = false,
) {
    val base = if (grayscale) Color(0xFF8A93A0) else bankBrandColor(bankCode)
    val shape = RoundedCornerShape(percent = 30)
    val initials = bankInitials(bankCode)
    val textColor = if (bankCode.uppercase() == "BAY") Color(0xFF333333) else Color.White
    val fontSize = when {
        initials.length <= 1 -> size.value * 0.46f
        initials.length <= 2 -> size.value * 0.34f
        initials.length <= 3 -> size.value * 0.27f
        else -> size.value * 0.21f
    }
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, shape, clip = false, spotColor = AeroPalette.NavyDeep)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lighten(base), base)))
            .border(1.5.dp, Color(0xEBFFFFFF), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.40f)
                .padding(top = size * 0.07f)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x00FFFFFF))))
        )
        Text(
            initials,
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ----------------------------------------------------------------------------
// Chips / badges
// ----------------------------------------------------------------------------
enum class ChipStyle { Green, Amber, Red, Aqua, Glass, GlassOnDark }

@Composable
fun AeroChip(
    text: String,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Green,
    leadingIcon: ImageVector? = null,
) {
    val (fill, content) = when (style) {
        ChipStyle.Green -> Brush.verticalGradient(listOf(Color(0xFFCBF3D7), Color(0xFFA9E9BE))) to AeroPalette.GreenDeep
        ChipStyle.Amber -> Brush.verticalGradient(listOf(Color(0xFFFCEFC4), Color(0xFFF6E2A0))) to AeroPalette.GoldText
        ChipStyle.Red -> Brush.verticalGradient(listOf(Color(0xFFF8D2CB), Color(0xFFF1B5AB))) to AeroPalette.RedLo
        ChipStyle.Aqua -> Brush.verticalGradient(listOf(Color(0xFFCFEAF1), Color(0xFFB2DCE7))) to Color(0xFF114A55)
        ChipStyle.Glass -> Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xB3F1F8FA))) to AeroPalette.Navy
        ChipStyle.GlassOnDark -> Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x1FFFFFFF))) to Color.White
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, Color(0xB3FFFFFF), shape)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        }
        Text(text, color = content, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1)
    }
}

/** Pill-shaped filter chip — money-green gloss when selected, glass when not. */
@Composable
fun AeroPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) Modifier
                    .background(Brush.verticalGradient(listOf(AeroPalette.GreenHi, AeroPalette.GreenLo)))
                    .border(1.dp, Color(0x66FFFFFF), shape)
                else Modifier
                    .background(aeroGlassBrush())
                    .border(glassBorder(), shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5.sp,
            maxLines = 1
        )
    }
}

// ----------------------------------------------------------------------------
// Section header  (icon + title  ........  optional action)
// ----------------------------------------------------------------------------
@Composable
fun AeroSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = AeroPalette.GreenLo, modifier = Modifier.size(16.dp))
            }
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (actionText != null) {
            Text(
                actionText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = AeroPalette.GreenDeep,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Stat tile — small glass card with a corner orb icon + label + big number
// ----------------------------------------------------------------------------
@Composable
fun AeroStatTile(
    label: String,
    value: String,
    orbIcon: ImageVector,
    orbGradient: List<Color>,
    modifier: Modifier = Modifier,
    valueColor: Color = AeroPalette.NavyDeep,
) {
    AeroGlass(modifier = modifier, cornerRadius = 18.dp, contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp)) {
        OrbIcon(
            icon = orbIcon,
            gradient = orbGradient,
            size = 30.dp,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 28.sp, maxLines = 1)
        }
    }
}

// ----------------------------------------------------------------------------
// Bank bar — coin + name + progress track + value (dashboard "popular banks")
// ----------------------------------------------------------------------------
@Composable
fun BankBar(
    bankCode: String,
    bankLabel: String,
    fraction: Float,
    valueText: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x4D9FB8C8)))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            BankCoin(bankCode = bankCode, size = 34.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    buildBankLine(bankCode, bankLabel),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x809FB8C8))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(bankBrandColor(bankCode), lighten(bankBrandColor(bankCode)))
                                )
                            )
                    )
                }
            }
            Text(valueText, fontWeight = FontWeight.Black, fontSize = 15.sp, color = AeroPalette.NavyDeep)
        }
    }
}

private fun buildBankLine(code: String, label: String): String =
    if (label.isBlank()) code else "$code · $label"

// ----------------------------------------------------------------------------
// Shared orb gradient presets
// ----------------------------------------------------------------------------
object OrbGradients {
    val Green = listOf(AeroPalette.GreenHi, AeroPalette.GreenLo)
    val Aqua = listOf(AeroPalette.AquaHi, AeroPalette.Aqua)
    val Gold = listOf(AeroPalette.GoldHi, AeroPalette.Gold)
    val Navy = listOf(Color(0xFF3A4A63), AeroPalette.NavyDeep)
    val Red = listOf(AeroPalette.RedHi, AeroPalette.Red)
}
