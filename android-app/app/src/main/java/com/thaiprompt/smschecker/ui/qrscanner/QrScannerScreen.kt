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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.thaiprompt.smschecker.ui.theme.AppColors
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

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
    var hasCameraPermission by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scannedResult by remember { mutableStateOf<QrConfigResult?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            errorMessage = "Camera permission is required to scan QR codes"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Server QR Code", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(
                                                ContextCompat.getMainExecutor(ctx)
                                            ) { imageProxy ->
                                                processImage(imageProxy, isProcessing) { result ->
                                                    scannedResult = result
                                                    onConfigScanned(result)
                                                }
                                            }
                                        }

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        errorMessage = "Camera initialization failed: ${e.message}"
                                        Log.e("QrScanner", "Camera bind failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scan frame overlay
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
                                    color = AppColors.GoldAccent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        )
                    }

                    // Bottom overlay with instruction
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
                            Text(
                                "Point camera at the server QR code",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
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
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            "Camera permission required",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text("Grant Permission")
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "How to get QR Code",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.GoldAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. Open your Laravel admin panel\n" +
                                "2. Go to SMS Checker device settings\n" +
                                "3. Click 'Show QR Code' for your device\n" +
                                "4. Scan the QR code shown on screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    isProcessing: AtomicBoolean,
    onResult: (QrConfigResult) -> Unit
) {
    if (!isProcessing.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        isProcessing.set(false)
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_UNKNOWN) {
                    val rawValue = barcode.rawValue ?: continue
                    parseQrConfig(rawValue)?.let { result ->
                        onResult(result)
                        return@addOnSuccessListener
                    }
                }
            }
            isProcessing.set(false)
        }
        .addOnFailureListener {
            isProcessing.set(false)
            Log.e("QrScanner", "ML Kit failed", it)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun parseQrConfig(json: String): QrConfigResult? {
    return try {
        val obj = JSONObject(json)
        val type = obj.optString("type", "")
        if (type != "smschecker_config") return null

        QrConfigResult(
            type = type,
            version = obj.optInt("version", 1),
            url = obj.getString("url"),
            apiKey = obj.getString("apiKey"),
            secretKey = obj.getString("secretKey"),
            deviceName = obj.optString("deviceName", "Scanned Server")
        )
    } catch (e: Exception) {
        Log.e("QrScanner", "Failed to parse QR config: $json", e)
        null
    }
}
