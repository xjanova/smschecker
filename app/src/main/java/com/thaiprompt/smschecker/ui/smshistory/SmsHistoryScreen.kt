@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui.smshistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings

/**
 * SMS History screen with manual scan button and improved status display.
 */
@Composable
fun SmsHistoryScreen(
    viewModel: SmsHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header
        item(key = "header") {
            GradientHeader {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            strings.smsMatcherTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            strings.smsMatcherSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF66BB6A)
                        )
                    }
                }
            }
        }

        item(key = "spacer_top") { Spacer(modifier = Modifier.height(12.dp)) }

        // Stats Summary
        item(key = "stats") {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        icon = Icons.Default.Email,
                        value = "${state.totalDetected}",
                        label = strings.totalDetected,
                        color = AppColors.InfoBlue
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    StatItem(
                        icon = Icons.Default.CloudDone,
                        value = "${state.totalSynced}",
                        label = strings.totalSynced,
                        color = AppColors.CreditGreen
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    StatItem(
                        icon = Icons.Default.Pending,
                        value = "${maxOf(0, state.totalDetected - state.totalSynced)}",
                        label = strings.pendingLabel,
                        color = AppColors.WarningOrange
                    )
                }
            }
        }

        item(key = "spacer_mid") { Spacer(modifier = Modifier.height(16.dp)) }

        // Scanning status
        if (state.isScanning) {
            item(key = "scanning") {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = AppColors.InfoBlue,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                strings.scanning,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.scanTotal > 0) {
                                Text(
                                    "${state.scanProgress} / ${state.scanTotal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (state.scanFoundCount > 0) {
                                Text(
                                    "${strings.foundMessages}: ${state.scanFoundCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.CreditGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            item(key = "spacer_scan") { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Scan complete
        if (state.scanComplete && !state.isScanning) {
            item(key = "scan_done") {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.CreditGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "${strings.scanComplete} — ${strings.foundMessages}: ${state.scanFoundCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.CreditGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            item(key = "spacer_done") { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Scan error
        val scanErr = state.scanError
        if (scanErr != null && !state.isScanning) {
            item(key = "scan_error") {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = AppColors.DebitRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            scanErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.DebitRed,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item(key = "spacer_err") { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Scan button + empty state
        item(key = "action") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        strings.noTransactionsYet,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Manual scan button
                    Button(
                        onClick = { viewModel.startInboxScan() },
                        enabled = !state.isScanning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GoldAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            strings.scanInbox,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        strings.scanInboxHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Bottom spacer for nav bar
        item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}
