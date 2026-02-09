package com.thaiprompt.smschecker.ui.qrscanner

import android.Manifest
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.thaiprompt.smschecker.ui.components.GradientHeader
import com.thaiprompt.smschecker.ui.components.GlassCard
import com.thaiprompt.smschecker.ui.components.premiumBackgroundBrush
import com.thaiprompt.smschecker.ui.theme.AppColors
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var isScanning by remember { mutableStateOf(false) }
    val isProcessing = remember { AtomicBoolean(false) }
    val hasFoundResult = remember { AtomicBoolean(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush())
    ) {
        // Header
        GradientHeader {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.backButton,
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        strings.qrScannerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        strings.qrScannerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF66BB6A) // Light green accent
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasCameraPermission) {
                // Camera preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
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
                                                    isScanning = true
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
                                        errorMessage = "${strings.cameraFailed}: ${e.message}"
                                        Log.e(TAG, "Camera bind failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scan frame overlay — gold border
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.8f)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(AppColors.GoldAccent, AppColors.GoldDark)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        )
                    }

                    // Scanning indicator + bottom instruction
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isScanning) {
                                    val rotation = rememberInfiniteTransition(label = "scan")
                                    val angle by rotation.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1500, easing = LinearEasing)
                                        ),
                                        label = "scanRotation"
                                    )
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(angle),
                                        tint = AppColors.GoldAccent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    strings.pointCameraAtQr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            } else {
                // No camera permission state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(AppColors.GoldAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = AppColors.GoldAccent.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            strings.cameraPermissionRequired,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.GoldAccent,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(strings.allowCamera)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show scan status (wrong format QR detected)
            scanStatus?.let { status ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = AppColors.WarningOrange.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        status,
                        modifier = Modifier.padding(16.dp),
                        color = AppColors.WarningOrange,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = AppColors.DebitRed.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = AppColors.DebitRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Instructions
            GlassCard {
                Text(
                    strings.howToGetQrCode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    strings.qrInstructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }
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
            deviceName = obj.optString("deviceName", "\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C\u0E08\u0E32\u0E01 QR"),
            syncInterval = syncInterval
        ).also {
            Log.d(TAG, "✓ Parsed config: url=${it.url}, deviceId=${it.deviceId}, syncInterval=${it.syncInterval}s")
        }
    } catch (e: Exception) {
        Log.e(TAG, "JSON parse error: ${e.message}", e)
        null
    }
}
