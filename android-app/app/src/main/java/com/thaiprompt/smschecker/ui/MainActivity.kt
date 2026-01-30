package com.thaiprompt.smschecker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.service.SmsProcessingService
import com.thaiprompt.smschecker.ui.dashboard.DashboardScreen
import com.thaiprompt.smschecker.ui.qrscanner.QrScannerScreen
import com.thaiprompt.smschecker.ui.settings.SettingsScreen
import com.thaiprompt.smschecker.ui.theme.SmsCheckerTheme
import com.thaiprompt.smschecker.ui.transactions.TransactionListScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var secureStorage: SecureStorage

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
        installSplashScreen()
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            val themeMode = remember { mutableStateOf(secureStorage.getThemeMode()) }

            SmsCheckerTheme(themeMode = themeMode.value) {
                MainApp(
                    themeMode = themeMode,
                    onThemeModeChanged = { mode ->
                        themeMode.value = mode
                    }
                )
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

    private fun startSmsService() {
        val intent = Intent(this, SmsProcessingService::class.java).apply {
            action = SmsProcessingService.ACTION_START_MONITORING
        }
        SmsProcessingService.enqueueWork(this, intent)
    }
}

sealed class Screen(val route: String, val title: String) {
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object Transactions : Screen("transactions", "Transactions")
    data object Settings : Screen("settings", "Settings")
    data object QrScanner : Screen("qr_scanner", "Scan QR Code")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    themeMode: MutableState<String> = mutableStateOf("system"),
    onThemeModeChanged: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = listOf(Screen.Dashboard, Screen.Transactions, Screen.Settings)

    // Hide bottom bar on QR scanner screen
    val showBottomBar = currentRoute in screens.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        is Screen.Dashboard -> Icons.Default.Dashboard
                                        is Screen.Transactions -> Icons.Default.History
                                        is Screen.Settings -> Icons.Default.Settings
                                        else -> Icons.Default.Dashboard
                                    },
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Transactions.route) { TransactionListScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(Screen.QrScanner.route) {
                QrScannerScreen(
                    onConfigScanned = { result ->
                        // Pass result back to Settings via savedStateHandle
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("qr_server_name", result.deviceName)
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("qr_server_url", result.url)
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("qr_api_key", result.apiKey)
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("qr_secret_key", result.secretKey)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
