package com.pepesantos.scontrino.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pepesantos.scontrino.R
import java.io.File

/** Share of the preview's width the receipt guide takes. */
private const val GUIDE_WIDTH_FRACTION = 0.78f

/** Share of the preview's height the receipt guide takes (receipts are tall). */
private const val GUIDE_HEIGHT_FRACTION = 0.72f

/**
 * Full-screen camera to photograph a receipt (CameraX, fully offline).
 *
 * The frame drawn on top is only a guide for the user: the photo is saved whole and the
 * preprocessor finds the receipt in it, exactly as with a gallery picture. Captures at the
 * highest resolution available, favouring quality over latency, since small receipt fonts
 * need every pixel. Tap on the preview to focus, pinch to zoom.
 *
 * The capture rotation is pinned to portrait on purpose. CameraController would follow the
 * motion sensor, and with the phone held flat over a receipt on a table the sensor easily flips
 * to landscape, which tags the photo with a 90°/270° rotation that ruins OCR. The UI is
 * portrait-only, so the photo always matches what the preview shows.
 *
 * The photo goes to the app's cache ([captureDir]); the previous one is deleted on each
 * capture, so at most one is kept.
 *
 * @param onPickFromGallery if set, a button next to the shutter to use a gallery photo instead.
 */
@Composable
fun ReceiptCamera(
    onCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
    onPickFromGallery: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val preview = remember {
        Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .setTargetRotation(Surface.ROTATION_0)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            )
            // Fixed: never follow the device sensor (see the KDoc above).
            .setTargetRotation(Surface.ROTATION_0)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val p = providerFuture.get().also { provider = it }
                p.unbind(preview, imageCapture)
                preview.setSurfaceProvider(previewView.surfaceProvider)
                camera = p.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture,
                )
            } catch (e: Exception) {
                Log.e("ReceiptCamera", "Cannot start the camera", e)
                onError(e.message ?: e.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbind(preview, imageCapture)
            camera = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView.apply { installTouchGestures { camera } } },
            modifier = Modifier.fillMaxSize(),
        )

        ReceiptGuide(modifier = Modifier.fillMaxSize())

        Text(
            text = stringResource(R.string.ocr_camera_hint),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 64.dp, end = 64.dp)
                .fillMaxWidth(),
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ocr_camera_close), tint = Color.White)
        }

        IconButton(
            onClick = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Icon(
                if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                contentDescription = stringResource(R.string.ocr_camera_torch),
                tint = Color.White,
            )
        }

        if (onPickFromGallery != null) {
            IconButton(
                onClick = onPickFromGallery,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 32.dp, bottom = 36.dp)
                    .size(48.dp),
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(R.string.ocr_pick_image),
                    tint = Color.White,
                )
            }
        }

        val captureLabel = stringResource(R.string.ocr_camera_capture)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .size(72.dp)
                .border(4.dp, Color.White, CircleShape),
        ) {
            if (capturing) {
                CircularProgressIndicator(color = Color.White)
            } else {
                // The white disc is the shutter.
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .semantics { contentDescription = captureLabel }
                        .clickable(role = Role.Button) {
                            capturing = true
                            takePicture(
                                context, imageCapture,
                                onSaved = { uri ->
                                    capturing = false
                                    onCaptured(uri)
                                },
                                onFailed = { message ->
                                    capturing = false
                                    onError(message)
                                },
                            )
                        },
                )
            }
        }
    }
}

/** Darkens everything but a tall rounded rectangle where the receipt should go. */
@Composable
private fun ReceiptGuide(modifier: Modifier = Modifier) {
    Canvas(
        // Offscreen so that BlendMode.Clear punches a hole in the shade only.
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val guide = Size(size.width * GUIDE_WIDTH_FRACTION, size.height * GUIDE_HEIGHT_FRACTION)
        val topLeft = Offset((size.width - guide.width) / 2, (size.height - guide.height) / 2)
        val radius = CornerRadius(16.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.45f))
        drawRoundRect(Color.Transparent, topLeft, guide, radius, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, topLeft, guide, radius, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * Tap to focus and pinch to zoom, as CameraController used to provide. [camera] is read on
 * each event since it is only available once the provider has bound the use cases.
 */
@SuppressLint("ClickableViewAccessibility") // Focus/zoom only; the shutter is a separate button.
private fun PreviewView.installTouchGestures(camera: () -> Camera?) {
    val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera() ?: return false
                val zoom = cam.cameraInfo.zoomState.value ?: return false
                cam.cameraControl.setZoomRatio(
                    (zoom.zoomRatio * detector.scaleFactor)
                        .coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
                )
                return true
            }
        },
    )
    var scaled = false
    setOnTouchListener { _, event ->
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> scaled = false
            MotionEvent.ACTION_POINTER_DOWN -> scaled = true
            MotionEvent.ACTION_UP -> if (!scaled) {
                camera()?.cameraControl?.startFocusAndMetering(
                    FocusMeteringAction.Builder(meteringPointFactory.createPoint(event.x, event.y)).build()
                )
            }
        }
        true
    }
}

private fun takePicture(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: (Uri) -> Unit,
    onFailed: (String) -> Unit,
) {
    val dir = captureDir(context)
    dir.listFiles()?.forEach { it.delete() }
    // A new name each time, so nothing downstream can mistake it for the previous photo.
    val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")

    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSaved(output.savedUri ?: Uri.fromFile(file))
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("ReceiptCamera", "Capture failed", exception)
                onFailed(exception.message ?: exception.javaClass.simpleName)
            }
        },
    )
}

private fun captureDir(context: Context): File =
    File(context.cacheDir, "receipt-camera").apply { mkdirs() }
