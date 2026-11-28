package com.pepesantos.scontrino.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.ocr.LayoutAttempt
import com.pepesantos.scontrino.ocr.OcrJson
import com.pepesantos.scontrino.ocr.OcrLanguage
import com.pepesantos.scontrino.ocr.OcrPageSegMode
import com.pepesantos.scontrino.ocr.OcrResult
import com.pepesantos.scontrino.ocr.ReceiptImagePreprocessor
import com.pepesantos.scontrino.ocr.ReceiptRow
import com.pepesantos.scontrino.ocr.ReceiptScanner
import com.pepesantos.scontrino.ocr.ScannedReceiptImage
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

private val DEFAULTS = ReceiptScanner.Options()

data class OcrDebugUiState(
    val sourceUri: Uri? = null,
    /** Ordered: the first language is Tesseract's primary one. */
    val languages: List<OcrLanguage> = DEFAULTS.languages,
    val detectDocument: Boolean = DEFAULTS.preprocessing.detectDocument,
    val deskew: Boolean = DEFAULTS.preprocessing.deskew,
    val removeLines: Boolean = DEFAULTS.preprocessing.removeLines,
    val binarize: Boolean = DEFAULTS.preprocessing.binarize,
    val pageSegMode: OcrPageSegMode = DEFAULTS.pageSegMode,
    /** If the chosen mode's reading does not add up, try the other modes too. */
    val tryOtherModes: Boolean = DEFAULTS.tryOtherModes,
    val isProcessing: Boolean = false,
    val processedImage: Bitmap? = null,
    val detection: ReceiptImagePreprocessor.Detection? = null,
    val skewAngle: Double? = null,
    val result: OcrResult? = null,
    val rows: List<ReceiptRow> = emptyList(),
    val parsed: ParsedReceipt? = null,
    /** The image and rows of [parsed], for the review screen. */
    val scanImage: ScannedReceiptImage? = null,
    /** Every mode tried on the last run, in order; the chosen one is [usedMode]. */
    val attempts: List<LayoutAttempt> = emptyList(),
    val usedMode: OcrPageSegMode? = null,
    /** The user's product names (normalized), to rank readings by names already known. */
    val knownNames: Set<String> = emptySet(),
    val error: String? = null,
) {
    val canRun: Boolean get() = sourceUri != null && languages.isNotEmpty() && !isProcessing

    fun toScanOptions() = ReceiptScanner.Options(
        languages = languages,
        preprocessing = ReceiptImagePreprocessor.Options(
            detectDocument = detectDocument,
            deskew = deskew,
            removeLines = removeLines,
            binarize = binarize,
        ),
        pageSegMode = pageSegMode,
        tryOtherModes = tryOtherModes,
    )
}

/**
 * Lab screen state for receipt scanning: the same pipeline as the scan feature
 * ([ReceiptScanner]), with every knob exposed and every intermediate step shown.
 * No data is persisted.
 */
class OcrDebugViewModel @JvmOverloads constructor(
    application: Application,
    private val scanner: ReceiptScanner = ReceiptScanner(application),
) : AndroidViewModel(application) {

    // Starts from the receipt languages in the settings, like the real scan.
    private val _uiState = MutableStateFlow(
        OcrDebugUiState(languages = ReceiptScanner.languagesFromSettings(application))
    )
    val uiState: StateFlow<OcrDebugUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    fun onImageSelected(uri: Uri) {
        runJob?.cancel()
        _uiState.update {
            it.copy(
                sourceUri = uri, isProcessing = false, processedImage = null,
                detection = null, skewAngle = null, result = null, rows = emptyList(), parsed = null,
                scanImage = null, attempts = emptyList(), usedMode = null, error = null,
            )
        }
        run()
    }

    fun toggleLanguage(language: OcrLanguage) = _uiState.update {
        val languages = if (language in it.languages) it.languages - language else it.languages + language
        it.copy(languages = languages)
    }

    fun setDetectDocument(enabled: Boolean) = _uiState.update { it.copy(detectDocument = enabled) }
    fun setDeskew(enabled: Boolean) = _uiState.update { it.copy(deskew = enabled) }
    fun setRemoveLines(enabled: Boolean) = _uiState.update { it.copy(removeLines = enabled) }
    fun setBinarize(enabled: Boolean) = _uiState.update { it.copy(binarize = enabled) }
    fun setPageSegMode(mode: OcrPageSegMode) = _uiState.update { it.copy(pageSegMode = mode) }
    fun setTryOtherModes(enabled: Boolean) = _uiState.update { it.copy(tryOtherModes = enabled) }

    fun run() {
        val state = _uiState.value
        val uri = state.sourceUri ?: return
        if (!state.canRun) return

        runJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val scan = scanner.scan(uri, state.toScanOptions()) { processed ->
                    _uiState.update {
                        it.copy(
                            processedImage = processed.bitmap,
                            detection = processed.detection,
                            skewAngle = processed.skewAngle,
                        )
                    }
                }
                val scanImage = withContext(Dispatchers.Default) { ScannedReceiptImage.of(scan) }
                _uiState.update {
                    it.copy(
                        scanImage = scanImage,
                        result = scan.best.result,
                        rows = scan.best.rows,
                        parsed = scan.best.parsed,
                        attempts = scan.attempts,
                        usedMode = scan.best.mode,
                        knownNames = scan.knownNames,
                        isProcessing = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun exportJson(): String? {
        val state = _uiState.value
        val result = state.result ?: return null
        return OcrJson.toJson(result, state.rows)
    }

    /**
     * The preprocessed image (full OCR resolution) and [exportJson], written to a temp
     * folder so testers can send both as a fixture. Overwritten on each call and deleted
     * when the lab is left; nothing else is kept.
     */
    suspend fun shareFiles(): List<File>? {
        val bitmap = _uiState.value.processedImage ?: return null
        val json = exportJson() ?: return null
        return withContext(Dispatchers.IO) {
            val dir = shareDir().apply { deleteRecursively(); mkdirs() }
            val image = File(dir, "receipt-ocr.png")
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val text = File(dir, "receipt-ocr.json").apply { writeText(json) }
            listOf(image, text)
        }
    }

    private fun shareDir() = File(getApplication<Application>().cacheDir, SHARE_DIR)

    override fun onCleared() {
        shareDir().deleteRecursively()
    }

    companion object {
        private const val TAG = "OcrDebugViewModel"

        /** Must match res/xml/debug_share_paths.xml in the debug source set. */
        private const val SHARE_DIR = "debug-share"

        /** FileProvider authority, declared in the debug manifest only. */
        fun shareAuthority(packageName: String) = "$packageName.debugshare"
    }
}
