package com.thaiprompt.smschecker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.thaiprompt.smschecker.data.model.ApprovalMode
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.SectionTitle
import com.thaiprompt.smschecker.ui.components.premiumBackground
import com.thaiprompt.smschecker.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToQrScanner: () -> Unit = {},
    qrServerName: String? = null,
    qrServerUrl: String? = null,
    qrApiKey: String? = null,
    qrSecretKey: String? = null,
    onQrResultConsumed: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Handle QR scan result
    LaunchedEffect(qrServerName, qrServerUrl, qrApiKey, qrSecretKey) {
        if (qrServerName != null && qrServerUrl != null && qrApiKey != null && qrSecretKey != null) {
            viewModel.addServer(
                name = qrServerName,
                url = qrServerUrl,
                apiKey = qrApiKey,
                secretKey = qrSecretKey,
                isDefault = true
            )
            onQrResultConsumed()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Gradient Header
        item {
            GradientHeader {
                Text(
                    "\u0E15\u0E31\u0E49\u0E07\u0E04\u0E48\u0E32",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "\u0E01\u0E32\u0E23\u0E01\u0E33\u0E2B\u0E19\u0E14\u0E04\u0E48\u0E32\u0E41\u0E25\u0E30\u0E01\u0E32\u0E23\u0E15\u0E31\u0E49\u0E07\u0E04\u0E48\u0E32",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.GoldAccent
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Device Info
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.GoldAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = AppColors.GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E2D\u0E38\u0E1B\u0E01\u0E23\u0E13\u0E4C",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GoldAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "ID: ${state.deviceId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Monitoring Toggle
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isMonitoring) AppColors.CreditGreen.copy(alpha = 0.15f)
                                    else AppColors.DebitRed.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MonitorHeart,
                                contentDescription = null,
                                tint = if (state.isMonitoring) AppColors.CreditGreen else AppColors.DebitRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "\u0E15\u0E34\u0E14\u0E15\u0E32\u0E21 SMS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                if (state.isMonitoring) "\u0E17\u0E33\u0E07\u0E32\u0E19\u0E2D\u0E22\u0E39\u0E48 - \u0E23\u0E2D\u0E23\u0E31\u0E1A SMS \u0E18\u0E19\u0E32\u0E04\u0E32\u0E23"
                                else "\u0E2B\u0E22\u0E38\u0E14\u0E0A\u0E31\u0E48\u0E27\u0E04\u0E23\u0E32\u0E27",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = state.isMonitoring,
                        onCheckedChange = { viewModel.toggleMonitoring() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.CreditGreen,
                            checkedTrackColor = AppColors.CreditGreen.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Approval Mode
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\u0E42\u0E2B\u0E21\u0E14\u0E2D\u0E19\u0E38\u0E21\u0E31\u0E15\u0E34",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.GoldAccent
                    )
                    if (state.offlineQueueCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.WarningOrange.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.WarningOrange)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${state.offlineQueueCount} \u0E23\u0E2D\u0E04\u0E34\u0E27",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.WarningOrange,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ApprovalMode.entries.forEach { mode ->
                    val isSelected = state.approvalMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) AppColors.GoldAccent.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    1.dp,
                                    AppColors.GoldAccent.copy(alpha = 0.3f),
                                    RoundedCornerShape(10.dp)
                                )
                                else Modifier
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setApprovalMode(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AppColors.GoldAccent
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                mode.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppColors.GoldAccent
                                    else Color.White
                            )
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Server Connections Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("\u0E40\u0E0A\u0E37\u0E48\u0E2D\u0E21\u0E15\u0E48\u0E2D\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C", showDivider = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // QR Code Scanner button
                    FilledTonalButton(
                        onClick = onNavigateToQrScanner,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                            contentColor = AppColors.GoldAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("\u0E2A\u0E41\u0E01\u0E19 QR", fontSize = 12.sp)
                    }
                    // Manual add button
                    FilledTonalButton(
                        onClick = { viewModel.showAddServerDialog() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                            contentColor = AppColors.GoldAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("\u0E40\u0E1E\u0E34\u0E48\u0E21\u0E40\u0E2D\u0E07", fontSize = 12.sp)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Server List
        if (state.servers.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "\u0E22\u0E31\u0E07\u0E44\u0E21\u0E48\u0E21\u0E35\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        Text(
                            "\u0E40\u0E1E\u0E34\u0E48\u0E21\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C\u0E40\u0E1E\u0E37\u0E48\u0E2D\u0E0B\u0E34\u0E07\u0E04\u0E4C\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E01\u0E32\u0E23\u0E0A\u0E33\u0E23\u0E30\u0E40\u0E07\u0E34\u0E19",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Quick QR scan button in empty state
                        Button(
                            onClick = onNavigateToQrScanner,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.GoldAccent,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("\u0E2A\u0E41\u0E01\u0E19 QR Code \u0E40\u0E1E\u0E34\u0E48\u0E21\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C")
                        }
                    }
                }
            }
        }

        items(state.servers) { server ->
            ServerCard(
                server = server,
                onToggle = { viewModel.toggleServerActive(server) },
                onDelete = { viewModel.deleteServer(server) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Supported Banks Info
        item {
            SectionTitle(
                "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E17\u0E35\u0E48\u0E23\u0E2D\u0E07\u0E23\u0E31\u0E1A",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                val banks = listOf(
                    "KBANK" to "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E01\u0E2A\u0E34\u0E01\u0E23\u0E44\u0E17\u0E22",
                    "SCB" to "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E44\u0E17\u0E22\u0E1E\u0E32\u0E13\u0E34\u0E0A\u0E22\u0E4C",
                    "KTB" to "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E01\u0E23\u0E38\u0E07\u0E44\u0E17\u0E22",
                    "BBL" to "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E01\u0E23\u0E38\u0E07\u0E40\u0E17\u0E1E",
                    "GSB" to "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E2D\u0E2D\u0E21\u0E2A\u0E34\u0E19",
                    "BAY" to "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E01\u0E23\u0E38\u0E07\u0E28\u0E23\u0E35\u0E2D\u0E22\u0E38\u0E18\u0E22\u0E32",
                    "TTB" to "\u0E18\u0E19\u0E32\u0E04\u0E32\u0E23\u0E17\u0E35\u0E17\u0E35\u0E1A\u0E35",
                    "PromptPay" to "\u0E1E\u0E23\u0E49\u0E2D\u0E21\u0E40\u0E1E\u0E22\u0E4C"
                )
                banks.forEachIndexed { index, (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.thaiprompt.smschecker.ui.components.BankLogoCircle(
                                bankCode = code,
                                size = 28.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                code,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < banks.lastIndex) {
                        Divider(
                            color = AppColors.GlassCardBorder,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }

        // Version info
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "SMS Payment Checker v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Add Server Dialog
    if (state.showAddDialog) {
        AddServerDialog(
            onDismiss = { viewModel.hideAddServerDialog() },
            onConfirm = { name, url, apiKey, secretKey, isDefault ->
                viewModel.addServer(name, url, apiKey, secretKey, isDefault)
            }
        )
    }
}

@Composable
fun ServerCard(
    server: ServerConfig,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.GlassCardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (server.isActive) AppColors.CreditGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = if (server.isActive) AppColors.CreditGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                server.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (server.isDefault) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AppColors.GoldAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "\u0E2B\u0E25\u0E31\u0E01",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.GoldAccent
                                    )
                                }
                            }
                        }
                        Text(
                            server.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = server.isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.CreditGreen,
                        checkedTrackColor = AppColors.CreditGreen.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = AppColors.GlassCardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (server.lastSyncAt != null) {
                        Text(
                            "\u0E0B\u0E34\u0E07\u0E04\u0E4C\u0E25\u0E48\u0E32\u0E2A\u0E38\u0E14: ${dateFormat.format(Date(server.lastSyncAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "\u0E22\u0E31\u0E07\u0E44\u0E21\u0E48\u0E40\u0E04\u0E22\u0E0B\u0E34\u0E07\u0E04\u0E4C",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (server.lastSyncStatus != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (server.lastSyncStatus) {
                                            "success" -> AppColors.CreditGreen
                                            "failed" -> AppColors.DebitRed
                                            else -> AppColors.WarningOrange
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when (server.lastSyncStatus) {
                                    "success" -> "\u0E2A\u0E33\u0E40\u0E23\u0E47\u0E08"
                                    "failed" -> "\u0E25\u0E49\u0E21\u0E40\u0E2B\u0E25\u0E27"
                                    else -> server.lastSyncStatus
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = when (server.lastSyncStatus) {
                                    "success" -> AppColors.CreditGreen
                                    "failed" -> AppColors.DebitRed
                                    else -> AppColors.WarningOrange
                                }
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.DebitRed.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("\u0E25\u0E1A", fontSize = 11.sp)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("\u0E25\u0E1A\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C") },
            text = { Text("\u0E15\u0E49\u0E2D\u0E07\u0E01\u0E32\u0E23\u0E25\u0E1A \"${server.name}\" \u0E2D\u0E2D\u0E01\u0E08\u0E32\u0E01\u0E01\u0E32\u0E23\u0E40\u0E0A\u0E37\u0E48\u0E2D\u0E21\u0E15\u0E48\u0E2D\u0E2B\u0E23\u0E37\u0E2D\u0E44\u0E21\u0E48? \u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E1A\u0E19\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C\u0E08\u0E30\u0E44\u0E21\u0E48\u0E16\u0E39\u0E01\u0E25\u0E1A") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("\u0E25\u0E1A", color = AppColors.DebitRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("\u0E22\u0E01\u0E40\u0E25\u0E34\u0E01")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, apiKey: String, secretKey: String, isDefault: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var apiKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showSecretKey by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gold accent line at top
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(AppColors.GoldAccent, AppColors.GoldDark)
                            )
                        )
                )

                Text(
                    "\u0E40\u0E1E\u0E34\u0E48\u0E21\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GoldAccent
                )

                Text(
                    "\u0E40\u0E0A\u0E37\u0E48\u0E2D\u0E21\u0E15\u0E48\u0E2D\u0E01\u0E31\u0E1A Laravel \u0E40\u0E1E\u0E37\u0E48\u0E2D\u0E0B\u0E34\u0E07\u0E04\u0E4C\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E01\u0E32\u0E23\u0E0A\u0E33\u0E23\u0E30\u0E40\u0E07\u0E34\u0E19",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("\u0E0A\u0E37\u0E48\u0E2D\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C") },
                    placeholder = { Text("\u0E40\u0E0A\u0E48\u0E19 Thaiprompt Main") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL \u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C") },
                    placeholder = { Text("https://your-domain.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = { Text("Secret Key (\u0E2A\u0E33\u0E2B\u0E23\u0E31\u0E1A\u0E40\u0E02\u0E49\u0E32\u0E23\u0E2B\u0E31\u0E2A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showSecretKey) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showSecretKey = !showSecretKey }) {
                            Icon(
                                if (showSecretKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "\u0E15\u0E31\u0E49\u0E07\u0E40\u0E1B\u0E47\u0E19\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C\u0E2B\u0E25\u0E31\u0E01",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("\u0E22\u0E01\u0E40\u0E25\u0E34\u0E01")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name, url, apiKey, secretKey, isDefault) },
                        enabled = name.isNotBlank() && url.length > 8 && apiKey.isNotBlank() && secretKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GoldAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("\u0E1A\u0E31\u0E19\u0E17\u0E36\u0E01")
                    }
                }
            }
        }
    }
}
