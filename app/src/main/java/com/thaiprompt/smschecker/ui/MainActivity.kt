@file:OptIn(ExperimentalMaterial3Api::class)

package com.thaiprompt.smschecker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.service.OrderSyncWorker
import com.thaiprompt.smschecker.service.RealtimeSyncService
import com.thaiprompt.smschecker.service.SmsProcessingService
import com.thaiprompt.smschecker.ui.dashboard.DashboardScreen
import com.thaiprompt.smschecker.ui.dashboard.DashboardViewModel
import com.thaiprompt.smschecker.ui.orders.OrdersScreen
import com.thaiprompt.smschecker.ui.qrscanner.QrScannerScreen
import com.thaiprompt.smschecker.ui.settings.SettingsScreen
import com.thaiprompt.smschecker.ui.smshistory.SmsHistoryScreen
import com.thaiprompt.smschecker.ui.smsmatcher.SmsMatcherScreen
import com.thaiprompt.smschecker.ui.theme.*
import com.thaiprompt.smschecker.ui.transactions.TransactionListScreen
import com.thaiprompt.smschecker.data.license.LicenseManager
import com.thaiprompt.smschecker.data.license.LicenseStatus
import com.thaiprompt.smschecker.data.update.UpdateChecker
import com.thaiprompt.smschecker.data.update.UpdateInfo
import com.thaiprompt.smschecker.ui.license.LicenseGateScreen
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var secureStorage: SecureStorage

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startSmsService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        requestBatteryOptimizationExemption()
        OrderSyncWorker.enqueuePeriodicSync(applicationContext)
        RealtimeSyncService.start(applicationContext)

        setContent {
            val themeMode = remember { mutableStateOf(ThemeMode.fromKey(secureStorage.getThemeMode())) }
            val languageMode = remember { mutableStateOf(LanguageMode.fromKey(secureStorage.getLanguage())) }

            val isDarkTheme = when (themeMode.value) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val appStrings = when (languageMode.value) {
                LanguageMode.THAI -> ThaiStrings
                LanguageMode.ENGLISH -> EnglishStrings
                LanguageMode.SYSTEM -> {
                    val locale = java.util.Locale.getDefault().language
                    if (locale == "th") ThaiStrings else EnglishStrings
                }
            }

            // Initialize license system
            val context = this@MainActivity
            LaunchedEffect(Unit) {
                LicenseManager.initialize(context)
                UpdateChecker.initAutoUpdatePref(context)
                UpdateChecker.checkForUpdate(context, shouldThrottle = true)
            }

            val licenseState by LicenseManager.state.collectAsState()
            val updateInfo by UpdateChecker.updateInfo.collectAsState()
            val updateScope = rememberCoroutineScope()

            CompositionLocalProvider(
                LocalAppStrings provides appStrings,
                LocalThemeMode provides themeMode.value,
                LocalLanguageMode provides languageMode.value
            ) {
                SmsCheckerTheme(darkTheme = isDarkTheme) {
                    // Force update dialog — shows ABOVE license gate so user must update first
                    if (updateInfo.hasUpdate) {
                        ForceUpdateDialog(
                            updateInfo = updateInfo,
                            onUpdate = { updateScope.launch { UpdateChecker.downloadAndInstall(context) } },
                            onDismiss = { UpdateChecker.dismissVersion(context, updateInfo.latestVersion) }
                        )
                    }

                    // Show license gate when expired/none, main app when active/trial
                    when (licenseState.status) {
                        LicenseStatus.CHECKING -> {
                            LicenseGateScreen(onLicenseActivated = {})
                        }
                        LicenseStatus.EXPIRED, LicenseStatus.NONE -> {
                            LicenseGateScreen(onLicenseActivated = {})
                        }
                        LicenseStatus.ACTIVE, LicenseStatus.TRIAL -> {
                            MainApp(
                                onThemeChanged = { mode ->
                                    secureStorage.setThemeMode(mode.key)
                                    themeMode.value = mode
                                },
                                onLanguageChanged = { mode ->
                                    secureStorage.setLanguage(mode.key)
                                    languageMode.value = mode
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startSmsService()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    /**
     * Request battery optimization exemption so the app can run reliably in background.
     * This is critical for SMS monitoring while the device is sleeping.
     * Shows the system dialog only if not already exempted.
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.i("MainActivity", "Requesting battery optimization exemption")
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to request battery optimization exemption", e)
                }
            } else {
                Log.d("MainActivity", "Already exempt from battery optimization")
            }
        }
    }

    private fun startSmsService() {
        val intent = Intent(this, SmsProcessingService::class.java).apply {
            action = SmsProcessingService.ACTION_START_MONITORING
        }
        SmsProcessingService.enqueueWork(this, intent)
    }
}

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Orders : Screen("orders")
    data object Transactions : Screen("transactions")
    data object Settings : Screen("settings")
    data object QrScanner : Screen("qr_scanner")
    data object SmsMatcher : Screen("sms_matcher")
    data object SmsHistory : Screen("sms_history")
}

@Composable
fun MainApp(
    onThemeChanged: (ThemeMode) -> Unit = {},
    onLanguageChanged: (LanguageMode) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val strings = LocalAppStrings.current

    val bottomScreens = listOf(Screen.Dashboard, Screen.Orders, Screen.Transactions, Screen.SmsHistory, Screen.Settings)

    // Observe pending count for badge on Orders tab
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val pendingCount = dashboardState.pendingApprovalCount

    // Hide bottom bar on full-screen routes
    val showBottomBar = currentRoute != Screen.QrScanner.route && currentRoute != Screen.SmsMatcher.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomScreens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        val title = when (screen) {
                            is Screen.Dashboard -> strings.navDashboard
                            is Screen.Orders -> strings.navOrders
                            is Screen.Transactions -> strings.navTransactions
                            is Screen.SmsHistory -> strings.navSmsHistory
                            is Screen.Settings -> strings.navSettings
                            else -> ""
                        }
                        NavigationBarItem(
                            icon = {
                                val icon = when (screen) {
                                    is Screen.Dashboard -> Icons.Default.Dashboard
                                    is Screen.Orders -> Icons.Default.Assignment
                                    is Screen.Transactions -> Icons.Default.History
                                    is Screen.SmsHistory -> Icons.Default.Textsms
                                    is Screen.Settings -> Icons.Default.Settings
                                    else -> Icons.Default.Dashboard
                                }
                                if (screen is Screen.Orders && pendingCount > 0) {
                                    Box {
                                        Icon(
                                            icon,
                                            contentDescription = title,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-4).dp)
                                                .size(16.dp)
                                                .background(
                                                    AppColors.WarningOrange,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "$pendingCount",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        icon,
                                        contentDescription = title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    title,
                                    fontSize = 10.sp
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = AppColors.GoldAccent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = AppColors.GoldAccent
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route
            ) {
                composable(Screen.Dashboard.route) { DashboardScreen(viewModel = dashboardViewModel) }
                composable(Screen.Orders.route) { OrdersScreen() }
                composable(Screen.Transactions.route) { TransactionListScreen() }
                composable(Screen.SmsHistory.route) {
                    SmsHistoryScreen()
                }
                composable(Screen.Settings.route) { backStackEntry ->
                    // Observe QR scan results from savedStateHandle
                    val savedStateHandle = backStackEntry.savedStateHandle
                    val qrServerName = savedStateHandle.get<String>("qr_server_name")
                    val qrServerUrl = savedStateHandle.get<String>("qr_server_url")
                    val qrApiKey = savedStateHandle.get<String>("qr_api_key")
                    val qrSecretKey = savedStateHandle.get<String>("qr_secret_key")
                    val qrDeviceId = savedStateHandle.get<String>("qr_device_id")
                    val qrSyncInterval = savedStateHandle.get<Int>("qr_sync_interval") ?: 5

                    SettingsScreen(
                        onNavigateToQrScanner = {
                            navController.navigate(Screen.QrScanner.route)
                        },
                        onNavigateToSmsMatcher = {
                            navController.navigate(Screen.SmsMatcher.route)
                        },
                        onThemeChanged = onThemeChanged,
                        onLanguageChanged = onLanguageChanged,
                        qrServerName = qrServerName,
                        qrServerUrl = qrServerUrl,
                        qrApiKey = qrApiKey,
                        qrSecretKey = qrSecretKey,
                        qrDeviceId = qrDeviceId,
                        qrSyncInterval = qrSyncInterval,
                        onQrResultConsumed = {
                            savedStateHandle.remove<String>("qr_server_name")
                            savedStateHandle.remove<String>("qr_server_url")
                            savedStateHandle.remove<String>("qr_api_key")
                            savedStateHandle.remove<String>("qr_secret_key")
                            savedStateHandle.remove<String>("qr_device_id")
                            savedStateHandle.remove<Int>("qr_sync_interval")
                        }
                    )
                }
                composable(Screen.QrScanner.route) {
                    QrScannerScreen(
                        onConfigScanned = { result ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.apply {
                                    set("qr_server_name", result.deviceName)
                                    set("qr_server_url", result.url)
                                    set("qr_api_key", result.apiKey)
                                    set("qr_secret_key", result.secretKey)
                                    set("qr_sync_interval", result.syncInterval)
                                    if (result.deviceId != null) {
                                        set("qr_device_id", result.deviceId)
                                    }
                                }
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(Screen.SmsMatcher.route) {
                    SmsMatcherScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Force update dialog — shows above everything including license gate.
 * User must update or dismiss before using the app.
 */
@Composable
private fun ForceUpdateDialog(
    updateInfo: UpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!updateInfo.isDownloading) onDismiss() },
        title = {
            Text(
                if (updateInfo.isDownloading) "กำลังอัพเดท..."
                else "อัพเดทใหม่ v${updateInfo.latestVersion}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "v${updateInfo.currentVersion} → v${updateInfo.latestVersion}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (updateInfo.isDownloading) {
                    if (updateInfo.downloadProgress < 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { updateInfo.downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "กำลังดาวน์โหลด ${updateInfo.downloadProgress}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (updateInfo.releaseNotes.isNotEmpty() && !updateInfo.isDownloading) {
                    Text(updateInfo.releaseNotes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (updateInfo.error.isNotEmpty()) {
                    Text(updateInfo.error, fontSize = 11.sp, color = Color(0xFFEF4444))
                }
            }
        },
        confirmButton = {
            if (!updateInfo.isDownloading) {
                Button(onClick = onUpdate) {
                    Text("อัพเดทเลย", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!updateInfo.isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("ภายหลัง")
                }
            }
        }
    )
}
