package com.pepesantos.scontrino.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.AppDatabase
import com.pepesantos.scontrino.ocr.LayoutAttempt
import com.pepesantos.scontrino.ocr.draft.ProductNames
import com.pepesantos.scontrino.ocr.LayoutChoice
import com.pepesantos.scontrino.ocr.OcrJson
import com.pepesantos.scontrino.ocr.OcrLanguage
import com.pepesantos.scontrino.ocr.OcrPageSegMode
import com.pepesantos.scontrino.ocr.OcrResult
import com.pepesantos.scontrino.ocr.ReceiptImageLoader
import com.pepesantos.scontrino.ocr.ReceiptImagePreprocessor
import com.pepesantos.scontrino.ocr.ReceiptRow
import com.pepesantos.scontrino.ocr.ReceiptTextRecognizer
import com.pepesantos.scontrino.ocr.TesseractTextRecognizer
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

data class OcrDebugUiState(
    val sourceUri: Uri? = null,
    /** Ordered: the first language is Tesseract's primary one. */
    val languages: List<OcrLanguage> = OcrLanguage.defaultFor(Locale.getDefault()),
    val detectDocument: Boolean = true,
    val deskew: Boolean = true,
    val removeLines: Boolean = false,
    val binarize: Boolean = true,
    // AUTO's column analysis read Bennet's price column as vertical text, even with
    // vertical detection disabled; a single column is what a receipt is.
    val pageSegMode: OcrPageSegMode = OcrPageSegMode.SINGLE_COLUMN,
    /** If the chosen mode's reading does not add up, try the other modes too. */
    val tryOtherModes: Boolean = true,
    val isProcessing: Boolean = false,
    val processedImage: Bitmap? = null,
    val detection: ReceiptImagePreprocessor.Detection? = null,
    val skewAngle: Double? = null,
    val result: OcrResult? = null,
    val rows: List<ReceiptRow> = emptyList(),
    val parsed: ParsedReceipt? = null,
    /** Every mode tried on the last run, in order; the chosen one is [usedMode]. */
    val attempts: List<LayoutAttempt> = emptyList(),
    val usedMode: OcrPageSegMode? = null,
    /** The user's product names (normalized), to rank readings by names already known. */
    val knownNames: Set<String> = emptySet(),
    val error: String? = null,
) {
    val canRun: Boolean get() = sourceUri != null && languages.isNotEmpty() && !isProcessing
}

/**
 * Lab screen state for phase 1 of receipt scanning: photo → preprocessed image →
 * OCR words → reconstructed rows. No data is persisted.
 */
class OcrDebugViewModel @JvmOverloads constructor(
    application: Application,
    private val recognizer: ReceiptTextRecognizer = TesseractTextRecognizer(application),
    private val preprocessor: ReceiptImagePreprocessor = ReceiptImagePreprocessor(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OcrDebugUiState())
    val uiState: StateFlow<OcrDebugUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    fun onImageSelected(uri: Uri) {
        runJob?.cancel()
        _uiState.update {
            it.copy(
                sourceUri = uri, isProcessing = false, processedImage = null,
                detection = null, skewAngle = null, result = null, rows = emptyList(), parsed = null,
                attempts = emptyList(), usedMode = null, error = null,
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
                val processed = withContext(Dispatchers.Default) {
                    val original = ReceiptImageLoader.load(getApplication(), uri)
                    preprocessor.process(
                        original,
                        ReceiptImagePreprocessor.Options(
                            detectDocument = state.detectDocument,
                            deskew = state.deskew,
                            removeLines = state.removeLines,
                            binarize = state.binarize,
                        )
                    ).also { original.recycle() }
                }
                _uiState.update {
                    it.copy(
                        processedImage = processed.bitmap,
                        detection = processed.detection,
                        skewAngle = processed.skewAngle,
                    )
                }

                suspend fun attempt(mode: OcrPageSegMode): LayoutAttempt {
                    val result = recognizer.recognize(processed.bitmap, state.languages, mode)
                    return withContext(Dispatchers.Default) { LayoutAttempt.of(mode, result) }
                }

                // The chosen mode first; the others only if its reading does not add up,
                // so a receipt that is read well costs a single OCR pass.
                val attempts = mutableListOf(attempt(state.pageSegMode))
                if (state.tryOtherModes && !LayoutChoice.isGoodEnough(attempts.first())) {
                    for (mode in LayoutChoice.fallbacksAfter(state.pageSegMode)) {
                        attempts += attempt(mode)
                    }
                }
                // Only needed to choose among several readings.
                val knownNames = if (attempts.size > 1) loadKnownNames() else emptySet()
                val best = LayoutChoice.best(attempts, knownNames)

                _uiState.update {
                    it.copy(
                        result = best.result,
                        rows = best.rows,
                        parsed = best.parsed,
                        attempts = attempts,
                        usedMode = best.mode,
                        knownNames = knownNames,
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

    private suspend fun loadKnownNames(): Set<String> = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(getApplication()).productDao().getAll()
            .map { ProductNames.normalize(it.name) }
            .toSet()
    }

    fun exportJson(): String? {
        val state = _uiState.value
        val result = state.result ?: return null
        return OcrJson.toJson(result, state.rows)
    }

    private companion object {
        const val TAG = "OcrDebugViewModel"
    }
}
