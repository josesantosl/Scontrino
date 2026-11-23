package com.pepesantos.scontrino.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import com.pepesantos.scontrino.ocr.OcrLanguage
import com.pepesantos.scontrino.ocr.ScannedReceiptImage
import com.pepesantos.scontrino.ui.components.ReceiptCamera
import com.pepesantos.scontrino.ui.components.ReceiptLanguagesDialog
import com.pepesantos.scontrino.ui.components.displayName
import com.pepesantos.scontrino.ui.viewmodel.ScanReceiptViewModel
import com.pepesantos.scontrino.ui.viewmodel.ScanState

/**
 * Scan a receipt: the camera opens straight away (gallery as an alternative), the photo
 * is read, and [onScanned] gets what was read for the review screen. If nothing can be
 * read, offers another photo or [onAddManually].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onNavigateBack: () -> Unit,
    onScanned: (ParsedReceipt, ScannedReceiptImage) -> Unit,
    onAddManually: () -> Unit,
    viewModel: ScanReceiptViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val languages by viewModel.languages.collectAsState()
    var showLanguagesDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current

    // Every way out forgets this scan, so the next one starts at the camera.
    fun exit() {
        viewModel.reset()
        onNavigateBack()
    }

    LaunchedEffect(state) {
        (state as? ScanState.Done)?.let { done ->
            viewModel.reset()
            onScanned(done.parsed, done.image)
        }
    }

    // While reading, back returns to the camera; otherwise it leaves.
    BackHandler {
        if (state is ScanState.Processing) viewModel.reset() else exit()
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::scan)
    }
    fun pickFromGallery() =
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    var cameraAllowed by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    // Asked once on entering; after a refusal the screen offers the gallery instead.
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var permissionRefused by rememberSaveable { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraAllowed = granted
        permissionRefused = !granted
    }
    LaunchedEffect(Unit) {
        if (!cameraAllowed && !permissionAsked) {
            permissionAsked = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    if (state == ScanState.Idle && cameraAllowed) {
        Dialog(
            onDismissRequest = ::exit,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                ReceiptCamera(
                    onCaptured = viewModel::scan,
                    onError = { message ->
                        Toast.makeText(context, resources.getString(R.string.ocr_error, message), Toast.LENGTH_LONG).show()
                    },
                    onDismiss = ::exit,
                    onPickFromGallery = ::pickFromGallery,
                )
            }
        }
    }

    if (showLanguagesDialog) {
        ReceiptLanguagesDialog(
            current = languages,
            onConfirm = { chosen ->
                showLanguagesDialog = false
                // Reads the same photo again if it is being read or nothing was found.
                if (chosen != languages) viewModel.setLanguages(chosen)
            },
            onDismiss = { showLanguagesDialog = false },
        )
    }
    // Visible while reading and when nothing was read, so whoever shops in another
    // language finds the setting right when it matters.
    val languagesLine: @Composable () -> Unit = {
        LanguagesLine(languages, onChange = { showLanguagesDialog = true })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = ::exit) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_receipt_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            when (val current = state) {
                is ScanState.Processing -> Processing(current, languagesLine, onCancel = viewModel::reset)
                ScanState.NothingFound -> Problem(
                    title = stringResource(R.string.scan_nothing_found_title),
                    message = stringResource(R.string.scan_nothing_found_message),
                    languagesLine = languagesLine,
                    cameraAllowed = cameraAllowed,
                    onRetake = viewModel::reset,
                    onPickFromGallery = ::pickFromGallery,
                    onAddManually = onAddManually,
                )
                is ScanState.Failed -> Problem(
                    title = stringResource(R.string.scan_failed_title),
                    message = stringResource(R.string.ocr_error, current.message),
                    languagesLine = languagesLine,
                    cameraAllowed = cameraAllowed,
                    onRetake = viewModel::reset,
                    onPickFromGallery = ::pickFromGallery,
                    onAddManually = onAddManually,
                )
                // Idle without camera: the permission was refused.
                ScanState.Idle -> if (permissionRefused) {
                    Problem(
                        title = stringResource(R.string.scan_no_camera_title),
                        message = stringResource(R.string.scan_no_camera_message),
                        cameraAllowed = false,
                        onRetake = {},
                        onPickFromGallery = ::pickFromGallery,
                        onAddManually = onAddManually,
                    )
                }
                // Handed over to the review screen by the effect above.
                is ScanState.Done -> Unit
            }
        }
    }
}

@Composable
private fun Processing(
    state: ScanState.Processing,
    languagesLine: @Composable () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.scan_reading), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        languagesLine()
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            state.preview?.let { bitmap ->
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().alpha(0.6f),
                )
            }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.delete_cancel)) }
    }
}

@Composable
private fun Problem(
    title: String,
    message: String,
    cameraAllowed: Boolean,
    languagesLine: (@Composable () -> Unit)? = null,
    onRetake: () -> Unit,
    onPickFromGallery: () -> Unit,
    onAddManually: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (languagesLine != null) {
            Spacer(Modifier.height(8.dp))
            languagesLine()
        }
        Spacer(Modifier.height(24.dp))
        if (cameraAllowed) {
            Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text(stringResource(R.string.scan_retake), modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onPickFromGallery, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text(stringResource(R.string.ocr_pick_image), modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Text(stringResource(R.string.receipt_add_manually), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** "Reading in: Español, English · Change". */
@Composable
private fun LanguagesLine(languages: List<OcrLanguage>, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.scan_languages, languages.joinToString(", ") { it.displayName() }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onChange) { Text(stringResource(R.string.scan_languages_change)) }
    }
}
