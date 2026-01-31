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
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.LanguageMode
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import com.thaiprompt.smschecker.ui.theme.LocalLanguageMode
import com.thaiprompt.smschecker.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToSmsMatcher: () -> Unit = {},
    onThemeChanged: (ThemeMode) -> Unit = {},
    onLanguageChanged: (LanguageMode) -> Unit = {},
    qrServerName: String? = null,
    qrServerUrl: String? = null,
    qrApiKey: String? = null,
    qrSecretKey: String? = null,
    onQrResultConsumed: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current

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
            .background(premiumBackgroundBrush()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Gradient Header
        item {
            GradientHeader {
                Text(
                    strings.settingsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    strings.configAndSettings,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF66BB6A) // Light green accent
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
                            strings.deviceInfo,
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
                                strings.monitorSms,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                if (state.isMonitoring) strings.monitoringActive
                                else strings.monitoringPaused,
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

        // Theme Toggle
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    strings.themeMode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                val themeOptions = listOf(
                    ThemeMode.DARK to strings.themeDark,
                    ThemeMode.LIGHT to strings.themeLight,
                    ThemeMode.SYSTEM to strings.themeSystem
                )
                themeOptions.forEach { (mode, label) ->
                    val isSelected = state.themeMode == mode
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
                            onClick = {
                                viewModel.setThemeMode(mode)
                                onThemeChanged(mode)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.GoldAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AppColors.GoldAccent
                                else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Language Toggle
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    strings.languageTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                val langOptions = listOf(
                    LanguageMode.THAI to strings.languageThai,
                    LanguageMode.ENGLISH to strings.languageEnglish,
                    LanguageMode.SYSTEM to strings.languageSystem
                )
                langOptions.forEach { (mode, label) ->
                    val isSelected = state.languageMode == mode
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
                            onClick = {
                                viewModel.setLanguageMode(mode)
                                onLanguageChanged(mode)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.GoldAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AppColors.GoldAccent
                                else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // TTS Toggle
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
                                    if (state.ttsEnabled) AppColors.GoldAccent.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = if (state.ttsEnabled) AppColors.GoldAccent
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                strings.ttsTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                strings.ttsDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = state.ttsEnabled,
                        onCheckedChange = { viewModel.setTtsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.GoldAccent,
                            checkedTrackColor = AppColors.GoldAccent.copy(alpha = 0.3f)
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
                        strings.approvalModeTitle,
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
                                "${state.offlineQueueCount} ${strings.queueCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.WarningOrange,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val langMode = LocalLanguageMode.current
                val isThai = when (langMode) {
                    LanguageMode.THAI -> true
                    LanguageMode.ENGLISH -> false
                    LanguageMode.SYSTEM -> java.util.Locale.getDefault().language == "th"
                }
                ApprovalMode.entries.forEach { mode ->
                    val isSelected = state.approvalMode == mode
                    val modeName = if (isThai) mode.displayNameTh else mode.displayNameEn
                    val modeDesc = if (isThai) mode.descriptionTh else mode.descriptionEn
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
                                modeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppColors.GoldAccent
                                    else MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                modeDesc,
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
            SectionTitle(
                strings.serverConnections,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        // Server action buttons — full width row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // QR Code Scanner button
                FilledTonalButton(
                    onClick = onNavigateToQrScanner,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                        contentColor = AppColors.GoldAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.scanQr, fontSize = 13.sp)
                }
                // Manual add button
                FilledTonalButton(
                    onClick = { viewModel.showAddServerDialog() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                        contentColor = AppColors.GoldAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.addManual, fontSize = 13.sp)
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
                            strings.noServers,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            strings.addServerToSync,
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
                            Text(strings.scanQrToAddServer)
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
                strings.supportedBanks,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                val strings2 = LocalAppStrings.current
                val banks = listOf(
                    "KBANK" to strings2.bankKbank,
                    "SCB" to strings2.bankScb,
                    "KTB" to strings2.bankKtb,
                    "BBL" to strings2.bankBbl,
                    "GSB" to strings2.bankGsb,
                    "BAY" to strings2.bankBay,
                    "TTB" to strings2.bankTtb,
                    "PromptPay" to strings2.bankPromptPay
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
                                color = MaterialTheme.colorScheme.onBackground
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
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }

        // SMS Matcher
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            strings.customBankMapping,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GoldAccent
                        )
                        Text(
                            strings.customBankMappingDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onNavigateToSmsMatcher,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.GoldAccent.copy(alpha = 0.2f),
                            contentColor = AppColors.GoldAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.smsMatcherTitle, fontSize = 12.sp)
                    }
                }
            }
        }

        // Version info
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                strings.versionInfo,
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
    val strings = LocalAppStrings.current
    val dateFormat = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
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
                                color = MaterialTheme.colorScheme.onBackground
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
                                        strings.primaryBadge,
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
            Divider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (server.lastSyncAt != null) {
                        Text(
                            "${strings.lastSyncLabel}: ${dateFormat.format(Date(server.lastSyncAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            strings.neverSynced,
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
                                    "success" -> strings.successStatus
                                    "failed" -> strings.failedStatus
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
                    Text(strings.removeButton, fontSize = 11.sp)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.removeServerTitle) },
            text = { Text(strings.removeServerMessage) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(strings.removeButton, color = AppColors.DebitRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancelButton)
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
    val strings = LocalAppStrings.current
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
                    strings.addServerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GoldAccent
                )

                Text(
                    strings.addServerSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.serverNameLabel) },
                    placeholder = { Text(strings.serverNamePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(strings.serverUrlLabel) },
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
                    label = { Text(strings.secretKeyLabel) },
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
                        strings.setAsDefault,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancelButton)
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
                        Text(strings.saveButton)
                    }
                }
            }
        }
    }
}
