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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiprompt.smschecker.ui.components.AeroGlass
import com.thaiprompt.smschecker.ui.components.aeroBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.service.OrderSyncWorker
import com.thaiprompt.smschecker.service.RealtimeSyncService
import com.thaiprompt.smschecker.service.ServiceWatchdogWorker
import com.thaiprompt.smschecker.service.SmsProcessingService
import com.thaiprompt.smschecker.ui.dashboard.DashboardScreen
import com.thaiprompt.smschecker.ui.dashboard.DashboardViewModel
import com.thaiprompt.smschecker.ui.health.SystemHealthScreen
import com.thaiprompt.smschecker.ui.orders.OrdersScreen
import com.thaiprompt.smschecker.ui.qrscanner.QrScannerScreen
import com.thaiprompt.smschecker.ui.settings.SettingsScreen
import com.thaiprompt.smschecker.ui.smshistory.SmsHistoryScreen
import com.thaiprompt.smschecker.ui.splash.IntroSplashScreen
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

    companion object {
        /**
         * 🎬 คลิปเปิดแอพ "ครั้งเดียวต่อการเปิดแอพจริง" ไม่ใช่ครั้งเดียวต่อ Activity
         *
         * ตัวแปร static อยู่ยาวเท่าอายุ **โปรเซส** → ใช้แยกได้ตรง ๆ ว่า
         *   - โปรเซสยังอยู่ (แอพทำงานเบื้องหลังอยู่) → เปิดมาเข้าแอพเลย ไม่ต้องดูคลิปซ้ำ
         *   - โปรเซสตายไปแล้ว (ปัดออกจาก recents / ระบบเก็บแรม) → เปิดใหม่จริง ค่อยเล่นคลิป
         *
         * ⚠️ ห้ามใช้ `rememberSaveable` ตัวเดียวตัดสิน — มันรอดแค่ตอนหมุนจอ/เปลี่ยนธีม
         *    Activity ถูกทำลายแล้วสร้างใหม่ทั้งที่โปรเซสยังอยู่ (กดย้อนกลับบน Android < 12,
         *    ระบบเก็บ Activity คืนแรม) จะได้ savedInstanceState = null → เด้งกลับเป็น true
         *    = ร้านเห็นคลิป 10 วิ + รอซิงค์ใหม่ทุกครั้งที่กดเข้าแอพ ซึ่งคืออาการที่เจ้าของเจอ
         */
        @Volatile private var introShownThisProcess = false

        /** นาฬิกาที่ไม่ขยับตามการตั้งเวลาเครื่อง — ใช้กันซิงค์รัวตอนสลับเข้าออกแอพถี่ ๆ */
        @Volatile private var lastStartupSyncAt = 0L
        private const val STARTUP_SYNC_MIN_GAP_MS = 20_000L
    }

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
        ServiceWatchdogWorker.enqueuePeriodic(applicationContext)
        RealtimeSyncService.start(applicationContext)

        // 🎬 ตัดสินใจ "รอบนี้เล่นคลิปไหม" ครั้งเดียวตอน onCreate แล้วปักธงทันที
        //    ปักตั้งแต่ตอนตัดสินใจ (ไม่ใช่ตอนคลิปจบ) เพราะถ้าผู้ใช้กดออกกลางคลิปแล้วเข้าใหม่
        //    ทั้งที่โปรเซสยังอยู่ ก็ไม่ควรโดนคลิปซ้ำอีกรอบ
        val playIntroThisLaunch = !introShownThisProcess
        introShownThisProcess = true

        setContent {
            // rememberSaveable เพิ่มอีกชั้นกันเล่นซ้ำตอนหมุนจอ/เปลี่ยนธีม
            // ค่าเริ่มต้นมาจาก playIntroThisLaunch (ผูกกับอายุโปรเซส) ไม่ใช่ค่าคงที่ true
            var showIntro by rememberSaveable { mutableStateOf(playIntroThisLaunch) }

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

            // ⚠️ อย่าเดา "ตรวจสิทธิ์เสร็จหรือยัง" จาก status != CHECKING
            //    LicenseState() ตั้งค่าเริ่มต้นเป็น TRIAL ไม่ใช่ CHECKING → ก่อน initialize() จะทำงาน
            //    มันจะอ่านได้ว่า "เสร็จแล้ว" ทั้งที่ยังไม่เริ่มตรวจ (เห็นข้อความสลับไปมาบนจอจริง)
            //    จับตอน initialize() คืนค่าจริงแทน แม่นกว่าและไม่ผูกกับค่าเริ่มต้นของ enum
            var licenseSettled by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                LicenseManager.initialize(context)
                licenseSettled = true
                UpdateChecker.initAutoUpdatePref(context)
                UpdateChecker.checkForUpdate(context, shouldThrottle = true)
            }

            val licenseState by LicenseManager.state.collectAsState()
            val updateInfo by UpdateChecker.updateInfo.collectAsState()
            val updateScope = rememberCoroutineScope()

            // 🔄 สถานะ "โหลดเสร็จหรือยัง" ที่คลิปเปิดแอพใช้เป็นเงื่อนไขจบ
            //    จบ = ซิงค์รอบเปิดแอพเสร็จ (สำเร็จ/ล้มเหลว/ยกเลิก ก็นับ) + รู้ผลสิทธิ์ใช้งานแล้ว
            //    ห้ามรอ "สำเร็จ" อย่างเดียว — เน็ตร้านล่มแล้วจะขังผู้ใช้ไว้ทั้งที่ดูบิลเก่าได้
            val syncSettled by remember(context) {
                OrderSyncWorker.oneTimeSyncSettled(context.applicationContext)
            }.collectAsState(initial = false)

            // ⏱ เพดานเวลารอ — ร้านที่เน็ตล่มต้องไม่รอนานกว่าร้านที่เน็ตดี
            //    งานซิงค์ตั้งเงื่อนไข "ต้องมีเน็ต" ไว้ ถ้าออฟไลน์มันจะค้างสถานะรอคิวตลอดกาล
            //    ไม่ใช่ล้มเหลว → ถ้าไม่ตั้งเพดานจะกลายเป็นขังคนออฟไลน์ไว้จนครบ HARD_CAP
            //    ตั้ง 8 วิ (สั้นกว่าคลิป 10 วิ) = ออฟไลน์ก็ยังจบพร้อมคลิปพอดี ไม่รู้สึกว่านานกว่า
            //    เปิดแอพแบบอุ่น (ไม่มีคลิป) ไม่ต้องนับเลย ไม่มีใครรอผลอยู่
            var syncGraceOver by remember { mutableStateOf(false) }
            LaunchedEffect(showIntro) {
                if (!showIntro) return@LaunchedEffect
                kotlinx.coroutines.delay(8_000)
                syncGraceOver = true
            }

            val startupReady = licenseSettled && (syncSettled || syncGraceOver)
            val startupStatus = when {
                startupReady -> appStrings.splashReady
                !licenseSettled -> appStrings.splashCheckingLicense
                else -> appStrings.splashSyncing
            }

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
                            // ปุ่ม "ภายหลัง" — เลื่อน 24 ชม.
                            onSnooze = { UpdateChecker.snoozeVersion(context, updateInfo.latestVersion) },
                            // แตะนอกกล่อง / ปัดกลับ — ซ่อนแค่รอบนี้ ไม่จำลง prefs
                            onHide = { UpdateChecker.hideUpdateDialogForNow() }
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
                                // ⬅️ กดย้อนกลับที่หน้าแรก = ย่อลงพื้นหลัง ไม่ใช่ทำลาย Activity
                                //    แอพนี้เฝ้า SMS อยู่เบื้องหลังตลอดอยู่แล้ว (foreground service)
                                //    การทำลายจอทิ้งไม่ได้ประหยัดอะไร แต่ทำให้กดเข้ามาใหม่ต้องสร้างใหม่หมด
                                //    Android 12+ ทำแบบนี้ให้เองอยู่แล้ว — ใส่เองเพื่อให้ 8/9/10/11 เหมือนกัน
                                onMinimize = { moveTaskToBack(true) },
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

                    // 🎬 คลิปเปิดแอพวางทับทุกอย่าง (รวมหน้า license) แล้วค่อยจางออก
                    //    เจ้าของสั่งให้คลิปทำหน้าที่เป็น "หน้าโหลด" ไปด้วย — ระหว่างเล่น 10 วินาที
                    //    แอพซิงค์ข้อมูลจากเซิร์ฟเวอร์ + ตรวจสิทธิ์ใช้งานไปพร้อมกัน
                    //    พอคลิปจบข้อมูลก็สดแล้ว ไม่ต้องมาเจอสปินเนอร์ในแอพอีกรอบ
                    // ⚠️ AnimatedVisibility ต้องอยู่นอก if(showIntro) ไม่งั้นพอ showIntro=false
                    //    ตัวมันถูกถอดออกจาก composition ทันที อนิเมชันจางออกไม่ได้เล่นเลย
                    AnimatedVisibility(
                        visible = showIntro,
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(320))
                    ) {
                        IntroSplashScreen(
                            isReady = startupReady,
                            statusText = startupStatus,
                            onFinished = { showIntro = false }
                        )
                    }
                }
            }
        }
    }

    /**
     * 🔄 ดึงข้อมูลสด "ทุกครั้งที่หน้าจอกลับมาอยู่ข้างหน้า" ไม่ใช่แค่ตอนสร้าง Activity
     *
     * ของเดิมยิงใน `onCreate` — พอเราหยุดสร้าง Activity ใหม่ทุกครั้งที่กดเข้าแอพ (ซึ่งคือจุดประสงค์)
     * `onCreate` จะไม่ถูกเรียกอีกเลยตราบใดที่โปรเซสยังอยู่ → ร้านที่เปิดแอพค้างไว้ทั้งวัน
     * จะไม่มีอะไรมาปลุกซิงค์เลย นอกจาก worker รอบ 15 นาที **ย้ายมา onStart จึงจำเป็น ไม่ใช่ของแถม**
     *
     * ⚠️ ต้องมีเพดานถี่ — `enqueueOneTimeSync` ใช้ `ExistingWorkPolicy.REPLACE` แปลว่า
     *    การยิงรอบใหม่ **ยกเลิกงานที่กำลังวิ่งอยู่ทิ้ง** ถ้าผู้ใช้สลับเข้าออกแอพรัว ๆ
     *    (เปิดดูบิล → สลับไปแชท → กลับมา) จะยกเลิกซ้ำจนไม่มีรอบไหนวิ่งจบสักที = ข้อมูลไม่อัพเดท
     */
    override fun onStart() {
        super.onStart()

        // เทียบด้วย `== 0L` แยกกรณี "ยังไม่เคยยิงในโปรเซสนี้" ออกมาก่อน — elapsedRealtime()
        // นับจากตอนบูตเครื่อง ถ้าเพิ่งบูตมาไม่ถึง 20 วิ ส่วนต่างจาก 0 จะไม่ถึงเพดาน
        // แล้วรอบเปิดแอพครั้งแรกจะโดนข้ามทิ้งทั้งที่ยังไม่เคยซิงค์เลย
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastStartupSyncAt == 0L || now - lastStartupSyncAt >= STARTUP_SYNC_MIN_GAP_MS) {
            lastStartupSyncAt = now
            OrderSyncWorker.enqueueOneTimeSync(applicationContext)
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

    /**
     * Every time the user returns to the app, ensure background components are alive.
     * This is a secondary safety net in addition to BootReceiver + ServiceWatchdogWorker.
     */
    override fun onResume() {
        super.onResume()
        try {
            RealtimeSyncService.start(applicationContext)
            ServiceWatchdogWorker.enqueuePeriodic(applicationContext)
        } catch (e: Exception) {
            Log.e("MainActivity", "onResume: failed to ensure services alive", e)
        }
    }

    /**
     * Public helper so Settings screen can re-prompt when user taps the banner.
     */
    fun reRequestBatteryOptimizationExemption() {
        requestBatteryOptimizationExemption()
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
    data object SystemHealth : Screen("system_health")
    data object RevenueDetail : Screen("revenue_detail")
}

@Composable
fun MainApp(
    onMinimize: () -> Unit = {},
    onThemeChanged: (ThemeMode) -> Unit = {},
    onLanguageChanged: (LanguageMode) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val strings = LocalAppStrings.current

    // ⬅️ ย้อนกลับจากหน้าแรก (ไม่มีอะไรให้ pop แล้ว) → ย่อลงพื้นหลัง เก็บจอไว้ทั้งดุ้น
    //    กลับเข้ามาอีกทีจึงได้แท็บเดิม ตำแหน่งเลื่อนเดิม ไม่ต้องโหลดใหม่
    //
    // ⚠️ ตัดสินจาก `previousBackStackEntry == null` ไม่ใช่ `currentRoute == Dashboard`
    //    แท็บล่างใช้ `popUpTo(Dashboard) { saveState = true }` → ยืนอยู่แท็บอื่นก็ยังมี
    //    Dashboard ค้างใน stack ให้ pop ได้ ถ้าเช็คแค่ route เราจะไปแย่งจังหวะ pop ของ NavHost
    //    แล้วปุ่มย้อนกลับจะ "ย่อแอพ" ตั้งแต่ยังกลับหน้าแรกไม่ได้
    //
    //    อ่าน navBackStackEntry (เป็น State) ก่อนในบรรทัดบน ตัวนี้จึงคำนวณใหม่ทุกครั้งที่ย้ายหน้า
    val atRootDestination = navBackStackEntry != null && navController.previousBackStackEntry == null
    BackHandler(enabled = atRootDestination) { onMinimize() }

    val bottomScreens = listOf(Screen.Dashboard, Screen.Orders, Screen.Transactions, Screen.SmsHistory, Screen.Settings)

    // Observe pending count for badge on Orders tab
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val pendingCount = dashboardState.pendingApprovalCount

    // Hide bottom bar on full-screen routes
    val showBottomBar = currentRoute != Screen.QrScanner.route &&
        currentRoute != Screen.SmsMatcher.route &&
        currentRoute != Screen.SystemHealth.route &&
        currentRoute != Screen.RevenueDetail.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    AeroGlass(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 26.dp,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                val icon = when (screen) {
                                    is Screen.Dashboard -> Icons.Default.GridView
                                    is Screen.Orders -> Icons.Default.ListAlt
                                    is Screen.Transactions -> Icons.Default.SwapVert
                                    is Screen.SmsHistory -> Icons.Default.ChatBubbleOutline
                                    is Screen.Settings -> Icons.Default.Tune
                                    else -> Icons.Default.GridView
                                }
                                AeroTab(
                                    selected = selected,
                                    icon = icon,
                                    label = title,
                                    badgeCount = if (screen is Screen.Orders) pendingCount else 0,
                                    modifier = Modifier.weight(1f),
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
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(aeroBackgroundBrush())
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onChartTap = { navController.navigate(Screen.RevenueDetail.route) }
                    )
                }
                composable(Screen.RevenueDetail.route) {
                    com.thaiprompt.smschecker.ui.dashboard.RevenueDetailScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
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
                        onNavigateToHealth = {
                            navController.navigate(Screen.SystemHealth.route)
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
                composable(Screen.SystemHealth.route) {
                    SystemHealthScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/**
 * A single tab in the Millennium 3D glass tab bar. The active tab's icon sits
 * inside a money-green gloss pill; its label takes the primary colour.
 */
@Composable
private fun AeroTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 32.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (selected) Modifier.background(
                            Brush.verticalGradient(listOf(AeroPalette.GreenHi, AeroPalette.GreenLo))
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-3).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AeroPalette.Red),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$badgeCount", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
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
    onSnooze: () -> Unit,
    onHide: () -> Unit
) {
    AlertDialog(
        // 🔧 (2026-07-27) ปัดกลับ/แตะนอกกล่อง = ซ่อนชั่วคราวเท่านั้น
        // ของเดิมยิง dismissVersion() ที่จำถาวร → ปัดพลาดครั้งเดียวไม่เห็นอัพเดทอีกเลย
        onDismissRequest = { if (!updateInfo.isDownloading) onHide() },
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
                TextButton(onClick = onSnooze) {
                    Text("ภายหลัง")
                }
            }
        }
    )
}
