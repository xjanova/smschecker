package com.thaiprompt.smschecker.ui.qrscanner

import android.Manifest
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.thaiprompt.smschecker.R
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.GlossButton
import com.thaiprompt.smschecker.ui.components.GlossIconButton
import com.thaiprompt.smschecker.ui.components.GlossStyle
import com.thaiprompt.smschecker.ui.components.HeaderTone
import com.thaiprompt.smschecker.ui.components.StatusBarTone
import com.thaiprompt.smschecker.ui.components.darkNavyRadial
import com.thaiprompt.smschecker.ui.theme.AeroPalette
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScanner"

data class QrConfigResult(
    val type: String,
    val version: Int,
    val url: String,
    val apiKey: String,
    val secretKey: String,
    val deviceId: String?,
    val deviceName: String,
    val syncInterval: Int = 300  // Sync interval in seconds (default 5min, FCM push is primary)
)

// Scanner options: QR_CODE format + enableAllPotentialBarcodes for dense/large QR codes
private val scannerOptions = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
    .enableAllPotentialBarcodes()
    .build()

/**
 * QR Device Setup — Millennium 3D / Frutiger Aero (design 05).
 * Dark radial navy screen: glowing logo coin, 230dp camera viewport with green
 * corner brackets + scanning laser, encrypted-connection readout, gloss buttons.
 */
@Composable
fun QrScannerScreen(
    onConfigScanned: (QrConfigResult) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val strings = LocalAppStrings.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scanStatus by remember { mutableStateOf<String?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }
    val hasFoundResult = remember { AtomicBoolean(false) }

    StatusBarTone(HeaderTone.Navy)

    // Background executor for ML Kit — keeps UI thread free
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Create scanner once — reuse across all frames
    val barcodeScanner = remember { BarcodeScanning.getClient(scannerOptions) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            errorMessage = strings.cameraPermissionDenied
        }
    }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.CAMERA
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // Cleanup when composable leaves
    DisposableEffect(Unit) {
        onDispose {
            try {
                barcodeScanner.close()
                analysisExecutor.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "Error during cleanup", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .darkNavyRadial()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // logo coin with green glow
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(14.dp, RoundedCornerShape(30), spotColor = AeroPalette.GreenLo, ambientColor = AeroPalette.GreenLo)
                    .clip(RoundedCornerShape(30))
                    .background(Color.White)
                    .border(1.5.dp, Color(0xE6FFFFFF), RoundedCornerShape(30)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.88f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                strings.aeroConnectDevice,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                strings.aeroConnectDeviceSub,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(18.dp))

            // scanner viewport — camera preview clipped in a 230dp rounded box
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .shadow(16.dp, RoundedCornerShape(30.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF35404F), Color(0xFF232B36))
                        )
                    )
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                Log.d(TAG, "Setting up camera preview")
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        // Use high resolution for dense QR codes with lots of data
                                        val resolutionSelector = ResolutionSelector.Builder()
                                            .setResolutionStrategy(
                                                ResolutionStrategy(
                                                    Size(1920, 1080),
                                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                                )
                                            )
                                            .build()

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setResolutionSelector(resolutionSelector)
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                // Use background executor to avoid blocking UI
                                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                                    processImage(
                                                        imageProxy,
                                                        barcodeScanner,
                                                        isProcessing,
                                                        hasFoundResult
                                                    ) { result ->
                                                        when (result) {
                                                            is ScanResult.ConfigFound -> {
                                                                Log.d(TAG, "Config found, navigating back")
                                                                ContextCompat.getMainExecutor(ctx).execute {
                                                                    onConfigScanned(result.config)
                                                                }
                                                            }
                                                            is ScanResult.WrongFormat -> {
                                                                scanStatus = result.message
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                        Log.d(TAG, "Camera bound successfully")
                                    } catch (e: Exception) {
                                        // Localized message only — never leak the raw exception to UI
                                        errorMessage = strings.cameraFailed
                                        Log.e(TAG, "Camera bind failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // no-permission placeholder inside the dark viewport
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = AeroPalette.GreenHi.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            strings.cameraPermissionRequired,
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                // glowing corner brackets (4 separate L-shapes, NOT a full border)
                ScannerCornerBrackets(modifier = Modifier.matchParentSize())

                // sweeping laser line (respects reduced motion)
                if (hasCameraPermission) {
                    ScannerLaser(modifier = Modifier.matchParentSize())
                }
            }

            // wrong-format / error notices (kept functional, restyled for the dark bg)
            scanStatus?.let { status ->
                Spacer(modifier = Modifier.height(14.dp))
                NoticeCard(text = status, accent = AeroPalette.Gold)
            }
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(14.dp))
                NoticeCard(text = error, accent = AeroPalette.RedHi)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // encrypted-connection readout
            GlassCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AeroPalette.GreenLo, modifier = Modifier.size(16.dp))
                    Text(
                        strings.aeroEncryptedConnection,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AeroPalette.NavyDeep
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                ConfigRow(label = strings.aeroServerField, value = "—", showDivider = true)
                ConfigRow(label = strings.aeroEncryptionField, value = "AES-256-GCM", showDivider = true)
                ConfigRow(label = strings.aeroSignatureField, value = "HMAC-SHA256", showDivider = false)
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlossButton(
                text = strings.aeroStartScanQr,
                onClick = {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        // camera scans continuously; treat as retry — clear stale notices
                        scanStatus = null
                        errorMessage = null
                    }
                },
                style = GlossStyle.Green,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 15.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            GlossButton(
                text = strings.aeroEnterManually,
                onClick = onBack,
                style = GlossStyle.Ghost,
                fontSize = 14,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // back affordance (design has none, navigation requires one)
        GlossIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            style = GlossStyle.Ghost,
            size = 38.dp,
            contentDescription = strings.backButton,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
    }
}

/** Four glowing green L-brackets at the viewport corners (.qcorner). */
@Composable
private fun ScannerCornerBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val inset = 16.dp.toPx()
        val arm = 26.dp.toPx()
        val stroke = 3.dp.toPx()
        val glowStroke = 8.dp.toPx()
        val c = AeroPalette.GreenHi
        val corners = listOf(
            // start point, horizontal arm direction, vertical arm direction
            Triple(Offset(inset, inset), 1f, 1f),                                  // top-left
            Triple(Offset(size.width - inset, inset), -1f, 1f),                    // top-right
            Triple(Offset(inset, size.height - inset), 1f, -1f),                   // bottom-left
            Triple(Offset(size.width - inset, size.height - inset), -1f, -1f),     // bottom-right
        )
        corners.forEach { (p, dx, dy) ->
            // glow pass then solid pass
            listOf(glowStroke to 0.25f, stroke to 1f).forEach { (w, a) ->
                drawLine(
                    color = c.copy(alpha = a),
                    start = p,
                    end = Offset(p.x + arm * dx, p.y),
                    strokeWidth = w,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = c.copy(alpha = a),
                    start = p,
                    end = Offset(p.x, p.y + arm * dy),
                    strokeWidth = w,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** The sweeping green laser (.qscan): 2.6s ease-in-out + alpha pulse + glow halo. */
@Composable
private fun ScannerLaser(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    Box(modifier = modifier.padding(horizontal = 14.dp)) {
        if (reducedMotion) {
            LaserBand(
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(0.6f)
            )
        } else {
            val transition = rememberInfiniteTransition(label = "laser")
            val frac by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "laserY"
            )
            val pulse by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1300, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "laserPulse"
            )
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val travel = maxHeight - 36.dp
                LaserBand(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 18.dp + travel * frac)
                        .alpha(pulse)
                )
            }
        }
    }
}

@Composable
private fun LaserBand(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        AeroPalette.GreenHi.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, AeroPalette.GreenHi, Color.Transparent)
                    )
                )
        )
    }
}

/** One label / mono value / green check row (.cfgrow). */
@Composable
private fun ConfigRow(label: String, value: String, showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.5.sp, color = AeroPalette.InkSoft)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = AeroPalette.NavyDeep
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Default.Check, contentDescription = null, tint = AeroPalette.GreenLo, modifier = Modifier.size(15.dp))
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x99DBE4EC))
        )
    }
}

/** Small translucent notice card on the dark background (status / error). */
@Composable
private fun NoticeCard(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 17.sp)
    }
}

/** Sealed class for scan results */
private sealed class ScanResult {
    data class ConfigFound(val config: QrConfigResult) : ScanResult()
    data class WrongFormat(val message: String) : ScanResult()
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    isProcessing: AtomicBoolean,
    hasFoundResult: AtomicBoolean,
    onResult: (ScanResult) -> Unit
) {
    // Already found a valid config — stop processing
    if (hasFoundResult.get()) {
        imageProxy.close()
        return
    }

    // Skip if already processing a frame
    if (!isProcessing.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        Log.w(TAG, "mediaImage is null")
        isProcessing.set(false)
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                val rawValue = barcode.rawValue
                if (rawValue.isNullOrBlank()) continue

                Log.d(TAG, "Barcode found! format=${barcode.format}, valueType=${barcode.valueType}, length=${rawValue.length}")
                Log.d(TAG, "Raw value: ${rawValue.take(200)}")

                val config = parseQrConfig(rawValue)
                if (config != null) {
                    if (hasFoundResult.compareAndSet(false, true)) {
                        Log.d(TAG, "✓ Valid smschecker_config found: url=${config.url}")
                        onResult(ScanResult.ConfigFound(config))
                    }
                    return@addOnSuccessListener
                } else {
                    // QR code read successfully but it's not our config format
                    Log.d(TAG, "QR code read but not smschecker_config format")
                    onResult(ScanResult.WrongFormat(
                        "QR Code อ่านได้แต่ไม่ใช่ format ของ SMS Checker\n" +
                        "ต้องเป็น QR ที่สร้างจากระบบเซิร์ฟเวอร์เท่านั้น"
                    ))
                }
            }
            isProcessing.set(false)
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "ML Kit scan failed: ${e.message}")
            isProcessing.set(false)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun parseQrConfig(raw: String): QrConfigResult? {
    var json = raw.trim()

    // If not JSON, try base64 decode (servers may encode config as base64 for dense QR)
    if (!json.startsWith("{")) {
        try {
            val decoded = String(
                android.util.Base64.decode(json, android.util.Base64.DEFAULT),
                Charsets.UTF_8
            )
            if (decoded.trimStart().startsWith("{")) {
                Log.d(TAG, "Decoded base64 QR payload (${raw.length} -> ${decoded.length} chars)")
                json = decoded.trim()
            } else {
                Log.d(TAG, "Not JSON after base64 decode (starts with '${decoded.take(1)}')")
                return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not JSON and not valid base64 (starts with '${json.take(1)}')")
            return null
        }
    }

    return try {
        val obj = JSONObject(json)
        val type = obj.optString("type", "")
        if (type != "smschecker_config") {
            Log.d(TAG, "JSON found but type='$type' (expected 'smschecker_config')")
            return null
        }

        val url = obj.optString("url", "")
        val apiKey = obj.optString("apiKey", "")
        val secretKey = obj.optString("secretKey", "")

        if (url.isBlank() || apiKey.isBlank() || secretKey.isBlank()) {
            Log.w(TAG, "Config missing required fields: url=$url, apiKey=${apiKey.take(4)}..., secretKey=${secretKey.take(4)}...")
            return null
        }

        // SECURITY: ตรวจสอบ URL format — ต้องเป็น HTTPS เท่านั้น (ยกเว้น debug build สำหรับ localhost)
        val normalizedUrl = url.trim().lowercase()
        val isSecureUrl = normalizedUrl.startsWith("https://")
        val isLocalDebug = com.thaiprompt.smschecker.BuildConfig.DEBUG && (
            normalizedUrl.startsWith("http://localhost") ||
            normalizedUrl.startsWith("http://10.0.2.2") ||
            normalizedUrl.startsWith("http://192.168.")
        )
        if (!isSecureUrl && !isLocalDebug) {
            Log.w(TAG, "SECURITY: Rejected insecure URL from QR: $url")
            return null
        }

        // SECURITY: ตรวจสอบ key format — ต้องมีความยาวเพียงพอ (อย่างน้อย 16 ตัวอักษร)
        if (apiKey.length < 16 || secretKey.length < 16) {
            Log.w(TAG, "SECURITY: API key or secret key too short (min 16 chars)")
            return null
        }

        val deviceId = obj.optString("deviceId", "").ifBlank { null }
        val syncInterval = obj.optInt("sync_interval", 300).coerceIn(30, 600) // 30-600 seconds, default 5min (FCM push is primary)

        QrConfigResult(
            type = type,
            version = obj.optInt("version", 1),
            url = url,
            apiKey = apiKey,
            secretKey = secretKey,
            deviceId = deviceId,
            deviceName = obj.optString("deviceName", "เซิร์ฟเวอร์จาก QR"),
            syncInterval = syncInterval
        ).also {
            Log.d(TAG, "✓ Parsed config: url=${it.url}, deviceId=${it.deviceId}, syncInterval=${it.syncInterval}s")
        }
    } catch (e: Exception) {
        Log.e(TAG, "JSON parse error: ${e.message}", e)
        null
    }
}
