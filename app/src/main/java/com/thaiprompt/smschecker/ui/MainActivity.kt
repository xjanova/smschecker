package com.thaiprompt.smschecker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thaiprompt.smschecker.service.OrderSyncWorker
import com.thaiprompt.smschecker.service.SmsProcessingService
import com.thaiprompt.smschecker.ui.dashboard.DashboardScreen
import com.thaiprompt.smschecker.ui.dashboard.DashboardViewModel
import com.thaiprompt.smschecker.ui.orders.OrdersScreen
import com.thaiprompt.smschecker.ui.settings.SettingsScreen
import com.thaiprompt.smschecker.ui.theme.AppColors
import com.thaiprompt.smschecker.ui.theme.SmsCheckerTheme
import com.thaiprompt.smschecker.ui.transactions.TransactionListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
        OrderSyncWorker.enqueuePeriodicSync(applicationContext)

        setContent {
            SmsCheckerTheme {
                MainApp()
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
    data object Orders : Screen("orders", "Orders")
    data object Transactions : Screen("transactions", "Transactions")
    data object Settings : Screen("settings", "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = listOf(Screen.Dashboard, Screen.Orders, Screen.Transactions, Screen.Settings)

    // Observe pending count for badge on Orders tab
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val pendingCount = dashboardState.pendingApprovalCount

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0A0E14),
                tonalElevation = 0.dp
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            val icon = when (screen) {
                                is Screen.Dashboard -> Icons.Default.Dashboard
                                is Screen.Orders -> Icons.Default.Assignment
                                is Screen.Transactions -> Icons.Default.History
                                is Screen.Settings -> Icons.Default.Settings
                            }
                            if (screen is Screen.Orders && pendingCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = AppColors.WarningOrange,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                "$pendingCount",
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = screen.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                screen.title,
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
                            selectedIconColor = AppColors.GoldAccent,
                            selectedTextColor = AppColors.GoldAccent,
                            unselectedIconColor = Color(0xFF8B949E),
                            unselectedTextColor = Color(0xFF8B949E),
                            indicatorColor = AppColors.GoldAccent.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route
            ) {
                composable(Screen.Dashboard.route) { DashboardScreen() }
                composable(Screen.Orders.route) { OrdersScreen() }
                composable(Screen.Transactions.route) { TransactionListScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}
