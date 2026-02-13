package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.thaiprompt.smschecker.data.model.MisclassificationIssueType
import com.thaiprompt.smschecker.ui.theme.AppColors

@Composable
fun MisclassificationReportDialog(
    onDismiss: () -> Unit,
    onConfirm: (MisclassificationIssueType) -> Unit,
    bankName: String,
    currentType: String, // "CREDIT" or "DEBIT"
    currentAmount: String
) {
    var selectedIssue by remember { mutableStateOf<MisclassificationIssueType?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "รายงานปัญหา",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "ช่วยเราปรับปรุงการแยกประเภทข้อความ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "ปิด",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transaction Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "ธนาคาร:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                bankName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "แอพตีความว่า:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (currentType == "CREDIT") "เงินเข้า" else "เงินออก",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (currentType == "CREDIT") AppColors.CreditGreen else AppColors.DebitRed
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "จำนวนเงิน:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "฿$currentAmount",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "ปัญหาที่พบ:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Issue Options
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IssueOption(
                        icon = Icons.Default.SwapHoriz,
                        title = "แยกประเภทผิด",
                        description = if (currentType == "CREDIT") "จริงๆ เป็นเงินออก ไม่ใช่เงินเข้า" else "จริงๆ เป็นเงินเข้า ไม่ใช่เงินออก",
                        issueType = MisclassificationIssueType.WRONG_TRANSACTION_TYPE,
                        selected = selectedIssue == MisclassificationIssueType.WRONG_TRANSACTION_TYPE,
                        onClick = { selectedIssue = MisclassificationIssueType.WRONG_TRANSACTION_TYPE },
                        color = AppColors.WarningOrange
                    )

                    IssueOption(
                        icon = Icons.Default.Money,
                        title = "จำนวนเงินผิด",
                        description = "แยกจำนวนเงินจาก SMS ผิด",
                        issueType = MisclassificationIssueType.WRONG_AMOUNT,
                        selected = selectedIssue == MisclassificationIssueType.WRONG_AMOUNT,
                        onClick = { selectedIssue = MisclassificationIssueType.WRONG_AMOUNT },
                        color = AppColors.InfoBlue
                    )

                    IssueOption(
                        icon = Icons.Default.Block,
                        title = "ไม่ใช่รายการเงิน",
                        description = "ข้อความนี้ไม่ใช่รายการธุรกรรม แต่แอพจับได้",
                        issueType = MisclassificationIssueType.NOT_A_TRANSACTION,
                        selected = selectedIssue == MisclassificationIssueType.NOT_A_TRANSACTION,
                        onClick = { selectedIssue = MisclassificationIssueType.NOT_A_TRANSACTION },
                        color = AppColors.DebitRed
                    )

                    IssueOption(
                        icon = Icons.Default.ErrorOutline,
                        title = "อื่นๆ",
                        description = "ปัญหาอื่นๆ ที่ไม่ตรงกับข้างต้น",
                        issueType = MisclassificationIssueType.OTHER,
                        selected = selectedIssue == MisclassificationIssueType.OTHER,
                        onClick = { selectedIssue = MisclassificationIssueType.OTHER },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = {
                            selectedIssue?.let { onConfirm(it) }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedIssue != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GoldAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ส่งรายงาน")
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueOption(
    icon: ImageVector,
    title: String,
    description: String,
    issueType: MisclassificationIssueType,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, color)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = if (selected) 0.3f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }

            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "เลือกแล้ว",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
