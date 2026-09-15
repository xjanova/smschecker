package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings

/** โทนเดียวกับชิปอื่นบนการ์ด (พื้น tint จาง + ขอบ) — teal = เว็บปกติ, ส้มแดง = ชนหลายเว็บ */
private val SiteTeal = Color(0xFF0B8595)
private val SiteWarning = Color(0xFFD9480F)

/**
 * 🌐 (2026-09-15) ชิปบอกว่ายอดเงินเข้านี้เป็นของ "เว็บไหน" (CONTRACT §E) — ใช้กับทุกแถวรายการเงิน
 *  - รู้เว็บ       → ไอคอนโลก + ชื่อเว็บ (1 บรรทัด)
 *  - ชนหลายเว็บ   → ไอคอนเตือนสีส้มแดง + "ยอดนี้ตรงกับหลายเว็บ: A, B — กรุณาตรวจสอบ" (สูงสุด 2 บรรทัด)
 *  - ยังไม่รู้เว็บ → ไม่แสดงอะไร (แถวหน้าตาเหมือนเดิม)
 */
@Composable
fun SiteAttributionChip(
    transaction: BankTransaction,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    if (transaction.matchConflict) {
        val sites = transaction.conflictSiteList()
        val text = buildString {
            append(strings.siteConflictLabel)
            if (sites.isNotEmpty()) append(": ").append(sites.joinToString(", "))
            append(" — ").append(strings.siteConflictCheck)
        }
        SiteChip(
            text = text,
            icon = Icons.Default.Warning,
            brand = SiteWarning,
            maxLines = 2,
            corner = 10.dp,
            contentDescription = strings.siteConflictLabel,
            modifier = modifier
        )
        return
    }
    val site = transaction.attributedSiteName() ?: return
    SiteChip(
        text = site,
        icon = Icons.Default.Language,
        brand = SiteTeal,
        maxLines = 1,
        corner = 50.dp,
        contentDescription = strings.siteChipDescription,
        modifier = modifier
    )
}

@Composable
private fun SiteChip(
    text: String,
    icon: ImageVector,
    brand: Color,
    maxLines: Int,
    corner: Dp,
    contentDescription: String,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(corner)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(shape)
            .background(brand.copy(alpha = 0.10f))
            .border(1.dp, brand.copy(alpha = 0.35f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(12.dp),
            tint = brand
        )
        Text(
            text,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = brand,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
