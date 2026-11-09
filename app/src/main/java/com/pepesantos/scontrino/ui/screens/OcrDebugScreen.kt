package com.pepesantos.scontrino.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.ocr.OcrLanguage
import com.pepesantos.scontrino.ocr.OcrPageSegMode
import com.pepesantos.scontrino.ocr.OcrResult
import com.pepesantos.scontrino.ocr.ReceiptImagePreprocessor
import com.pepesantos.scontrino.ocr.parse.Amounts
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import com.pepesantos.scontrino.ui.viewmodel.OcrDebugUiState
import com.pepesantos.scontrino.ui.viewmodel.OcrDebugViewModel
import java.util.Locale

/** Words below this confidence are outlined in red on the preview. */
private const val LOW_CONFIDENCE = 60f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrDebugScreen(
    onNavigateBack: () -> Unit,
    /** Opens the new receipt screen prefilled with what was read. */
    onUseReceipt: (ParsedReceipt) -> Unit,
    viewModel: OcrDebugViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    BackHandler(onBack = onNavigateBack)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onImageSelected)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_receipt_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OcrSettings(
                    state = state,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onDetectDocumentChange = viewModel::setDetectDocument,
                    onDeskewChange = viewModel::setDeskew,
                    onRemoveLinesChange = viewModel::setRemoveLines,
                    onBinarizeChange = viewModel::setBinarize,
                    onPageSegModeChange = viewModel::setPageSegMode,
                    onTryOtherModesChange = viewModel::setTryOtherModes,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Text(stringResource(R.string.ocr_pick_image), modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(onClick = viewModel::run, enabled = state.canRun) {
                        Text(stringResource(R.string.ocr_run))
                    }
                }
                if (state.isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }

            state.error?.let { error ->
                item {
                    Text(
                        stringResource(R.string.ocr_error, error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.sourceUri == null) {
                item { Text(stringResource(R.string.ocr_no_image)) }
            }

            state.detection?.let { detection ->
                item {
                    Text(
                        stringResource(
                            when (detection) {
                                ReceiptImagePreprocessor.Detection.CORNERS -> R.string.ocr_document_detected
                                ReceiptImagePreprocessor.Detection.BRIGHT_REGION -> R.string.ocr_document_region
                                ReceiptImagePreprocessor.Detection.NONE -> R.string.ocr_document_not_detected
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    state.skewAngle?.let { angle ->
                        Text(
                            stringResource(R.string.ocr_skew_corrected, String.format(Locale.ROOT, "%.1f", angle)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            state.processedImage?.let { bitmap ->
                item {
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .drawWithContent {
                                drawContent()
                                val result = state.result ?: return@drawWithContent
                                drawWordBoxes(result)
                            }
                    )
                }
            }

            state.result?.let { result ->
                item {
                    Text(
                        stringResource(
                            R.string.ocr_summary,
                            result.words.size, state.rows.size, result.meanConfidence, result.durationMs.toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LayoutAttemptsSummary(state)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = {
                            viewModel.exportJson()?.let { copyToClipboard(context, "receipt-ocr.json", it) }
                        }) { Text(stringResource(R.string.ocr_copy_json)) }
                        OutlinedButton(onClick = {
                            copyToClipboard(context, "receipt-ocr.txt", state.rows.joinToString("\n") { it.text })
                        }) { Text(stringResource(R.string.ocr_copy_text)) }
                    }
                }
                state.parsed?.let { parsed ->
                    item { ParsedReceiptSection(parsed, onUse = { onUseReceipt(parsed) }) }
                }
                item {
                    Text(stringResource(R.string.ocr_rows), style = MaterialTheme.typography.titleMedium)
                }
                items(state.rows) { row ->
                    Text(
                        row.segments.joinToString("  │  "),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** What the parser understood: the proposal the future review screen will start from. */
@Composable
private fun ParsedReceiptSection(parsed: ParsedReceipt, onUse: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.ocr_parsed_title), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onUse, enabled = parsed.items.isNotEmpty()) {
            Text(stringResource(R.string.ocr_use_receipt))
        }
        parsed.date?.let { Text(stringResource(R.string.ocr_parsed_date, it.toString())) }

        val total = parsed.totalCents
        if (total == null) {
            Text(stringResource(R.string.ocr_parsed_no_total), color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                stringResource(
                    R.string.ocr_parsed_total,
                    Amounts.format(total),
                    Amounts.format(parsed.itemsTotalCents),
                )
            )
            Text(
                stringResource(
                    if (parsed.isConsistent) R.string.ocr_parsed_consistent else R.string.ocr_parsed_inconsistent
                ),
                color = if (parsed.isConsistent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            )
        }

        parsed.items.forEach { item ->
            val details = buildList {
                if (item.discountCents > 0) {
                    add(stringResource(R.string.ocr_parsed_discount, Amounts.format(item.discountCents)))
                }
                item.vatCode?.let { code ->
                    add(item.vatRate?.takeIf { !code.endsWith("%") }?.let { "$code ${"%.0f".format(it)}%" } ?: code)
                }
                item.correctedFromCents?.let {
                    add(stringResource(R.string.ocr_parsed_corrected, Amounts.format(it)))
                }
            }
            Text(
                "${item.name}  ${Amounts.format(item.priceCents)}" +
                    if (details.isEmpty()) "" else "  (${details.joinToString(" · ")})",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.correctedFromCents != null) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
            )
        }

        if (parsed.unparsedRows.isNotEmpty()) {
            Text(
                stringResource(R.string.ocr_parsed_unparsed, parsed.unparsedRows.size),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OcrSettings(
    state: OcrDebugUiState,
    onToggleLanguage: (OcrLanguage) -> Unit,
    onDetectDocumentChange: (Boolean) -> Unit,
    onDeskewChange: (Boolean) -> Unit,
    onRemoveLinesChange: (Boolean) -> Unit,
    onBinarizeChange: (Boolean) -> Unit,
    onPageSegModeChange: (OcrPageSegMode) -> Unit,
    onTryOtherModesChange: (Boolean) -> Unit,
) {
    val appLocale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.ocr_languages), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OcrLanguage.entries.forEach { language ->
                FilterChip(
                    selected = language in state.languages,
                    onClick = { onToggleLanguage(language) },
                    label = {
                        Text(Locale.forLanguageTag(language.isoCode).getDisplayLanguage(appLocale)
                            .replaceFirstChar { it.titlecase(appLocale) })
                    },
                )
            }
        }

        Text(stringResource(R.string.ocr_page_mode), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OcrPageSegMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == state.pageSegMode,
                    onClick = { onPageSegModeChange(mode) },
                    label = { Text(stringResource(mode.label)) },
                )
            }
        }
        SwitchRow(stringResource(R.string.ocr_try_other_modes), state.tryOtherModes, onTryOtherModesChange)

        SwitchRow(stringResource(R.string.ocr_detect_document), state.detectDocument, onDetectDocumentChange)
        SwitchRow(stringResource(R.string.ocr_deskew), state.deskew, onDeskewChange)
        SwitchRow(stringResource(R.string.ocr_remove_lines), state.removeLines, onRemoveLinesChange)
        SwitchRow(stringResource(R.string.ocr_binarize), state.binarize, onBinarizeChange)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Which layout mode was used and, if several were tried, how each one did. */
@Composable
private fun LayoutAttemptsSummary(state: OcrDebugUiState) {
    val used = state.usedMode ?: return
    Text(
        stringResource(R.string.ocr_mode_used, stringResource(used.label)),
        style = MaterialTheme.typography.bodySmall,
    )
    if (state.attempts.size < 2) return
    state.attempts.forEach { attempt ->
        Text(
            stringResource(
                R.string.ocr_mode_attempt,
                stringResource(attempt.mode.label),
                attempt.parsed.items.size,
                stringResource(
                    if (attempt.parsed.isConsistent) R.string.ocr_attempt_adds_up
                    else R.string.ocr_attempt_does_not_add_up
                ),
                attempt.nameConfidence.toInt(),
                attempt.knownNames(state.knownNames),
            ),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (attempt.mode == used) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}

private val OcrPageSegMode.label: Int
    get() = when (this) {
        OcrPageSegMode.AUTO -> R.string.ocr_psm_auto
        OcrPageSegMode.SINGLE_COLUMN -> R.string.ocr_psm_single_column
        OcrPageSegMode.SINGLE_BLOCK -> R.string.ocr_psm_single_block
        OcrPageSegMode.SPARSE -> R.string.ocr_psm_sparse
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWordBoxes(result: OcrResult) {
    val scale = size.width / result.imageWidth
    val stroke = Stroke(width = 2f)
    result.words.forEach { word ->
        drawRect(
            color = if (word.confidence < LOW_CONFIDENCE) Color.Red else Color(0xFF2E7D32),
            topLeft = Offset(word.box.left * scale, word.box.top * scale),
            size = Size(word.box.width * scale, word.box.height * scale),
            style = stroke,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, R.string.ocr_copied, Toast.LENGTH_SHORT).show()
}
