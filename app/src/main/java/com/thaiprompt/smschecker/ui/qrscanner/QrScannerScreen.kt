package com.thaiprompt.smschecker.ui.qrscanner

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "QrScanner"

data class QrConfigResult(
    val type: String,
    val version: Int,
    val url: String,
    val apiKey: String,
    val secretKey: String,
    val deviceName: String
)

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
    var isScanning by remember { mutableStateOf(false) }
    var framesProcessed by remember { mutableIntStateOf(0) }
    val isProcessing = remember { AtomicBoolean(false) }
    val lastProcessingTime = remember { AtomicLong(0L) }

    // Background executor for ML Kit — keeps UI thread free
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Auto-reset isProcessing if stuck for >3 seconds
    LaunchedEffect(framesProcessed) {
        val lastTime = lastProcessingTime.get()
        if (lastTime > 0 && isProcessing.get()) {
            val elapsed = System.currentTimeMillis() - lastTime
            if (elapsed > 3000) {
                Log.w(TAG, "isProcessing stuck for ${elapsed}ms, resetting")
                isProcessing.set(false)
            }
        }
    }

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

    // Cleanup executor when composable leaves
    DisposableEffect(Unit) {
        onDispose {
            try {
                analysisExecutor.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "Error shutting down executor", e)
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
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        strings.qrScannerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        strings.qrScannerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.GoldAccent
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

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                // Use background executor to avoid blocking UI thread
                                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                                    isScanning = true
                                                    processImage(
                                                        imageProxy,
                                                        isProcessing,
                                                        lastProcessingTime
                                                    ) { result ->
                                                        Log.d(TAG, "QR config scanned successfully: ${result.url}")
                                                        // Callback on main thread
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onConfigScanned(result)
                                                        }
                                                    }
                                                    framesProcessed++
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
                                    // Animated scanning indicator
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

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    isProcessing: AtomicBoolean,
    lastProcessingTime: AtomicLong,
    onResult: (QrConfigResult) -> Unit
) {
    if (!isProcessing.compareAndSet(false, true)) {
        // Check for stuck state — auto-reset after 3 seconds
        val elapsed = System.currentTimeMillis() - lastProcessingTime.get()
        if (elapsed > 3000) {
            Log.w(TAG, "Processing stuck for ${elapsed}ms, force resetting")
            isProcessing.set(false)
            // Try again on next frame
        }
        imageProxy.close()
        return
    }

    lastProcessingTime.set(System.currentTimeMillis())

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        Log.w(TAG, "mediaImage is null")
        isProcessing.set(false)
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            if (barcodes.isNotEmpty()) {
                Log.d(TAG, "Found ${barcodes.size} barcode(s)")
            }
            for (barcode in barcodes) {
                if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_UNKNOWN) {
                    val rawValue = barcode.rawValue ?: continue
                    Log.d(TAG, "Barcode raw value: ${rawValue.take(100)}...")
                    parseQrConfig(rawValue)?.let { result ->
                        Log.d(TAG, "Valid QR config found: url=${result.url}")
                        onResult(result)
                        // Reset processing flag after successful callback
                        isProcessing.set(false)
                        return@addOnSuccessListener
                    }
                }
            }
            isProcessing.set(false)
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "ML Kit barcode scan failed", e)
            isProcessing.set(false)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun parseQrConfig(json: String): QrConfigResult? {
    return try {
        val obj = JSONObject(json)
        val type = obj.optString("type", "")
        if (type != "smschecker_config") {
            Log.d(TAG, "QR type mismatch: '$type' (expected 'smschecker_config')")
            return null
        }

        QrConfigResult(
            type = type,
            version = obj.optInt("version", 1),
            url = obj.getString("url"),
            apiKey = obj.getString("apiKey"),
            secretKey = obj.getString("secretKey"),
            deviceName = obj.optString("deviceName", "\u0E40\u0E0B\u0E34\u0E23\u0E4C\u0E1F\u0E40\u0E27\u0E2D\u0E23\u0E4C\u0E08\u0E32\u0E01 QR")
        ).also {
            Log.d(TAG, "Parsed QR config: url=${it.url}, device=${it.deviceName}")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse QR config: $json", e)
        null
    }
}
