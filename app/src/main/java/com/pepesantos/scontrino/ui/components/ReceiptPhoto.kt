package com.pepesantos.scontrino.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.ocr.BoundingBox

/**
 * The lines of the receipt photo an item was read from, one image per [crops] box,
 * so the reading can be checked against the paper without leaving the card.
 */
@Composable
fun ReceiptLines(image: ImageBitmap, crops: List<BoundingBox>, modifier: Modifier = Modifier) {
    if (crops.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (crop in crops) {
            val painter = remember(image, crop) {
                BitmapPainter(
                    image,
                    srcOffset = IntOffset(crop.left, crop.top),
                    srcSize = IntSize(crop.width, crop.height),
                    filterQuality = FilterQuality.Medium,
                )
            }
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(crop.width.toFloat() / crop.height)
                    .clip(RoundedCornerShape(6.dp))
                    // The cleaned-up photo is black on white: keep it so in a dark theme too.
                    .background(Color.White),
            )
        }
    }
}

/**
 * The whole receipt photo, full screen: fits the width and scrolls down the receipt;
 * pinch or double tap to zoom. [onRescan] replaces the reading with another photo
 * (null hides it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPhotoViewer(image: ImageBitmap, onDismiss: () -> Unit, onRescan: (() -> Unit)? = null) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.scan_photo_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.scan_photo_close))
                        }
                    },
                    actions = {
                        if (onRescan != null) {
                            TextButton(onClick = onRescan) {
                                Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = Color.White)
                                Text(
                                    stringResource(R.string.scan_photo_rescan),
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            },
        ) { padding ->
            ZoomableImage(image, Modifier.padding(padding).fillMaxSize())
        }
    }
}

/**
 * [image] fitted to the width, top first. Pan with one finger, zoom with two (1×–5×)
 * around the fingers, double tap to zoom in or back out.
 */
@Composable
private fun ZoomableImage(image: ImageBitmap, modifier: Modifier = Modifier) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Height of the image when it fills the width at scale 1.
    fun fittedHeight() = viewSize.width * image.height.toFloat() / image.width

    fun clamp(candidate: Offset, atScale: Float): Offset {
        val width = viewSize.width * atScale
        val height = fittedHeight() * atScale
        val x = candidate.x.coerceIn(minOf(0f, viewSize.width - width), 0f)
        // Shorter than the screen: centred; otherwise, no gap above or below.
        val y = if (height <= viewSize.height) (viewSize.height - height) / 2
        else candidate.y.coerceIn(viewSize.height - height, 0f)
        return Offset(x, y)
    }

    fun zoomTo(newScale: Float, around: Offset) {
        val clamped = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        offset = clamp(around - (around - offset) * (clamped / scale), clamped)
        scale = clamped
    }

    Canvas(
        modifier = modifier
            .onSizeChanged {
                viewSize = it
                offset = clamp(offset, scale)
            }
            .pointerInput(image) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    zoomTo(scale * zoom, centroid)
                    offset = clamp(offset + pan, scale)
                }
            }
            .pointerInput(image) {
                detectTapGestures(onDoubleTap = { tap ->
                    zoomTo(if (scale > MIN_SCALE) MIN_SCALE else DOUBLE_TAP_SCALE, tap)
                })
            },
    ) {
        if (viewSize == IntSize.Zero) return@Canvas
        translate(offset.x, offset.y) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                drawImage(
                    image,
                    dstSize = IntSize(viewSize.width, fittedHeight().toInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
