package com.pepesantos.scontrino.ui.components

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera component that uses CameraX and zxing-cpp (fully offline, no Google services)
 * to scan barcodes.
 * Specifically handles the UPC-A to EAN-13 conversion to preserve leading zeros.
 */
@Composable
fun BarcodeScanner(
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    // Explicitly configure scanner for common loyalty card formats
    val barcodeReader = remember {
        BarcodeReader(
            BarcodeReader.Options(
                formats = setOf(
                    BarcodeReader.Format.EAN_13,
                    BarcodeReader.Format.EAN_8,
                    BarcodeReader.Format.UPC_A,
                    BarcodeReader.Format.CODE_128
                ),
                tryHarder = true,
                tryRotate = true
            )
        )
    }
    // Written from the camera thread, so it must be thread-safe.
    val isScanned = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            if (isScanned.get()) return@setAnalyzer

                            val barcode = barcodeReader.read(imageProxy)
                                .firstOrNull { !it.text.isNullOrEmpty() }
                                ?: return@setAnalyzer
                            val value = barcode.text!!

                            // EUROPEAN FIX: If it's a 12-digit UPC-A, it's often an EAN-13
                            // with a leading zero that the decoder "simplifies" away.
                            // We restore it to ensure compatibility with store scanners.
                            val finalValue = if (barcode.format == BarcodeReader.Format.UPC_A && value.length == 12) {
                                "0$value"
                            } else {
                                value
                            }

                            if (isScanned.compareAndSet(false, true)) {
                                mainExecutor.execute { onBarcodeScanned(finalValue) }
                            }
                        } catch (e: Exception) {
                            Log.e("BarcodeScanner", "Scanning failed", e)
                        } finally {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Log.e("BarcodeScanner", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}
