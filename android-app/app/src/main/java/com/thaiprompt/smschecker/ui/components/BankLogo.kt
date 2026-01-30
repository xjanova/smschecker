package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BankVisualInfo(
    val code: String,
    val shortName: String,
    val fullName: String,
    val brandColor: Color,
    val textColor: Color = Color.White
)

object BankVisuals {
    private val banks = mapOf(
        "KBANK" to BankVisualInfo(
            code = "KBANK",
            shortName = "K",
            fullName = "Kasikorn Bank",
            brandColor = Color(0xFF138F2D) // KBank green
        ),
        "SCB" to BankVisualInfo(
            code = "SCB",
            shortName = "SCB",
            fullName = "Siam Commercial Bank",
            brandColor = Color(0xFF4E2A82) // SCB purple
        ),
        "KTB" to BankVisualInfo(
            code = "KTB",
            shortName = "KTB",
            fullName = "Krungthai Bank",
            brandColor = Color(0xFF00A4E4) // KTB blue
        ),
        "BBL" to BankVisualInfo(
            code = "BBL",
            shortName = "BBL",
            fullName = "Bangkok Bank",
            brandColor = Color(0xFF1E22AA) // BBL blue
        ),
        "GSB" to BankVisualInfo(
            code = "GSB",
            shortName = "GSB",
            fullName = "Government Savings Bank",
            brandColor = Color(0xFFEB198D) // GSB pink
        ),
        "BAY" to BankVisualInfo(
            code = "BAY",
            shortName = "KS",
            fullName = "Bank of Ayudhya (Krungsri)",
            brandColor = Color(0xFFFEC43B), // Krungsri yellow
            textColor = Color.Black
        ),
        "TTB" to BankVisualInfo(
            code = "TTB",
            shortName = "ttb",
            fullName = "TMBThanachart Bank",
            brandColor = Color(0xFFFC4F1F) // TTB orange
        ),
        "PROMPTPAY" to BankVisualInfo(
            code = "PROMPTPAY",
            shortName = "PP",
            fullName = "PromptPay",
            brandColor = Color(0xFF1A3C6D) // PromptPay navy
        ),
    )

    fun getVisualInfo(bankCode: String): BankVisualInfo {
        return banks[bankCode.uppercase()]
            ?: banks.entries.firstOrNull { bankCode.uppercase().contains(it.key) }?.value
            ?: BankVisualInfo(
                code = bankCode,
                shortName = bankCode.take(2).uppercase(),
                fullName = bankCode,
                brandColor = Color(0xFF757575) // Default gray
            )
    }

    fun allBanks(): List<BankVisualInfo> = banks.values.toList()
}

@Composable
fun BankLogoCircle(
    bankCode: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val info = BankVisuals.getVisualInfo(bankCode)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(info.brandColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = info.shortName,
            color = info.textColor,
            fontWeight = FontWeight.Bold,
            fontSize = when {
                info.shortName.length <= 1 -> (size.value * 0.45f).sp
                info.shortName.length <= 2 -> (size.value * 0.35f).sp
                else -> (size.value * 0.28f).sp
            }
        )
    }
}
